package com.ysmjjsy.goya.security.bus.enums;


/**
 * 事件路由策略枚举
 * 定义事件的处理方式：本地、远程或混合
 *
 * @author goya
 * @since 2025/6/13 17:56
 */
public enum EventRoutingStrategy {

    /**
     * 仅本地处理，不进行远程广播
     */
    LOCAL_ONLY,

    /**
     * 仅远程广播，不进行本地处理
     */
    REMOTE_ONLY,

    /**
     * 先本地处理，再远程广播
     */
    LOCAL_AND_REMOTE;


    public static EventRoutingStrategy defaultRoute() {
        return  LOCAL_ONLY;
    }

    public boolean isLocal() {
        return this == LOCAL_ONLY || this == LOCAL_AND_REMOTE;
    }

    public  boolean isRemote() {
        return this == REMOTE_ONLY || this == LOCAL_AND_REMOTE;
    }


}