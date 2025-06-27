package com.ysmjjsy.goya.security.bus.core;

import com.ysmjjsy.goya.security.bus.api.IEvent;
import com.ysmjjsy.goya.security.bus.api.IEventBus;
import com.ysmjjsy.goya.security.bus.api.PublishResult;
import com.ysmjjsy.goya.security.bus.decision.DecisionResult;
import com.ysmjjsy.goya.security.bus.decision.MessageConfigDecision;
import com.ysmjjsy.goya.security.bus.duplicate.MessageDeduplicator;
import com.ysmjjsy.goya.security.bus.encry.MessageEncryptor;
import com.ysmjjsy.goya.security.bus.enums.MessageStatus;
import com.ysmjjsy.goya.security.bus.enums.MessageType;
import com.ysmjjsy.goya.security.bus.enums.ReliabilityLevel;
import com.ysmjjsy.goya.security.bus.enums.TransportType;
import com.ysmjjsy.goya.security.bus.route.RoutingContext;
import com.ysmjjsy.goya.security.bus.serializer.MessageSerializer;
import com.ysmjjsy.goya.security.bus.spi.MessageRecord;
import com.ysmjjsy.goya.security.bus.spi.TransportMessage;
import com.ysmjjsy.goya.security.bus.spi.TransportResult;
import com.ysmjjsy.goya.security.bus.store.MessageStore;
import com.ysmjjsy.goya.security.bus.transport.MessageTransport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 默认事件总线实现
 * <p>
 * 实现 IEventBus 接口，提供消息发布的核心功能
 * 集成智能决策引擎，支持多种传输层和路由策略
 *
 * @author goya
 * @since 2025/6/24
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractEventBus implements IEventBus {

    private final MessageConfigDecision messageConfigDecision;
    private final TaskExecutor busTaskExecutor;
    private final LocalEventBus localEventBus;
    private final MessageSerializer messageSerializer;
    private final MessageStore messageStore;
    private final MessageDeduplicator messageDeduplicator;
    private Map<String, MessageTransport> messageTransportMap;

    @Override
    public PublishResult publish(IEvent event) {
        return publish(event, null);
    }

    @Override
    public PublishResult publish(IEvent event, MessageConfigHint hint) {
        if (event == null) {
            return PublishResult.failure("Event cannot be null");
        }

        try {
            // 验证事件
            validateEvent(event);

            // 智能决策
            DecisionResult decision = messageConfigDecision.decide(event, hint);
            log.debug("Decision result for event {}: {}", event.getEventId(), decision);

            // 根据路由范围决定处理方式
            switch (decision.getRouteScope()) {
                case LOCAL_ONLY:
                    return publishLocal(event, decision);
                case REMOTE_ONLY:
                    return publishRemote(event, decision);
                case AUTO:
                default:
                    return publishAuto(event, decision);
            }

        } catch (Exception e) {
            log.error("Failed to publish event: {}", event.getEventId(), e);
            return PublishResult.failure("Failed to publish event: " + e.getMessage(), e);
        }
    }

    @Override
    public PublishResult publishDelayed(IEvent event, Duration delay) {
        MessageConfigHint hint = MessageConfigHint.delayed(delay);
        return publish(event, hint);
    }

    @Override
    public PublishResult publishScheduled(IEvent event, LocalDateTime deliverTime) {
        MessageConfigHint hint = MessageConfigHint.scheduled(deliverTime);
        return publish(event, hint);
    }

    @Override
    public PublishResult publishOrdered(IEvent event, String sequenceKey) {
        MessageConfigHint hint = MessageConfigHint.ordered(sequenceKey);
        return publish(event, hint);
    }

    @Override
    public PublishResult publishTransactional(IEvent event) {
        MessageConfigHint hint = MessageConfigHint.transactional();
        return publish(event, hint);
    }

    /**
     * 仅本地发布
     */
    private PublishResult publishLocal(IEvent event, DecisionResult decision) {
        log.debug("Publishing event {} locally", event.getEventId());
        boolean success = localEventBus.publish(event);
        if (success) {
            return PublishResult.success(event.getEventId(), TransportType.LOCAL);
        } else {
            return PublishResult.failure("No local listeners found for event: " + event.getEventType());
        }
    }

    /**
     * 仅远程发布
     */
    private PublishResult publishRemote(IEvent event, DecisionResult decision) {
        log.debug("Publishing event {} remotely via {}", event.getEventId(), decision.getTransportType());
        return sendToRemoteTransport(event, decision);
    }

    /**
     * 自动路由（混合模式）
     */
    private PublishResult publishAuto(IEvent event, DecisionResult decision) {
        log.debug("Publishing event {} in auto mode", event.getEventId());

        // 首先尝试本地发布
        boolean hasLocalListeners = localEventBus.hasListeners(event.getEventType());
        if (hasLocalListeners) {
            boolean localSuccess = localEventBus.publish(event);
            log.debug("Local publish result for {}: {}", event.getEventId(), localSuccess);
        }

        // 根据可靠性级别和业务需求决定是否需要远程发布
        if (needsRemotePublish(decision, hasLocalListeners)) {
            PublishResult remoteResult = sendToRemoteTransport(event, decision);
            if (!remoteResult.isSuccess()) {
                return remoteResult; // 远程发布失败
            }
            return PublishResult.success(event.getEventId(), decision.getTransportType());
        }

        // 仅本地发布成功
        return PublishResult.success(event.getEventId(), TransportType.LOCAL);
    }

    /**
     * 发送到远程传输层
     */
    private PublishResult sendToRemoteTransport(IEvent event, DecisionResult decision) {

        MessageTransport transport = messageConfigDecision.getRegisteredTransports().get(decision.getTransportType());
        if (transport == null) {
            return PublishResult.failure("No transport registered for type: " + decision.getTransportType());
        }
        try {
            // 幂等性检查
            if (messageDeduplicator != null && messageDeduplicator.isDuplicate(event.getEventId())) {
                log.warn("Duplicate message detected, skipping: {}", event.getEventId());
                return PublishResult.success(event.getEventId(), decision.getTransportType());
            }

            // 构建传输消息
            TransportMessage transportMessage = buildTransportMessage(event, decision);

            // 如果需要可靠投递，先保存到MessageStore
            if (decision.isPersistent()) {
                MessageRecord record = MessageRecord.fromTransportMessage(transportMessage, decision.getTransportType().name());
                messageStore.save(record);
                log.debug("Saved message to store for reliable delivery: {}", event.getEventId());

                // 异步发送
                busTaskExecutor.execute(() -> sendReliableMessage(record, transport));
                return PublishResult.success(event.getEventId(), decision.getTransportType());
            }

            // 直接发送消息
            TransportResult result = transport.send(transportMessage);

            if (result.isSuccess()) {
                // 标记消息已处理（用于去重）
                if (messageDeduplicator != null) {
                    messageDeduplicator.markAsProcessed(event.getEventId());
                }
                return PublishResult.success(result.getMessageId(), decision.getTransportType());
            } else {
                return PublishResult.failure("Transport send failed: " + result.getErrorMessage(), result.getThrowable());
            }

        } catch (Exception e) {
            return PublishResult.failure("Failed to send via transport: " + e.getMessage(), e);
        }
    }

    /**
     * 构建传输消息
     */
    private TransportMessage buildTransportMessage(IEvent event, DecisionResult decision) {
        // 序列化事件
        byte[] body = messageSerializer.serialize(event);

        // 处理加密
        if (decision.isEnableEncryption()) {
            try {
                MessageEncryptor encryptor = new MessageEncryptor();
                MessageEncryptor.EncryptionResult encryptionResult =
                        encryptor.encrypt(body, MessageEncryptor.EncryptionType.AES_128);
                if (encryptionResult.isEncrypted()) {
                    body = encryptionResult.getData();
                    log.debug("Message encrypted: {} -> {} bytes",
                            encryptionResult.getOriginalSize(), encryptionResult.getEncryptedSize());
                }
            } catch (Exception e) {
                log.warn("Failed to encrypt message, using original data", e);
            }
        }

        // 构建消息头
        Map<String, Object> headers = new HashMap<>();
        headers.put("eventId", event.getEventId());
        headers.put("eventType", event.getEventType());
        headers.put("eventClass", event.getClass().getName());
        headers.put("createTime", event.getCreateTime());
        headers.put("messageModel", decision.getMessageModel().name());
        headers.put("messageType", decision.getMessageType().name());
        headers.put("reliabilityLevel", decision.getReliabilityLevel().name());

        // 添加高级特性标识
        if (decision.getBusinessPriority() != null) {
            headers.put("businessPriority", decision.getBusinessPriority().name());
        }
        if (decision.isEnableCompression()) {
            headers.put("compressed", true);
            headers.put("compressionType", "gzip");
            headers.put("originalSize", body.length);
        }
        if (decision.isEnableEncryption()) {
            headers.put("encrypted", true);
            headers.put("encryptionType", "aes128");
        }
        if (decision.isPerformanceSensitive()) {
            headers.put("performanceSensitive", true);
        }

        // 合并自定义属性
        Map<String, Object> customProperties = new HashMap<>();
        if (event.getMetadata() != null) {
            customProperties.putAll(event.getMetadata());
        }
        if (decision.getCustomProperties() != null) {
            customProperties.putAll(decision.getCustomProperties());
        }

        // 构建传输消息
        TransportMessage.TransportMessageBuilder builder = TransportMessage.builder()
                .messageId(event.getEventId())
                .routingContext(decision.getRoutingContext())
                .body(body)
                .originalBodySize(body.length)
                .headers(headers)
                .properties(event.getProperties())
                .customProperties(customProperties.isEmpty() ? null : customProperties)
                .messageType(event.getEventType())
                .priority(event.getPriority())
                .sequenceKey(decision.getSequenceKey())
                .delayTime(decision.getDelayTime())
                .deliverTime(decision.getDeliverTime())
                .ttl(decision.getMessageTtl() == null ? null : decision.getMessageTtl().toMillis())
                .businessPriority(decision.getBusinessPriority())
                .enableCompression(decision.isEnableCompression())
                .enableEncryption(decision.isEnableEncryption())
                .persistent(decision.isPersistent())
                .retryTimes(decision.getRetryTimes())
                .performanceSensitive(decision.isPerformanceSensitive());
        return builder.build();
    }

    /**
     * 判断是否需要远程发布
     */
    private boolean needsRemotePublish(DecisionResult decision, boolean hasLocalListeners) {
        // 事务消息总是需要远程发布
        if (decision.getReliabilityLevel() == ReliabilityLevel.TRANSACTIONAL) {
            return true;
        }

        // 延迟/定时消息需要远程发布
        if (decision.getMessageType() == MessageType.DELAYED || decision.getMessageType() == MessageType.SCHEDULED) {
            return true;
        }

        // 顺序消息需要远程发布
        if (decision.getMessageType() == MessageType.ORDERED) {
            return true;
        }

        // 如果没有本地监听器，且不是仅发后不管的消息，则需要远程发布
        if (!hasLocalListeners && decision.getReliabilityLevel() != ReliabilityLevel.FIRE_AND_FORGET) {
            return true;
        }

        return false;
    }

    /**
     * 验证事件
     */
    private void validateEvent(IEvent event) {
        if (!StringUtils.hasText(event.getEventId())) {
            throw new IllegalArgumentException("Event ID cannot be empty");
        }
        if (!StringUtils.hasText(event.getEventType())) {
            throw new IllegalArgumentException("Event type cannot be empty");
        }
    }

    /**
     * 发送可靠消息（带重试）
     */
    private void sendReliableMessage(MessageRecord record, MessageTransport transport) {
        try {
            // 更新状态为发送中
            messageStore.updateStatus(record.getMessageId(), MessageStatus.SENDING, null);

            // 注意：这里不重新生成路由上下文，因为在重试时很难完整恢复
            // 我们依赖 RabbitMQTransport 中的回退逻辑来处理没有路由上下文的情况


            RoutingContext routingContext = RoutingContext.builder()
                    .businessDomain(record.getBusinessDomain())
                    .eventType(record.getEventType())
                    .consumerGroup(record.getConsumerGroup())
                    .routingSelector(record.getRoutingSelector())
                    .messageModel(record.getMessageModel())
                    .build();

            // 重新构建传输消息
            TransportMessage transportMessage = TransportMessage.builder()
                    .messageId(record.getMessageId())
                    .routingContext(routingContext)
                    .body(record.getBody())
                    .headers(record.getHeaders())
                    .properties(record.getProperties())
                    .priority(record.getPriority())
                    .ttl(record.getTtl())
                    .delayTime(record.getDelayTime())
                    .deliverTime(record.getDeliverTime())
                    .sequenceKey(record.getSequenceKey())
                    .build();

            // 发送消息
            TransportResult result = transport.send(transportMessage);

            if (result.isSuccess()) {
                // 发送成功
                messageStore.updateStatus(record.getMessageId(), MessageStatus.SUCCESS, null);
                // 标记已处理用于去重
                if (messageDeduplicator != null) {
                    messageDeduplicator.markAsProcessed(record.getMessageId());
                }
                log.debug("Reliable message sent successfully: {}", record.getMessageId());
            } else {
                // 发送失败，准备重试
                handleSendFailure(record, result.getErrorMessage(), result.getThrowable());
            }

        } catch (Exception e) {
            log.error("Failed to send reliable message: {}", record.getMessageId(), e);
            handleSendFailure(record, e.getMessage(), e);
        }
    }

    /**
     * 处理发送失败
     */
    private void handleSendFailure(MessageRecord record, String errorMessage, Throwable throwable) {
        record.incrementRetryCount();

        if (record.canRetry()) {
            // 计算下次重试时间（简单的指数退避）
            long retryDelay = Math.min(1000L * (1L << record.getRetryCount()), 60000L); // 最大1分钟
            LocalDateTime nextRetryTime = LocalDateTime.now().plusNanos(retryDelay * 1_000_000);
            record.setNextRetryTime(nextRetryTime);

            messageStore.updateStatus(record.getMessageId(), MessageStatus.FAILED, errorMessage);
            log.warn("Message send failed, will retry after {}: {} - {}",
                    retryDelay / 1000.0 + "s", record.getMessageId(), errorMessage);
        } else {
            // 超过最大重试次数，进入死信状态
            messageStore.updateStatus(record.getMessageId(), MessageStatus.DEAD_LETTER, errorMessage);
            log.error("Message send failed after {} retries, moved to dead letter: {} - {}",
                    record.getMaxRetryTimes(), record.getMessageId(), errorMessage);
        }
    }

    public LocalEventBus getLocalEventBus() {
        return localEventBus;
    }
} 