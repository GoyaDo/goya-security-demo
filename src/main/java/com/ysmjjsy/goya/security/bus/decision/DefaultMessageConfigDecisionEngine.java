package com.ysmjjsy.goya.security.bus.decision;

import com.ysmjjsy.goya.security.bus.api.IEvent;
import com.ysmjjsy.goya.security.bus.configuration.properties.BusProperties;
import com.ysmjjsy.goya.security.bus.core.MessageConfigHint;
import com.ysmjjsy.goya.security.bus.route.RoutingStrategyManager;
import com.ysmjjsy.goya.security.bus.enums.*;
import com.ysmjjsy.goya.security.bus.transport.MessageTransport;
import com.ysmjjsy.goya.security.bus.route.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 消息配置智能决策引擎
 * <p>
 * 根据消息特性、业务提示和系统状态，动态选择最优的消息配置
 * 包括消息模型、传输方式、可靠性级别、路由策略等
 *
 * @author goya
 * @since 2025/6/24
 */
@Slf4j
@RequiredArgsConstructor
public class DefaultMessageConfigDecisionEngine implements MessageConfigDecision {

    private final BusProperties properties;
    private final Map<TransportType, MessageTransport> transportRegistry = new ConcurrentHashMap<>();
    private RoutingStrategyManager routingStrategyManager;

    /**
     * 设置路由策略管理器（延迟注入以避免循环依赖）
     */
    @Autowired(required = false)
    public void setRoutingStrategyManager(RoutingStrategyManager routingStrategyManager) {
        this.routingStrategyManager = routingStrategyManager;
        log.info("Routing strategy manager injected into decision engine");
    }

    /**
     * 注册传输层
     *
     * @param transports 传输层列表
     */
    @Autowired(required = false)
    public void registerTransports(List<MessageTransport> transports) {
        if (transports != null) {
            for (MessageTransport transport : transports) {
                transportRegistry.put(transport.getTransportType(), transport);
                log.info("Registered MessageTransport: {} with capabilities: {}",
                        transport.getTransportType(), transport.getSupportedCapabilities());
            }
        }
    }

    /**
     * 决策消息配置
     *
     * @param event 事件
     * @param hint  配置提示
     * @return 决策结果
     */
    @Override
    public DecisionResult decide(IEvent event, MessageConfigHint hint) {
        log.debug("Starting decision process for event: {}, hint: {}", event.getEventKey(), hint);

        DecisionResult.DecisionResultBuilder builder = DecisionResult.builder();

        // 1. 决策消息模型
        EventModel messageModel = decideMessageModel(event, hint);
        builder.eventModel(messageModel);
        log.debug("Decided messageModel: {}", messageModel);

        // 2. 决策消息类型
        EventType messageType = decideMessageType(event, hint);
        builder.eventType(messageType);
        log.debug("Decided messageType: {}", messageType);

        // 3. 决策可靠性级别
        ReliabilityLevel reliabilityLevel = decideReliabilityLevel(event, hint);
        builder.reliabilityLevel(reliabilityLevel);
        log.debug("Decided reliabilityLevel: {}", reliabilityLevel);

        // 5. 决策传输层类型
        TransportType transportType = decideTransportType(hint, messageType);
        builder.transportType(transportType);
        log.debug("Decided transportType: {}", transportType);

        // 6 构建路由上下文
        RoutingContext routingContext = routingStrategyManager.buildSendingContext(event, messageModel, transportType);
        // 更新destination为路由上下文提供的值
        builder.routingContext(routingContext);
        log.debug("Updated routing context: {}", routingContext);

        // 8. 决策是否启用压缩
        boolean enableCompression = decideCompression(event, hint);
        builder.enableCompression(enableCompression);
        log.debug("Decided enableCompression: {}", enableCompression);

        // 9. 决策是否启用加密
        boolean enableEncryption = decideEncryption(event, hint);
        builder.enableEncryption(enableEncryption);
        log.debug("Decided enableEncryption: {}", enableEncryption);

        // 10. 设置其他相关配置
        if (Objects.nonNull(hint)) {
            builder.delayTime(hint.getDelayTime());
            builder.deliverTime(hint.getDeliverTime());
            builder.sequenceKey(hint.getSequenceKey());
            builder.ttl(hint.getTtl());
            builder.persistent(hint.isPersistent());
            builder.retryTimes(hint.getRetryTimes());
            builder.idempotence(hint.getIdempotence());
            builder.properties(hint.getProperties());
        }

        DecisionResult result = builder.build();
        log.info("Decision completed for event: {}, result: {}", event.getEventKey(), result);
        return result;
    }

    /**
     * 决策消息模型
     */
    private EventModel decideMessageModel(IEvent event, MessageConfigHint hint) {
        if (hint != null && hint.getMessageModel() != null) {
            return hint.getMessageModel();
        }

        // 根据事件类型推断：通知类事件通常使用TOPIC，命令类事件使用QUEUE
        String eventKey = event.getEventKey();
        if (eventKey != null
                && (eventKey.contains("notification")
                || eventKey.contains("broadcast")
                || eventKey.contains("topic")
        )) {
            return EventModel.TOPIC;
        }
        // 默认使用队列模式
        return EventModel.QUEUE;
    }

    /**
     * 决策消息类型
     */
    private EventType decideMessageType(IEvent event, MessageConfigHint hint) {
        if (hint != null && hint.getEventType() != null) {
            return hint.getEventType();
        }

        if (hint != null) {
            if (hint.getDelayTime() != null) {
                return EventType.DELAYED;
            }
            if (hint.getDeliverTime() != null) {
                return EventType.SCHEDULED;
            }
            if (StringUtils.hasText(hint.getSequenceKey())) {
                return EventType.ORDERED;
            }
        }

        return EventType.NORMAL;
    }

    /**
     * 决策可靠性级别
     */
    private ReliabilityLevel decideReliabilityLevel(IEvent event, MessageConfigHint hint) {
        if (hint != null && hint.getReliabilityLevel() != null) {
            return hint.getReliabilityLevel();
        }

        if (event.getPriority() >= 5) {
            return ReliabilityLevel.RELIABLE;
        }

        // 检查是否在事务中
        if (isInTransaction()) {
            return ReliabilityLevel.TRANSACTIONAL;
        }
        // 默认可靠投递
        return ReliabilityLevel.RELIABLE;
    }

    /**
     * 决策传输层类型
     */
    private TransportType decideTransportType(MessageConfigHint hint, EventType messageType) {
        if (hint != null && hint.getTransportType() != null) {
            TransportType preferred = hint.getTransportType();
            if (isTransportAvailable(preferred, messageType)) {
                return preferred;
            }
            log.warn("Preferred transport {} is not available or doesn't support {}, falling back to default",
                    preferred, messageType);
        }

        // 根据消息类型和能力选择最优传输层
        for (Map.Entry<TransportType, MessageTransport> entry : transportRegistry.entrySet()) {
            MessageTransport transport = entry.getValue();
            if (transport.isHealthy() && supportsMessageType(transport, messageType)) {
                return entry.getKey();
            }
        }

        // 使用默认传输层
        TransportType defaultTransport = properties.getDefaultTransport();
        if (isTransportAvailable(defaultTransport, messageType)) {
            return defaultTransport;
        }

        log.warn("Default transport {} is not available, using LOCAL as fallback", defaultTransport);
        return TransportType.LOCAL;
    }

    /**
     * 检查传输层是否可用且支持指定消息类型
     */
    private boolean isTransportAvailable(TransportType transportType, EventType messageType) {
        MessageTransport transport = transportRegistry.get(transportType);
        return transport != null && transport.isHealthy() && supportsMessageType(transport, messageType);
    }

    /**
     * 检查传输层是否支持指定消息类型
     */
    private boolean supportsMessageType(MessageTransport transport, EventType messageType) {
        switch (messageType) {
            case DELAYED:
            case SCHEDULED:
                return transport.getSupportedCapabilities().contains(EventCapability.DELAYED_MESSAGE);
            case ORDERED:
                return transport.getSupportedCapabilities().contains(EventCapability.ORDERED_MESSAGE);
            case NORMAL:
            default:
                // 所有传输层都支持普通消息
                return true;
        }
    }

    /**
     * 检查当前是否在事务中
     */
    private boolean isInTransaction() {
        try {
            // 使用Spring的事务同步管理器检查
            return TransactionSynchronizationManager.isActualTransactionActive();
        } catch (Exception e) {
            log.debug("Failed to check transaction status: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取已注册的传输层
     */
    @Override
    public Map<TransportType, MessageTransport> getRegisteredTransports() {
        return new ConcurrentHashMap<>(transportRegistry);
    }


    /**
     * 决策是否启用压缩
     */
    private boolean decideCompression(IEvent event, MessageConfigHint hint) {
        if (hint != null && hint.getEnableCompression() != null) {
            return hint.getEnableCompression();
        }

        // 默认启用压缩（对于大消息有效）
        return true;
    }

    /**
     * 决策是否启用加密
     */
    private boolean decideEncryption(IEvent event, MessageConfigHint hint) {
        if (hint != null && hint.getEnableEncryption() != null) {
            return hint.getEnableEncryption();
        }

        // 根据事件类型决定是否加密
        String eventKey = event.getEventKey();
        if (eventKey != null && (eventKey.contains("sensitive") ||
                eventKey.contains("private") ||
                eventKey.contains("security"))) {
            return true;
        }

        // 默认不启用加密（避免性能损失）
        return false;
    }
} 