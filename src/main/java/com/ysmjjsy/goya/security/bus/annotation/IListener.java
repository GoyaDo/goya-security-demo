package com.ysmjjsy.goya.security.bus.annotation;

import com.ysmjjsy.goya.security.bus.enums.EventModel;
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
     * 是否启用
     *
     * @return 是否启用
     */
    boolean enabled() default true;

    /**
     * 消息模型
     *
     * @return 消息模型
     */
    EventModel eventModel() default EventModel.QUEUE;

    /**
     * 监听的事件类型列表
     * 当使用方法级监听器时，用于指定监听的事件类型
     * 对于类级监听器，框架会自动从泛型参数推断
     *
     * @return 事件类型数组
     */
    String eventKey() default "";

    /**
     * 传输层类型枚举
     *
     * @return 传输层类型枚举
     */
    TransportType transportType() default TransportType.LOCAL;


    /**
     * RabbitMQ配置
     *
     * @return RabbitMQ配置
     */
    RabbitConfig rabbitConfig() default @RabbitConfig;

    /**
     * Kafka配置
     *
     * @return Kafka配置
     */
    KafkaConfig kafkaConfig() default @KafkaConfig;

    /**
     * Redis配置
     */
    RedisConfig redisConfig() default @RedisConfig;
} 