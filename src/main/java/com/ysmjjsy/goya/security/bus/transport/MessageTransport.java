package com.ysmjjsy.goya.security.bus.transport;


import com.ysmjjsy.goya.security.bus.enums.EventCapability;
import com.ysmjjsy.goya.security.bus.enums.TransportType;
import com.ysmjjsy.goya.security.bus.spi.MessageConsumer;
import com.ysmjjsy.goya.security.bus.spi.SubscriptionConfig;
import com.ysmjjsy.goya.security.bus.spi.TransportEvent;
import com.ysmjjsy.goya.security.bus.spi.TransportResult;

import java.util.Set;

/**
 * 消息传输层 SPI 接口
 * <p>
 * 定义了统一的传输层抽象，支持不同 MQ 产品的接入
 * 实现类需要注册为 Spring Bean，框架会自动发现并管理
 *
 * @author goya
 * @since 2025/6/24
 */
public interface MessageTransport {

    /**
     * 发送消息
     *
     * @param transportEvent 传输消息
     * @return 传输结果
     */
    TransportResult send(TransportEvent transportEvent);

    /**
     * 订阅消息
     *
     * @param config   订阅配置
     * @param consumer 消息消费者
     */
    void subscribe(SubscriptionConfig config, MessageConsumer consumer);

    /**
     * 获取传输层类型
     *
     * @return 传输层类型枚举
     */
    TransportType getTransportType();

    /**
     * 获取支持的消息能力
     *
     * @return 支持的消息能力集合
     */
    Set<EventCapability> getSupportedCapabilities();

    /**
     * 检查传输层健康状态
     *
     * @return true表示健康，false表示不健康
     */
    boolean isHealthy();
}