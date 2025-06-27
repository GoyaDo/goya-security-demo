package com.ysmjjsy.goya.security.bus.duplicate;

import java.time.Duration;

/**
 * 消息去重器接口
 * 
 * 用于实现消息的幂等性，防止重复消费
 * 通常基于消息ID进行去重判断
 *
 * @author goya
 * @since 2025/6/24
 */
public interface MessageDeduplicator {

    /**
     * 检查消息是否已被处理过
     *
     * @param messageId 消息ID
     * @return true如果消息已被处理过
     */
    boolean isDuplicate(String messageId);

    /**
     * 标记消息已被处理
     *
     * @param messageId 消息ID
     */
    void markAsProcessed(String messageId);

    /**
     * 标记消息已被处理（带过期时间）
     *
     * @param messageId 消息ID
     * @param expireAfter 过期时间
     */
    void markAsProcessed(String messageId, Duration expireAfter);

    /**
     * 移除去重记录
     *
     * @param messageId 消息ID
     */
    void remove(String messageId);

    /**
     * 检查并标记消息（原子操作）
     * 如果消息未被处理过，则标记为已处理并返回false
     * 如果消息已被处理过，则返回true
     *
     * @param messageId 消息ID
     * @return true如果是重复消息
     */
    boolean checkAndMark(String messageId);

    /**
     * 检查并标记消息（原子操作，带过期时间）
     *
     * @param messageId   消息ID
     * @param expireAfter 过期时间
     * @return true如果是重复消息
     */
    boolean checkAndMark(String messageId, Duration expireAfter);

    /**
     * 清理过期的去重记录
     *
     * @return 清理的记录数
     */
    long cleanupExpired();

    /**
     * 获取去重记录总数
     *
     * @return 记录总数
     */
    long count();
} 