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
 * <p>演示重构后的RabbitMQ功能，展示简化配置和核心功能的使用</p>
 * <p>基于Spring Boot RabbitMQ的核心功能进行测试</p>
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
        return "Hello Goya Security with Refactored RabbitMQ Support! 🚀";
    }

    /**
     * 发布基础测试事件
     */
    @GetMapping("/event")
    public String publishEvent() {
        TestEvent event = new TestEvent("test")
                .topic("bus.test")
                .routingStrategy(EventRoutingStrategy.REMOTE_ONLY);
        event.setDelayMillis(1000);

//        event.setRoutingKey("test-simple-queue");

        eventBus.publish(event);
        return "Basic Event Published Successfully!";
    }

}
