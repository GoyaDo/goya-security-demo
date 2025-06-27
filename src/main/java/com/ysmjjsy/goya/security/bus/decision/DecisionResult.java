package com.ysmjjsy.goya.security.bus.decision;

import com.ysmjjsy.goya.security.bus.enums.*;
import com.ysmjjsy.goya.security.bus.route.RoutingContext;
import lombok.Builder;
import lombok.Data;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * <p>决策结果</p>
 *
 * @author goya
 * @since 2025/6/26 22:08
 */
@Data
@Builder
public class DecisionResult {

    /**
     * 消息模型
     */
    private MessageModel messageModel;

    /**
     * 消息类型
     */
    private MessageType messageType;

    /**
     * 可靠性级别
     */
    private ReliabilityLevel reliabilityLevel;

    /**
     * 路由范围
     */
    private RouteScope routeScope;

    /**
     * 传输方式
     */
    private TransportType transportType;

    /**
     * 延迟时间
     */
    private Duration delayTime;

    /**
     * 发送时间
     */
    private LocalDateTime deliverTime;

    /**
     * 消息序列键
     */
    private String sequenceKey;

    /**
     * 消息生存时间
     */
    private Duration messageTtl;

    /**
     * 业务优先级
     */
    private BusinessPriority businessPriority;

    /**
     * 是否持久化
     */
    private boolean persistent;

    /**
     * 是否启用压缩
     */
    private boolean enableCompression;

    /**
     * 是否启用加密
     */
    private boolean enableEncryption;

    /**
     * 重试次数
     */
    private Integer retryTimes;

    /**
     * 自定义属性
     */
    private Map<String, Object> customProperties;

    /**
     * 性能敏感
     */
    private boolean performanceSensitive;
    
    /**
     * 路由上下文（包含详细的路由信息）
     */
    private RoutingContext routingContext;
}
