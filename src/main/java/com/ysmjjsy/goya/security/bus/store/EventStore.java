package com.ysmjjsy.goya.security.bus.store;

import com.ysmjjsy.goya.security.bus.enums.EventStatus;
import com.ysmjjsy.goya.security.bus.spi.EventRecord;

import java.util.List;

/**
 * 消息存储接口 (Outbox 模式)
 * 
 * 用于可靠消息投递，先将消息保存到本地存储，再异步发送到MQ
 * 确保消息不丢失，支持事务消息场景
 *
 * @author goya
 * @since 2025/6/24
 */
public interface EventStore {

    /**
     * 保存消息记录
     *
     * @param record 消息记录
     */
    void save(EventRecord record);

    /**
     * 更新消息状态
     *
     * @param eventId    消息ID
     * @param status       新状态
     * @param errorMessage 错误信息（可选）
     */
    void updateStatus(String eventId, EventStatus status, String errorMessage);

    /**
     * 根据ID查找消息记录
     *
     * @param eventId 消息ID
     * @return 消息记录，如果不存在返回null
     */
    EventRecord findById(String eventId);

    /**
     * 查找准备发送的消息
     * 
     * @param limit 最大返回数量
     * @return 待发送的消息列表
     */
    List<EventRecord> findReadyToSend(int limit);

    /**
     * 查找需要重试的消息
     *
     * @param limit 最大返回数量
     * @return 需要重试的消息列表
     */
    List<EventRecord> findRetryable(int limit);

    /**
     * 删除消息记录（发送成功后清理）
     *
     * @param messageId 消息ID
     */
    void delete(String messageId);

    /**
     * 批量删除过期的成功消息记录
     *
     * @param beforeTime 过期时间戳（毫秒）
     * @return 删除的记录数
     */
    int deleteExpiredSuccessRecords(long beforeTime);

    /**
     * 统计各状态的消息数量
     *
     * @return 状态统计映射
     */
    java.util.Map<EventStatus, Long> countByStatus();
} 