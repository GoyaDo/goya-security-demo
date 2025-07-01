package com.ysmjjsy.goya.security.bus.transport.redis;

import com.ysmjjsy.goya.security.bus.api.IEvent;
import com.ysmjjsy.goya.security.bus.configuration.properties.BusProperties;
import com.ysmjjsy.goya.security.bus.decision.DecisionResult;
import com.ysmjjsy.goya.security.bus.enums.EventStatus;
import com.ysmjjsy.goya.security.bus.serializer.MessageSerializer;
import com.ysmjjsy.goya.security.bus.spi.EventRecord;
import com.ysmjjsy.goya.security.bus.store.EventStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.ZoneId;
import java.util.*;

/**
 * 基于Redis的消息存储实现（Outbox模式）
 * <p>
 * 使用Redis Hash结构存储消息记录，支持：
 * - 消息状态管理
 * - 重试队列
 * - 状态统计
 * - 过期清理
 *
 * @author goya
 * @since 2025/6/27 17:48
 */
@Slf4j
@RequiredArgsConstructor
public class RedisMessageStore implements EventStore {

    private final RedisTemplate<String, String> redisTemplate;
    private final MessageSerializer messageSerializer;
    private final BusProperties busProperties;

    // Redis Key 前缀
    private static final String EVENT_RECORD_PREFIX = "redis:event:";
    private static final String STATUS_INDEX_PREFIX = "redis:status:";
    private static final String RETRY_QUEUE_PREFIX = "redis:retry:";
    private static final String READY_QUEUE_KEY = "redis:ready";
    private static final String ERROR_INFO_PREFIX = "redis:error:";

    @Override
    public void save(EventRecord record) {
        try {
            IEvent event = record.getEvent();
            if (event == null) {
                throw new IllegalArgumentException("Event cannot be null");
            }

            String eventId = event.getEventId();
            String key = buildEventKey(eventId);

            // 序列化完整的EventRecord对象
            byte[] serializedEventData = messageSerializer.serialize(record.getEvent());
            String serializedRecord = Base64.getEncoder().encodeToString(serializedEventData);

            byte[] serializedDecisionData = messageSerializer.serialize(record.getDecision());
            String serializedDecision = Base64.getEncoder().encodeToString(serializedDecisionData);
            Map<String, String> eventData = new HashMap<>();
            eventData.put("event", serializedRecord);
            eventData.put("decision", serializedDecision);
            eventData.put("eventId", eventId);
            eventData.put("eventKey", event.getEventKey());
            eventData.put("status", event.getEventStatus().name());
            eventData.put("createTime", String.valueOf(event.getCreateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()));
            eventData.put("priority", String.valueOf(event.getPriority()));
            eventData.put("eventClass", record.getEvent().getClass().getName());

            // 保存到Redis Hash
            redisTemplate.opsForHash().putAll(key, eventData);

            log.debug("Saved event record: {}, status: {}", eventId, event.getEventStatus());

        } catch (Exception e) {
            log.error("Failed to save event record: {}", record.getEvent() != null ? record.getEvent().getEventId() : "unknown", e);
            throw new RuntimeException("Failed to save event record", e);
        }
    }

    @Override
    public void updateStatus(String eventId, EventStatus status, String errorMessage) {
        try {
            String key = buildEventKey(eventId);

            // 检查记录是否存在
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
                log.warn("Event record not found for eventId: {}", eventId);
                return;
            }

            // 获取当前EventRecord
            EventRecord record = findById(eventId);
            if (record == null) {
                log.warn("Cannot find event record for eventId: {}", eventId);
                return;
            }

            // 获取当前状态
            EventStatus currentStatus = record.getEvent().getEventStatus();

            // 更新事件状态
            record.getEvent().setEventStatus(status);

            // 重新序列化并保存

            String serializedRecord = (String) redisTemplate.opsForHash().get(key, "event");
            String eventClassName = (String) redisTemplate.opsForHash().get(key, "eventClass");

            byte[] serializedRecordData = Base64.getDecoder().decode(serializedRecord);
            Class<?> eventClass = Class.forName(eventClassName);
            IEvent deserialize = (IEvent) messageSerializer.deserialize(serializedRecordData, eventClass);

            deserialize.setEventStatus(currentStatus);
            // 序列化完整的EventRecord对象
            byte[] serializedEventData = messageSerializer.serialize(record.getEvent());
            String serializedRecordAfter = Base64.getEncoder().encodeToString(serializedEventData);
            redisTemplate.opsForHash().put(key, "event", serializedRecordAfter);
            redisTemplate.opsForHash().put(key, "status", status.name());

            // 保存错误信息（如果有）
            if (errorMessage != null) {
                redisTemplate.opsForValue().set(busProperties.getBusPrefix() + ":" + ERROR_INFO_PREFIX + eventId, errorMessage);
            }

            log.debug("Updated event status: {}, from {} to {}", eventId, currentStatus, status);

        } catch (Exception e) {
            log.error("Failed to update event status: {}", eventId, e);
            throw new RuntimeException("Failed to update event status", e);
        }
    }

    @Override
    public EventRecord findById(String eventId) {
        try {
            String key = buildEventKey(eventId);
            return serializeEventRecord(key);

        } catch (Exception e) {
            log.error("Failed to find event record: {}", eventId, e);
            return null;
        }
    }


    private EventRecord serializeEventRecord(String key) {
        try {
            String serializedRecord = (String) redisTemplate.opsForHash().get(key, "event");
            String serializedDecision = (String) redisTemplate.opsForHash().get(key, "decision");
            String eventClassName = (String) redisTemplate.opsForHash().get(key, "eventClass");

            byte[] serializedRecordData = Base64.getDecoder().decode(serializedRecord);
            byte[] serializedDecisionData = Base64.getDecoder().decode(serializedDecision);
            Class<?> eventClass = Class.forName(eventClassName);
            Object deserialize = messageSerializer.deserialize(serializedRecordData, eventClass);
            DecisionResult decisionResult = messageSerializer.deserialize(serializedDecisionData, DecisionResult.class);
            return new EventRecord((IEvent) deserialize, decisionResult);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize event record", e);
        }
    }

    @Override
    public List<EventRecord> findReadyToSend(int limit) {
        try {
            // 从准备队列中获取事件ID
            List<String> eventIds = redisTemplate.opsForList().range(busProperties.getBusPrefix() + ":" + READY_QUEUE_KEY, 0, limit - 1);

            if (eventIds == null || eventIds.isEmpty()) {
                return Collections.emptyList();
            }

            List<EventRecord> result = new ArrayList<>();
            for (String eventId : eventIds) {
                EventRecord record = findById(eventId);
                if (record != null && record.getEvent().getEventStatus() == EventStatus.PENDING) {
                    result.add(record);
                    // 从准备队列中移除
                    redisTemplate.opsForList().remove(busProperties.getBusPrefix() + ":" + READY_QUEUE_KEY, 1, eventId);
                }
            }

            log.debug("Found {} ready to send events", result.size());
            return result;

        } catch (Exception e) {
            log.error("Failed to find ready to send events", e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<EventRecord> findRetryable(int limit) {
        try {
            // 从重试队列中获取事件ID（按分数排序，分数越小优先级越高）
            Set<String> eventIds = redisTemplate.opsForZSet().range(busProperties.getBusPrefix() + ":" + RETRY_QUEUE_PREFIX + "default", 0, limit - 1);

            if (eventIds == null || eventIds.isEmpty()) {
                return Collections.emptyList();
            }

            List<EventRecord> result = new ArrayList<>();
            long currentTime = System.currentTimeMillis();

            for (String eventId : eventIds) {
                // 检查是否到了重试时间
                Double score = redisTemplate.opsForZSet().score(busProperties.getBusPrefix() + ":" + RETRY_QUEUE_PREFIX + "default", eventId);
                if (score != null && score <= currentTime) {
                    EventRecord record = findById(eventId);
                    if (record != null && (record.getEvent().getEventStatus() == EventStatus.FAILED
                            || record.getEvent().getEventStatus() == EventStatus.RETRYING)) {
                        result.add(record);
                        // 从重试队列中移除
                        redisTemplate.opsForZSet().remove(busProperties.getBusPrefix() + ":" + RETRY_QUEUE_PREFIX + "default", eventId);
                    }
                }
            }

            log.debug("Found {} retryable events", result.size());
            return result;

        } catch (Exception e) {
            log.error("Failed to find retryable events", e);
            return Collections.emptyList();
        }
    }

    @Override
    public void delete(String messageId) {
        try {
            String key = buildEventKey(messageId);

            // 获取当前状态用于清理索引
            String statusStr = (String) redisTemplate.opsForHash().get(key, "status");
            if (statusStr != null) {
                EventStatus status = EventStatus.valueOf(statusStr);
                updateStatusIndex(messageId, status, null);
            }

            // 删除事件记录
            redisTemplate.delete(key);

            // 删除错误信息
            redisTemplate.delete(busProperties.getBusPrefix() + ":" + ERROR_INFO_PREFIX + messageId);

            // 从各个队列中移除
            redisTemplate.opsForList().remove(busProperties.getBusPrefix() + ":" + READY_QUEUE_KEY, 0, messageId);
            redisTemplate.opsForZSet().remove(busProperties.getBusPrefix() + ":" + RETRY_QUEUE_PREFIX + "default", messageId);

            log.debug("Deleted event record: {}", messageId);

        } catch (Exception e) {
            log.error("Failed to delete event record: {}", messageId, e);
        }
    }

    @Override
    public int deleteExpiredSuccessRecords(long beforeTime) {
        try {
            Set<String> statusKeys = redisTemplate.keys(busProperties.getBusPrefix() + ":" + STATUS_INDEX_PREFIX + EventStatus.SUCCESS.name() + ":*");
            if (statusKeys == null || statusKeys.isEmpty()) {
                return 0;
            }

            int deletedCount = 0;
            for (String statusKey : statusKeys) {
                Set<String> eventIds = redisTemplate.opsForSet().members(statusKey);
                if (eventIds != null) {
                    for (String eventId : eventIds) {
                        String createTimeStr = (String) redisTemplate.opsForHash().get(buildEventKey(eventId), "createTime");
                        if (createTimeStr != null) {
                            long createTime = Long.parseLong(createTimeStr);
                            if (createTime < beforeTime) {
                                delete(eventId);
                                deletedCount++;
                            }
                        }
                    }
                }
            }

            log.debug("Deleted {} expired success records", deletedCount);
            return deletedCount;

        } catch (Exception e) {
            log.error("Failed to delete expired success records", e);
            return 0;
        }
    }

    @Override
    public Map<EventStatus, Long> countByStatus() {
        try {
            Map<EventStatus, Long> result = new HashMap<>();

            for (EventStatus status : EventStatus.values()) {
                Set<String> statusKeys = redisTemplate.keys(busProperties.getBusPrefix() + ":" + STATUS_INDEX_PREFIX + status.name() + ":*");
                long count = 0;

                if (statusKeys != null) {
                    for (String statusKey : statusKeys) {
                        Long size = redisTemplate.opsForSet().size(statusKey);
                        if (size != null) {
                            count += size;
                        }
                    }
                }

                result.put(status, count);
            }

            return result;

        } catch (Exception e) {
            log.error("Failed to count by status", e);
            return Collections.emptyMap();
        }
    }

    /**
     * 更新状态索引
     */
    private void updateStatusIndex(String eventId, EventStatus oldStatus, EventStatus newStatus) {
        // 从旧状态索引中移除
        if (oldStatus != null) {
            String oldStatusKey = busProperties.getBusPrefix() + ":" + STATUS_INDEX_PREFIX + oldStatus.name() + ":" + getShardIndex(eventId);
            redisTemplate.opsForSet().remove(oldStatusKey, eventId);
        }

        // 添加到新状态索引
        if (newStatus != null) {
            String newStatusKey = busProperties.getBusPrefix() + ":" + STATUS_INDEX_PREFIX + newStatus.name() + ":" + getShardIndex(eventId);
            redisTemplate.opsForSet().add(newStatusKey, eventId);
        }
    }

    /**
     * 更新队列
     */
    private void updateQueues(String eventId, EventStatus oldStatus, EventStatus newStatus, EventRecord record) {
        // 根据新状态决定队列操作
        switch (newStatus) {
            case PENDING:
                redisTemplate.opsForList().leftPush(busProperties.getBusPrefix() + ":" + READY_QUEUE_KEY, eventId);
                break;
            case FAILED:
            case RETRYING:
                // 添加到重试队列，使用当前时间+延迟时间作为score
                long retryTime = System.currentTimeMillis() + calculateRetryDelay(record);
                redisTemplate.opsForZSet().add(busProperties.getBusPrefix() + ":" + RETRY_QUEUE_PREFIX + "default", eventId, retryTime);
                break;
            case SUCCESS:
            case DEAD_LETTER:
            case EXPIRED:
            case CANCELLED:
                // 从准备队列和重试队列中移除
                redisTemplate.opsForList().remove(busProperties.getBusPrefix() + ":" + READY_QUEUE_KEY, 0, eventId);
                redisTemplate.opsForZSet().remove(busProperties.getBusPrefix() + ":" + RETRY_QUEUE_PREFIX + "default", eventId);
                break;
        }
    }

    /**
     * 计算重试延迟时间（毫秒）
     */
    private long calculateRetryDelay(EventRecord record) {
        // 默认重试延迟策略：指数退避，基础延迟1秒
        if (record != null && record.getDecision() != null && record.getDecision().getRetryTimes() != null) {
            int retryCount = record.getDecision().getRetryTimes();
            return Math.min(1000L * (1L << retryCount), 300000L); // 最大5分钟
        }
        return 1000L; // 默认1秒
    }

    /**
     * 构建事件Key
     */
    private String buildEventKey(String eventId) {
        return busProperties.getBusPrefix() + ":" + EVENT_RECORD_PREFIX + eventId;
    }

    /**
     * 获取分片索引
     */
    private int getShardIndex(String eventId) {
        return Math.abs(eventId.hashCode()) % 10;
    }
}
