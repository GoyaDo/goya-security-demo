package com.ysmjjsy.goya.security.bus.transport.redis;

import com.ysmjjsy.goya.security.bus.enums.EventStatus;
import com.ysmjjsy.goya.security.bus.spi.EventRecord;
import com.ysmjjsy.goya.security.bus.store.EventStore;
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
public class RedisMessageStore implements EventStore {
    @Override
    public void save(EventRecord record) {

    }

    @Override
    public void updateStatus(String eventId, EventStatus status, String errorMessage) {

    }

    @Override
    public EventRecord findById(String eventId) {
        return null;
    }

    @Override
    public List<EventRecord> findReadyToSend(int limit) {
        return List.of();
    }

    @Override
    public List<EventRecord> findRetryable(int limit) {
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
    public Map<EventStatus, Long> countByStatus() {
        return Map.of();
    }
}
