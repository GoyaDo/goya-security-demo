package com.ysmjjsy.goya.security.bus.transport.redis;

import com.ysmjjsy.goya.security.bus.duplicate.MessageDeduplicator;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

/**
 * <p></p>
 *
 * @author goya
 * @since 2025/6/27 17:55
 */
@Slf4j
public class RedisMessageDeduplicator implements MessageDeduplicator {
    @Override
    public boolean isDuplicate(String messageId) {
        return false;
    }

    @Override
    public void markAsProcessed(String messageId) {

    }

    @Override
    public void markAsProcessed(String messageId, Duration expireAfter) {

    }

    @Override
    public void remove(String messageId) {

    }

    @Override
    public boolean checkAndMark(String messageId) {
        return false;
    }

    @Override
    public boolean checkAndMark(String messageId, Duration expireAfter) {
        return false;
    }

    @Override
    public long cleanupExpired() {
        return 0;
    }

    @Override
    public long count() {
        return 0;
    }
}
