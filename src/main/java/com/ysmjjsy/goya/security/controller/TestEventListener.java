package com.ysmjjsy.goya.security.controller;

import com.ysmjjsy.goya.security.bus.annotation.IListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * <p>测试事件监听器</p>
 * <p>演示如何使用增强的@IListener注解和RabbitMQ配置</p>
 *
 * @author goya
 * @since 2025/6/24 23:23
 */
@Slf4j
@Component
public class TestEventListener {

    /**
     * 基础的事件监听器，使用简单的队列配置
     */
    @IListener(topic = "bus.test")
    public void onTestEvent(TestEvent event) {
        log.info("接收到基础测试事件: {}", event);
    }

    /**
     * 基础的事件监听器，使用简单的队列配置
     */
    @IListener(topic = "bus.test1")
    public void onTestEvent1(TestEvent event) {
        log.info("接收到基础测试事件: {}", event);
    }

    /**
     * 基础的事件监听器，使用简单的队列配置
     */
    @IListener(topic = "bus.test3")
    public void onTestEvent3(TestEvent event) {
        log.info("接收到基础测试事件: {}", event);
    }
}
