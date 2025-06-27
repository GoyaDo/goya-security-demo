package com.ysmjjsy.goya.security.bus.enums;

/**
 * <p>路由策略枚举</p>
 *
 * @author goya
 * @since 2025/6/26 22:02
 */
public enum RoutingStrategy {

    /**
     * 仅本地路由
     */
    LOCAL_ONLY,
    /**
     * 仅远程路由
     */
    REMOTE_ONLY,
    /**
     * 混合路由 - 优先本地，必要时远程
     */
    HYBRID
}
