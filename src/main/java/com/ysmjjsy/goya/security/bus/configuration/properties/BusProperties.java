package com.ysmjjsy.goya.security.bus.configuration.properties;

import com.ysmjjsy.goya.security.bus.enums.BusRemoteType;
import com.ysmjjsy.goya.security.bus.enums.EventRoutingStrategy;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.io.Serializable;

/**
 * <p>事件总线配置</p>
 *
 * @author goya
 * @since 2025/6/24 16:02
 */
@Data
@ConfigurationProperties("bus")
public class BusProperties implements Serializable {

    private static final long serialVersionUID = -4799391114751021696L;

    /**
     * 是否启用事件总线
     */
    private boolean enabled = true;

    /**
     * 默认事件主题
     */
    private String defaultTopic = "bus-event-default";

    /**
     * 默认事件路由策略
     */
    private EventRoutingStrategy defaultRoutingStrategy = EventRoutingStrategy.defaultRoute();

    /**
     * 默认远程事件类型
     */
    private BusRemoteType defaultRemoteType = BusRemoteType.RABBITMQ;

    /**
     * Redis传输配置
     */
    private Redis redis = new Redis();

    /**
     * RabbitMQ传输配置
     */
    private RabbitMQ rabbitmq = new RabbitMQ();

    @Setter
    @Getter
    public static class Redis{
        /**
         * 是否启用Redis传输
         */
        private boolean enabled = false;
    }

    /**
     * RabbitMQ传输配置
     */
    @Setter
    @Getter
    public static class RabbitMQ {
        /**
         * 是否启用RabbitMQ传输
         */
        private boolean enabled = false;

        /**
         * 默认交换器名称
         */
        private String defaultExchangeName;

        /**
         * 交换器类型
         */
        private String exchangeType = "topic";

        /**
         * 是否持久化交换器
         */
        private boolean durableExchange = true;

        /**
         * 是否自动删除交换器
         */
        private boolean autoDeleteExchange = false;

        /**
         * 是否持久化队列
         */
        private boolean durableQueue = true;

        /**
         * 是否持开启重试队列
         */
        private boolean retryQueue = true;

        /**
         * 消息确认模式
         */
        private String acknowledgmentMode = "manual";

        /**
         * 预取数量
         */
        private int prefetchCount = 10;

        /**
         * 消息TTL (毫秒)
         */
        private Long messageTtl;

        /**
         * 死信交换器
         */
        private String deadLetterExchange;

        /**
         * 重试次数
         */
        private int retryAttempts = 3;

        /**
         * 重试延迟间隔 (毫秒)
         */
        private long retryInterval = 5000;

        /**
         * 自动清理冲突队列
         */
        private boolean autoCleanupConflicts = true;

        // ===== 延迟队列配置 =====

        /**
         * 是否启用延迟队列功能
         */
        private boolean delayEnabled = true;

        /**
         * 延迟交换器名称
         */
        private String delayExchangeName = "bus.delay.exchange";

        /**
         * 延迟队列前缀
         */
        private String delayQueuePrefix = "bus.delay";

        /**
         * 最大延迟时间（毫秒），超过此时间的延迟消息将被拒绝
         */
        private long maxDelayMillis = 24 * 60 * 60 * 1000L; // 24小时

        /**
         * 延迟队列自动清理间隔（秒）
         * 定期清理不再使用的延迟队列，0表示不清理
         */
        private long delayQueueCleanupInterval = 3600; // 1小时

        /**
         * 延迟队列空闲时间阈值（秒）
         * 超过此时间没有消息的延迟队列将被清理
         */
        private long delayQueueIdleTimeout = 7200; // 2小时
    }
}
