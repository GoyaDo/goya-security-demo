package com.ysmjjsy.goya.security.bus.annotation;

/**
 * <p>Redis Config</p>
 *
 * @author goya
 * @since 2025/6/29 21:43
 */
public @interface RedisConfig {

    /**
     * 是否启用
     *
     * @return 是否启用
     */
    boolean enabled() default true;
}
