package com.ysmjjsy.goya.security.bus.annotation;

import com.ysmjjsy.goya.security.bus.enums.MessageModel;
import com.ysmjjsy.goya.security.bus.enums.TransportType;

import java.lang.annotation.*;

/**
 * 事件监听器注解
 * <p>
 * 用于标记类或方法为消息监听器，提供丰富的配置选项
 * 可以应用在类级别（配合 IEventListener 接口）或方法级别
 *
 * @author goya
 * @since 2025/6/24
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface IListener {

    /**
     * 消息模型
     *
     * @return 消息模型
     */
    MessageModel messageModel() default MessageModel.QUEUE;

    /**
     * 监听的事件类型列表
     * 当使用方法级监听器时，用于指定监听的事件类型
     * 对于类级监听器，框架会自动从泛型参数推断
     *
     * @return 事件类型数组
     */
    String eventType() default "";

    /**
     * 传输层类型枚举
     *
     * @return 传输层类型枚举
     */
    TransportType transportType() default TransportType.LOCAL;

    /**
     * 消费并发线程数
     * 控制监听器实例的并发处理能力，直接影响消费吞吐量
     *
     * @return 并发线程数，默认为1
     */
    int concurrency() default 1;

    /**
     * 最大重试次数
     * 当消息处理失败时的最大重试次数
     *
     * @return 最大重试次数，默认为3次
     */
    int maxRetryTimes() default 3;

    /**
     * 是否顺序消费
     * true表示需要按顺序消费消息（适用于有序消息场景）
     *
     * @return 是否顺序消费，默认为false
     */
    boolean orderly() default false;

    /**
     * 客户端过滤条件
     * Spring Expression Language (SpEL) 表达式
     * 只有当表达式评估为true时，消息才会被 onEvent 方法处理
     * 事件对象可在表达式中通过 #event 引用
     * <p>
     * 示例: "#event.payload.amount > 1000"
     *
     * @return SpEL 过滤条件
     */
    String condition() default "";

    /**
     * 服务端消息选择器
     * 用于在 MQ 服务端进行消息属性过滤
     * 具体语法取决于底层 MQ 的支持（如 RabbitMQ 的 header, Kafka 的 key）
     * <p>
     * 示例: "region = 'EAST' AND type = 'VIP'"
     *
     * @return 消息选择器表达式
     */
    String selector() default "";

    /**
     * 批量消费大小
     * 每次从 MQ 拉取并提交给处理方法的消息数量
     * 仅对支持批量消费的 MQ 和传输层有效
     *
     * @return 批量大小，默认为1（单条消费）
     */
    int batchSize() default 1;

    /**
     * 消费超时时间（毫秒）
     * 单条消息的处理超时时间
     *
     * @return 超时时间，默认15秒
     */
    long consumeTimeout() default 15000L;

    /**
     * 是否启用
     * 可以通过此属性动态控制监听器的启用状态
     *
     * @return 是否启用，默认为true
     */
    boolean enabled() default true;

    /**
     * 描述信息
     * 用于文档化和监控
     *
     * @return 描述信息
     */
    String description() default "";
} 