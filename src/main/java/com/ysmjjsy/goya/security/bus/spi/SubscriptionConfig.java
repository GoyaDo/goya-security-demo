package com.ysmjjsy.goya.security.bus.spi;

import com.ysmjjsy.goya.security.bus.enums.EventModel;
import com.ysmjjsy.goya.security.bus.enums.TransportType;
import lombok.Builder;
import lombok.Data;

/**
 * 订阅配置
 *
 * @author goya
 * @since 2025/6/24
 */
@Data
@Builder
public class SubscriptionConfig {

    /**
     * 消息模型
     */
    private EventModel messageModel;

    /**
     * 监听的事件类型列表
     * 当使用方法级监听器时，用于指定监听的事件类型
     * 对于类级监听器，框架会自动从泛型参数推断
     */
    private String eventKey;

    /**
     * 传输层类型枚举
     */
    private TransportType transportType;

    /**
     * 消息过期时间（秒）
     */
    @Builder.Default
    private long ttl = 0L;

    /**
     * 并发线程数
     */
    @Builder.Default
    private Integer concurrency = 1;

    /**
     * 批量大小
     */
    @Builder.Default
    private Integer batchSize = 1;

    /**
     * 是否顺序消费
     */
    @Builder.Default
    private Boolean orderly = false;

    /**
     * 消息选择器
     */
    private String selector;

    /**
     * 客户端过滤条件
     * Spring Expression Language (SpEL) 表达式
     * 只有当表达式评估为true时，消息才会被 onEvent 方法处理
     * 事件对象可在表达式中通过 #event 引用
     * <p>
     * 示例: "#event.payload.amount > 1000"
     */
    private String condition;

    /**
     * 消费超时时间（毫秒）
     */
    @Builder.Default
    private Long consumeTimeout = 15000L;

    /**
     * 最大重试次数
     */
    @Builder.Default
    private Integer maxRetryTimes = 3;

    /**
     * 是否启用
     */
    @Builder.Default
    private Boolean enabled = true;

    /**
     * 描述信息
     */
    private String description;
} 