package com.ysmjjsy.goya.security.bus.transport.redis;

import com.ysmjjsy.goya.security.bus.duplicate.MessageDeduplicator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;

/**
 * 基于Redis的消息去重器实现
 * <p>
 * 使用Redis SET数据结构实现消息幂等性检查
 * 支持TTL自动过期清理
 *
 * @author goya
 * @since 2025/6/27 17:55
 */
@Slf4j
public class RedisMessageDeduplicator implements MessageDeduplicator {

    private final RedisTemplate<String, String> redisTemplate;
    private static final String DEDUP_KEY_PREFIX = "mq:dedup:";
    private static final Duration DEFAULT_EXPIRE_DURATION = Duration.ofHours(24);

    // Lua脚本：原子性检查并标记
    private static final String CHECK_AND_MARK_SCRIPT = 
        "local key = KEYS[1] " +
        "local value = ARGV[1] " +
        "local ttl = tonumber(ARGV[2]) " +
        "if redis.call('sismember', key, value) == 1 then " +
        "  return 1 " +
        "else " +
        "  redis.call('sadd', key, value) " +
        "  if ttl > 0 then " +
        "    redis.call('expire', key, ttl) " +
        "  end " +
        "  return 0 " +
        "end";

    private final DefaultRedisScript<Long> checkAndMarkScript;

    public RedisMessageDeduplicator(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.checkAndMarkScript = new DefaultRedisScript<>(CHECK_AND_MARK_SCRIPT, Long.class);
    }

    @Override
    public boolean isDuplicate(String messageId) {
        try {
            String key = buildKey(messageId);
            Boolean exists = redisTemplate.opsForSet().isMember(key, messageId);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.error("Failed to check duplicate for messageId: {}", messageId, e);
            // 发生异常时，为了安全起见，认为不是重复消息
            return false;
        }
    }

    @Override
    public void markAsProcessed(String messageId) {
        markAsProcessed(messageId, DEFAULT_EXPIRE_DURATION);
    }

    @Override
    public void markAsProcessed(String messageId, Duration expireAfter) {
        try {
            String key = buildKey(messageId);
            redisTemplate.opsForSet().add(key, messageId);
            
            if (expireAfter != null && !expireAfter.isZero()) {
                redisTemplate.expire(key, expireAfter);
            }
            
            log.debug("Marked message as processed: {}, expire: {}", messageId, expireAfter);
        } catch (Exception e) {
            log.error("Failed to mark message as processed: {}", messageId, e);
        }
    }

    @Override
    public void remove(String messageId) {
        try {
            String key = buildKey(messageId);
            redisTemplate.opsForSet().remove(key, messageId);
            log.debug("Removed dedup record for messageId: {}", messageId);
        } catch (Exception e) {
            log.error("Failed to remove dedup record for messageId: {}", messageId, e);
        }
    }

    @Override
    public boolean checkAndMark(String messageId) {
        return checkAndMark(messageId, DEFAULT_EXPIRE_DURATION);
    }

    @Override
    public boolean checkAndMark(String messageId, Duration expireAfter) {
        try {
            String key = buildKey(messageId);
            long ttlSeconds = expireAfter != null ? expireAfter.getSeconds() : 0;
            
            Long result = redisTemplate.execute(checkAndMarkScript, 
                Collections.singletonList(key), messageId, String.valueOf(ttlSeconds));
            
            boolean isDuplicate = result != null && result == 1L;
            log.debug("CheckAndMark for messageId: {}, isDuplicate: {}", messageId, isDuplicate);
            
            return isDuplicate;
        } catch (Exception e) {
            log.error("Failed to checkAndMark for messageId: {}", messageId, e);
            // 发生异常时，为了安全起见，认为不是重复消息并尝试标记
            markAsProcessed(messageId, expireAfter);
            return false;
        }
    }

    @Override
    public long cleanupExpired() {
        // Redis的TTL机制会自动清理过期的key，这里返回0
        // 如果需要手动清理，可以扫描所有去重key并检查TTL
        try {
            Set<String> keys = redisTemplate.keys(DEDUP_KEY_PREFIX + "*");
            if (keys == null || keys.isEmpty()) {
                return 0;
            }
            
            long cleanedCount = 0;
            for (String key : keys) {
                Long ttl = redisTemplate.getExpire(key);
                if (ttl != null && ttl == -2L) { // key不存在
                    cleanedCount++;
                }
            }
            
            log.debug("Cleanup expired dedup records, count: {}", cleanedCount);
            return cleanedCount;
        } catch (Exception e) {
            log.error("Failed to cleanup expired dedup records", e);
            return 0;
        }
    }

    @Override
    public long count() {
        try {
            Set<String> keys = redisTemplate.keys(DEDUP_KEY_PREFIX + "*");
            if (keys == null) {
                return 0;
            }
            
            long totalCount = 0;
            for (String key : keys) {
                Long size = redisTemplate.opsForSet().size(key);
                if (size != null) {
                    totalCount += size;
                }
            }
            
            return totalCount;
        } catch (Exception e) {
            log.error("Failed to count dedup records", e);
            return 0;
        }
    }

    /**
     * 构建Redis Key
     */
    private String buildKey(String messageId) {
        // 使用消息ID的哈希值作为分片键，避免单个key过大
        int shardIndex = Math.abs(messageId.hashCode()) % 100;
        return DEDUP_KEY_PREFIX + shardIndex;
    }
}
