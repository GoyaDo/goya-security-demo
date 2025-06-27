package com.ysmjjsy.goya.security.bus.spi;

import com.ysmjjsy.goya.security.bus.enums.MessageModel;
import com.ysmjjsy.goya.security.bus.enums.MessageStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.BeanUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 消息记录实体
 * 
 * 用于Outbox模式的消息持久化，记录消息的完整信息和状态
 *
 * @author goya
 * @since 2025/6/24
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageRecord {

    /**
     * 消息唯一ID
     */
    private String messageId;

    /**
     * 消息模型
     */
    private MessageModel messageModel;

    /**
     * 业务域（用于Exchange/Topic命名）
     */
    private String businessDomain;

    /**
     * 事件类型（用于路由键）
     */
    private String eventType;

    /**
     * 消费者组（用于Queue命名）
     */
    private String consumerGroup;

    /**
     * 路由选择器（支持通配符）
     */
    private String routingSelector;

    /**
     * 消息体（序列化后的字节数组）
     */
    private byte[] body;

    /**
     * 消息头
     */
    private Map<String, Object> headers;

    /**
     * 消息属性
     */
    private Map<String, String> properties;

    /**
     * 消息状态
     */
    private MessageStatus status;

    /**
     * 传输层类型
     */
    private String transportType;

    /**
     * 消息优先级
     */
    private Integer priority;

    /**
     * 重试次数
     */
    private Integer retryCount;

    /**
     * 最大重试次数
     */
    private Integer maxRetryTimes;

    /**
     * 下次重试时间
     */
    private LocalDateTime nextRetryTime;

    /**
     * TTL（消息存活时间，毫秒）
     */
    private Long ttl;

    /**
     * 延迟时间
     */
    private Duration delayTime;

    /**
     * 定时投递时间
     */
    private LocalDateTime deliverTime;

    /**
     * 顺序消息键
     */
    private String sequenceKey;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 最后错误信息
     */
    private String lastErrorMessage;


    /**
     * 创建新的消息记录
     *
     * @param transportMessage 传输消息
     * @param transportType    传输类型
     * @return 消息记录
     */
    public static MessageRecord fromTransportMessage(TransportMessage transportMessage, String transportType) {
        MessageRecord record = new MessageRecord();
        BeanUtils.copyProperties(transportMessage, record);
        record.setTransportType(transportType);
        record.setBusinessDomain(transportMessage.getRoutingContext().getBusinessDomain());
        record.setEventType(transportMessage.getRoutingContext().getEventType());
        record.setConsumerGroup(transportMessage.getRoutingContext().getConsumerGroup());
        record.setRoutingSelector(transportMessage.getRoutingContext().getRoutingSelector());
        record.setMessageModel(transportMessage.getRoutingContext().getMessageModel());
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        record.setStatus(MessageStatus.PENDING);
        return record;
    }

    /**
     * 增加重试次数
     */
    public void incrementRetryCount() {
        this.retryCount = (this.retryCount == null ? 0 : this.retryCount) + 1;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 是否可以重试
     *
     * @return true如果可以重试
     */
    public boolean canRetry() {
        return this.retryCount != null && this.maxRetryTimes != null && 
               this.retryCount < this.maxRetryTimes;
    }

    /**
     * 是否已过期
     *
     * @return true如果消息已过期
     */
    public boolean isExpired() {
        if (ttl == null || createTime == null) {
            return false;
        }
        return LocalDateTime.now().isAfter(createTime.plusNanos(ttl * 1_000_000));
    }

    /**
     * 是否到达投递时间
     *
     * @return true如果到达投递时间
     */
    public boolean isReadyToDeliver() {
        if (deliverTime == null) {
            return true;
        }
        return LocalDateTime.now().isAfter(deliverTime) || LocalDateTime.now().isEqual(deliverTime);
    }
} 