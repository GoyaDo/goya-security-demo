package com.ysmjjsy.goya.security.bus.properties;

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
     * RabbitMQ传输配置
     */
    private RabbitMQ rabbitmq = new RabbitMQ();

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
        private String defaultExchangeName = "goya.events.topic";

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
         * 是否自动删除队列
         */
        private boolean autoDeleteQueue = false;

        /**
         * 是否排他队列
         */
        private boolean exclusiveQueue = false;

        /**
         * 队列前缀
         */
        private String queuePrefix = "goya.events.";

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
    }
}
