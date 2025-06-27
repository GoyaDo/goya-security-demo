package com.ysmjjsy.goya.security.bus.enums;

/**
 * <p>路由范围枚举</p>
 *
 * @author goya
 * @since 2025/6/26 22:00
 */
public enum RouteScope {

    /**
     * 仅本地 - 只在本JVM内路由，不通过MQ
     */
    LOCAL_ONLY,
    /**
     * 仅远程 - 只通过MQ路由，不在本地处理
     */
    REMOTE_ONLY,
    /**
     * 自动选择 - 框架智能决策本地或远程路由
     */
    AUTO
}
