package com.ysmjjsy.goya.security.controller;

import com.ysmjjsy.goya.security.bus.domain.IEvent;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * <p></p>
 *
 * @author goya
 * @since 2025/6/24 23:22
 */
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class TestEvent extends IEvent {

    private final String data;

    public TestEvent(String data) {
        super();
        this.data = data;
    }
}
