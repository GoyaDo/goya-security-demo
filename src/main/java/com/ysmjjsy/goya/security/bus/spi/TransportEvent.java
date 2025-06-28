package com.ysmjjsy.goya.security.bus.spi;

import com.ysmjjsy.goya.security.bus.enums.*;
import com.ysmjjsy.goya.security.bus.route.RoutingContext;
import lombok.Builder;
import lombok.Data;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 传输层消息封装
 * <p>
 * 用于在传输层之间传递消息数据
 * 包含消息的所有必要信息和传输配置
 *
 * @author goya
 * @since 2025/6/24
 */
@Data
@Builder
public class TransportEvent {

    /**
     * 消息ID
     */
    private String eventId;

    /**
     * 消息原生key
     */
    private String originEventKey;

    /**
     * 消息类型
     */
    private String eventClass;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 消息状态
     */
    private EventStatus eventStatus;

    /**
     * 消息优先级
     */
    private int priority;

    /**
     * 消息体（已序列化）
     */
    private byte[] body;

    /**
     * 路由上下文
     */
    private RoutingContext routingContext;


    /**
     * 消息模型
     */
    private EventModel eventModel;

    /**
     * 消息类型
     */
    private EventType eventType;

    /**
     * 可靠性级别
     */
    private ReliabilityLevel reliabilityLevel;

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
    private Duration ttl;

    /**
     * 是否本地记录
     */
    private boolean localRecord;

    /**
     * 是否持久化
     */
    private boolean persistent;

    /**
     * 重试次数
     */
    private Integer retryTimes;

    /**
     * 是否启用压缩
     */
    private Boolean enableCompression;

    /**
     * 是否启用加密
     */
    private Boolean enableEncryption;

    /**
     * 是否启用幂等
     */
    private Boolean idempotence;

    /**
     * 事务ID
     */
    private String transactionalId;

    /**
     * 自定义属性
     * 用于传递额外信息
     */
    private Map<String, Object> properties;
} 