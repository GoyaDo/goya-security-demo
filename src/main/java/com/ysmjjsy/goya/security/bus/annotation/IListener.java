package com.ysmjjsy.goya.security.bus.annotation;

import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

/**
 * <p>统一事件监听器注解</p>
 *
 * @author goya
 * @since 2025/6/24 16:37
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface IListener {

    /**
     * 监听的事件类型
     * 如果不指定，将从方法参数中推断
     */
    Class<?>[] value() default {};

    /**
     * 别名，同 value
     */
    @AliasFor("value")
    Class<?>[] events() default {};

    /**
     * 监听的主题/队列名称
     * 用于远程事件订阅
     * 目前只是单topic
     */
    String topic() default "";

    /**
     * 条件表达式，支持 SpEL
     * 只有当条件为 true 时才处理事件
     */
    String condition() default "";
}
