package com.ysmjjsy.goya.security.bus.core;

import com.ysmjjsy.goya.security.bus.enums.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 消息配置提示类
 * <p>
 * 使用建造者模式为智能决策引擎提供配置提示
 * 这些提示将影响消息的模型、类型、可靠性、路由、传输方式等
 *
 * @author goya
 * @since 2025/6/24
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageConfigHint {

    /**
     * 消息模型提示 - QUEUE 或 TOPIC
     */
    private EventModel eventModel;

    /**
     * 消息类型提示 - NORMAL, DELAYED, SCHEDULED, ORDERED
     */
    private EventType eventType;

    /**
     * 可靠性级别提示 - FIRE_AND_FORGET, RELIABLE, TRANSACTIONAL
     */
    private ReliabilityLevel reliabilityLevel;

    /**
     * 传输层偏好 - RABBITMQ, KAFKA, ROCKETMQ, REDIS, LOCAL
     */
    private TransportType transportType;

    /**
     * 延迟时长 - 用于延迟消息
     */
    private Duration delayTime;

    /**
     * 投递时间 - 用于定时消息
     */
    private LocalDateTime deliverTime;

    /**
     * 顺序消息键 - 用于有序消息
     */
    private String sequenceKey;

    /**
     * 消息TTL（生存时间）
     */
    private Duration ttl;

    /**
     * 是否本地记录
     */
    @Builder.Default
    private boolean localRecord = true;

    /**
     * 是否持久化
     */
    @Builder.Default
    private boolean persistent = true;

    /**
     * 重试次数
     */
    private Integer retryTimes;

    /**
     * 是否启用压缩
     */
    @Builder.Default
    private Boolean enableCompression = false;

    /**
     * 是否启用加密
     */
    @Builder.Default
    private Boolean enableEncryption = false;

    /**
     * 是否启用幂等
     */
    @Builder.Default
    private Boolean idempotence = false;

    /**
     * 事务ID
     */
    private String transactionalId;

    /**
     * 自定义属性
     * 用于传递额外信息
     */
    private Map<String, Object> properties;

    /**
     * 创建延迟消息提示
     *
     * @param delay 延迟时长
     * @return 延迟消息提示
     */
    public static MessageConfigHint delayed(Duration delay) {
        return MessageConfigHint.builder()
                .eventType(EventType.DELAYED)
                .delayTime(delay)
                .build();
    }

    /**
     * 创建定时消息提示
     *
     * @param deliverTime 投递时间
     * @return 定时消息提示
     */
    public static MessageConfigHint scheduled(LocalDateTime deliverTime) {
        return MessageConfigHint.builder()
                .eventType(EventType.SCHEDULED)
                .deliverTime(deliverTime)
                .build();
    }

    /**
     * 创建顺序消息提示
     *
     * @param sequenceKey 顺序键
     * @return 顺序消息提示
     */
    public static MessageConfigHint ordered(String sequenceKey) {
        return MessageConfigHint.builder()
                .eventType(EventType.ORDERED)
                .sequenceKey(sequenceKey)
                .build();
    }

    /**
     * 创建事务消息提示
     *
     * @return 事务消息提示
     */
    public static MessageConfigHint transactional() {
        return MessageConfigHint.builder()
                .reliabilityLevel(ReliabilityLevel.TRANSACTIONAL)
                .build();
    }
} 