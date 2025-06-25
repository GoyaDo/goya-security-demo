package com.ysmjjsy.goya.security.controller;

import com.ysmjjsy.goya.security.bus.bus.IEventBus;
import com.ysmjjsy.goya.security.bus.enums.EventRoutingStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>测试控制器</p>
 *
 * @author goya
 * @since 2025/6/24 16:44
 */
@Slf4j
@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class TestController {

    private final IEventBus eventBus;

    @GetMapping("/hello")
    public String hello() {
        return "Hello Goya Security!";
    }

    @GetMapping("/event")
    public String publishEvent() {
        TestEvent event = new TestEvent("test").topic("test").routingStrategy(EventRoutingStrategy.LOCAL_AND_REMOTE);

        // 发布事件
        eventBus.publish(event);
        return "Publish Success!";
    }
}
