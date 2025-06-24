package com.ysmjjsy.goya.security.controller;

import com.ysmjjsy.goya.security.bus.annotation.IListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * <p></p>
 *
 * @author goya
 * @since 2025/6/24 23:23
 */
@Slf4j
@Component
public class TestEventListener {

    @IListener(topic = "test")
    public void onEvent(TestEvent event) {
        log.error("接收到的event:{}", event);
    }
}
