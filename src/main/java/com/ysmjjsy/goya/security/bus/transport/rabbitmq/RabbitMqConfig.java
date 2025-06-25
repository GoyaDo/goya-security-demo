package com.ysmjjsy.goya.security.bus.transport.rabbitmq;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p></p>
 *
 * @author goya
 * @since 2025/6/25 11:36
 */
@Target({})
@Retention(RetentionPolicy.RUNTIME)
public @interface RabbitMqConfig {

    /**
     * 队列名称
     * 如果为空，将使用默认命名规则
     */
    String queueName() default "";

    /**
     * 路由键
     * 如果为空，将使用 topic 作为路由键
     */
    String routingKey() default "";
}
