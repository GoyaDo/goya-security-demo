package com.ysmjjsy.goya.security.bus.spi;

import com.ysmjjsy.goya.security.bus.enums.BusinessPriority;
import com.ysmjjsy.goya.security.bus.route.RoutingContext;
import lombok.Builder;
import lombok.Data;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 传输层消息封装
 * 
 * 用于在传输层之间传递消息数据
 * 包含消息的所有必要信息和传输配置
 *
 * @author goya
 * @since 2025/6/24
 */
@Data
@Builder
public class TransportMessage {

    /**
     * 消息ID
     */
    private String messageId;

    /**
     * 消息体（已序列化）
     */
    private byte[] body;


    /**
     * 路由上下文
     */
    private RoutingContext routingContext;

    /**
     * 原始消息体大小（用于压缩率统计）
     */
    private Integer originalBodySize;

    /**
     * 消息头信息
     */
    private Map<String, Object> headers;

    /**
     * 消息属性（用于过滤等）
     */
    private Map<String, String> properties;

    /**
     * 自定义属性（业务扩展用）
     */
    private Map<String, Object> customProperties;

    /**
     * 消息类型
     */
    private String messageType;

    /**
     * 顺序消息键
     */
    private String sequenceKey;

    /**
     * 延迟时间
     */
    private Duration delayTime;

    /**
     * 延迟投递时间
     */
    private LocalDateTime deliverTime;

    /**
     * 消息TTL
     */
    private Long ttl;

    /**
     * 消息优先级（0-255，数值越高优先级越高）
     */
    private Integer priority;

    /**
     * 业务优先级
     */
    private BusinessPriority businessPriority;

    /**
     * 是否启用压缩
     */
    @Builder.Default
    private Boolean enableCompression = false;

    /**
     * 压缩算法类型
     */
    private String compressionType;

    /**
     * 是否启用加密
     */
    @Builder.Default
    private Boolean enableEncryption = false;

    /**
     * 加密算法类型
     */
    private String encryptionType;

    /**
     * 是否持久化
     */
    @Builder.Default
    private Boolean persistent = true;

    /**
     * 是否对性能敏感
     */
    @Builder.Default
    private Boolean performanceSensitive = false;

    /**
     * 重试次数
     */
    @Builder.Default
    private Integer retryTimes = 0;

    /**
     * 最大重试次数
     */
    @Builder.Default
    private Integer maxRetryTimes = 3;

    /**
     * 创建时间
     */
    @Builder.Default
    private LocalDateTime createTime = LocalDateTime.now();

    /**
     * 消息标签（用于路由和过滤）
     */
    private String[] tags;

    /**
     * 消息分区键（用于分区消息）
     */
    private String partitionKey;

    /**
     * 事务组ID（用于事务消息）
     */
    private String transactionId;

    /**
     * 消息来源标识
     */
    private String source;

    /**
     * 消息追踪ID（用于链路追踪）
     */
    private String traceId;

    /**
     * 消息批次ID（用于批量消息）
     */
    private String batchId;

    /**
     * 批次中的索引位置
     */
    private Integer batchIndex;

    /**
     * 批次总大小
     */
    private Integer batchSize;
} 