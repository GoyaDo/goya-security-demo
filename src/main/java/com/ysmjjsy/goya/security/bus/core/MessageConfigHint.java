package com.ysmjjsy.goya.security.bus.core;

import com.ysmjjsy.goya.security.bus.enums.*;
import lombok.Builder;
import lombok.Data;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 消息配置提示类
 * 
 * 使用建造者模式为智能决策引擎提供配置提示
 * 这些提示将影响消息的模型、类型、可靠性、路由、传输方式等
 *
 * @author goya
 * @since 2025/6/24
 */
@Data
@Builder
public class MessageConfigHint {

    /**
     * 消息模型提示 - QUEUE 或 TOPIC
     */
    private MessageModel messageModel;

    /**
     * 消息类型提示 - NORMAL, DELAYED, SCHEDULED, ORDERED
     */
    private MessageType messageType;

    /**
     * 可靠性级别提示 - FIRE_AND_FORGET, RELIABLE, TRANSACTIONAL
     */
    private ReliabilityLevel reliabilityLevel;

    /**
     * 路由范围提示 - LOCAL_ONLY, REMOTE_ONLY, AUTO
     */
    private RouteScope routeScope;

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
     * 是否对性能敏感
     * true表示优先考虑性能，可能牺牲一些可靠性
     */
    @Builder.Default
    private Boolean performanceSensitive = false;

    /**
     * 业务优先级
     * 用于指导框架在资源有限时的处理策略
     */
    private BusinessPriority businessPriority;

    /**
     * 消息TTL（生存时间）
     */
    private Duration messageTtl;

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
     * 自定义属性
     * 用于传递额外的业务提示信息
     */
    private Map<String, Object> customProperties;

    /**
     * 创建仅本地路由的提示
     *
     * @return 本地路由提示
     */
    public static MessageConfigHint localOnly() {
        return MessageConfigHint.builder()
                .routeScope(RouteScope.LOCAL_ONLY)
                .build();
    }

    /**
     * 创建仅远程路由的提示
     *
     * @return 远程路由提示
     */
    public static MessageConfigHint remoteOnly() {
        return MessageConfigHint.builder()
                .routeScope(RouteScope.REMOTE_ONLY)
                .build();
    }

    /**
     * 创建延迟消息提示
     *
     * @param delay 延迟时长
     * @return 延迟消息提示
     */
    public static MessageConfigHint delayed(Duration delay) {
        return MessageConfigHint.builder()
                .messageType(MessageType.DELAYED)
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
                .messageType(MessageType.SCHEDULED)
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
                .messageType(MessageType.ORDERED)
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

    /**
     * 创建高性能消息提示
     *
     * @return 高性能消息提示
     */
    public static MessageConfigHint highPerformance() {
        return MessageConfigHint.builder()
                .reliabilityLevel(ReliabilityLevel.FIRE_AND_FORGET)
                .performanceSensitive(true)
                .build();
    }

    /**
     * 创建高可靠性消息提示
     *
     * @return 高可靠性消息提示
     */
    public static MessageConfigHint highReliability() {
        return MessageConfigHint.builder()
                .reliabilityLevel(ReliabilityLevel.RELIABLE)
                .performanceSensitive(false)
                .build();
    }
} 