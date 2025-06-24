package com.ysmjjsy.goya.security.controller;

import com.ysmjjsy.goya.security.bus.bus.IEventBus;
import com.ysmjjsy.goya.security.bus.enums.BusRemoteType;
import com.ysmjjsy.goya.security.bus.enums.EventRoutingStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p></p>
 *
 * @author goya
 * @since 2025/6/24 14:55
 */
@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
@Slf4j
public class TestController {

    private final IEventBus iEventBus;

    @RequestMapping("test")
    public String test() {
        TestEvent testEvent = new TestEvent("test");
        testEvent.routingStrategy(EventRoutingStrategy.REMOTE_ONLY);
        testEvent.topic("test");
        testEvent.remoteType(BusRemoteType.RABBITMQ);
        iEventBus.publish(testEvent);
        return "test";
    }
}
