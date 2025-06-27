package com.ysmjjsy.goya.security.bus.transport.redis;

import com.ysmjjsy.goya.security.bus.enums.MessageStatus;
import com.ysmjjsy.goya.security.bus.spi.MessageRecord;
import com.ysmjjsy.goya.security.bus.store.MessageStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * <p></p>
 *
 * @author goya
 * @since 2025/6/27 17:48
 */
@Slf4j
@RequiredArgsConstructor
public class RedisMessageStore implements MessageStore {
    @Override
    public void save(MessageRecord record) {

    }

    @Override
    public void updateStatus(String messageId, MessageStatus status, String errorMessage) {

    }

    @Override
    public MessageRecord findById(String messageId) {
        return null;
    }

    @Override
    public List<MessageRecord> findReadyToSend(int limit) {
        return List.of();
    }

    @Override
    public List<MessageRecord> findRetryable(int limit) {
        return List.of();
    }

    @Override
    public void delete(String messageId) {

    }

    @Override
    public int deleteExpiredSuccessRecords(long beforeTime) {
        return 0;
    }

    @Override
    public Map<MessageStatus, Long> countByStatus() {
        return Map.of();
    }
}
