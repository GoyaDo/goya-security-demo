package com.ysmjjsy.goya.security.controller;

import com.ysmjjsy.goya.security.bus.domain.IEvent;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * <p>测试事件</p>
 *
 * @author goya
 * @since 2025/6/24 23:22
 */
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class TestEvent extends IEvent {

    private final String message;

    public TestEvent(String message) {
        super();
        this.message = message;
    }
}
