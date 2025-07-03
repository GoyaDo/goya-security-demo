package com.ysmjjsy.goya.security.example;

import com.ysmjjsy.goya.security.bus.api.IEventBus;
import com.ysmjjsy.goya.security.bus.api.PublishResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 事件测试控制器
 *
 * @author goya
 * @since 2025/6/24
 */
@Slf4j
@RestController
@RequestMapping("/api/test/events")
@RequiredArgsConstructor
public class EventTestController {

    private final IEventBus eventBus;
    private final RabbitTemplate rabbitTemplate;

    /**
     * 测试发布用户创建事件
     */
    @GetMapping("/user/create")
    public Map<String, Object> testUserCreateEvent() {
        try {
            // 创建用户数据
            UserCreatedEvent userData = new UserCreatedEvent();
            AdinCreatedEvent adminData = new AdinCreatedEvent();
//            userData.setEventKey("user.created.default");
            // 创建事件
            log.info("Publishing user created event: {}", userData.getEventId());
            
            // 发布事件
//            PublishResult result = eventBus.publishDelayed(userData, Duration.ofSeconds(3));
//            PublishResult result = eventBus.publish(userData);
            PublishResult result2 = eventBus.publish(adminData);

//            PublishResult result = PublishResult.success("123", TransportType.LOCAL);
//            for (int i = 0; i < 10; i++) {
//                result =  eventBus.publish(userData);
//            }

            Map<String, Object> response = new HashMap<>();

            return response;

        } catch (Exception e) {
            log.error("Failed to publish user created event", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to publish event: " + e.getMessage());
            return response;
        }
    }

    /**
     * 测试发布用户创建事件
     */
    @GetMapping("/user2/create")
    public String test2UserCreateEvent() {
        try {
            Map<String, Object> msg = Map.of("content", "123", "timestamp", System.currentTimeMillis());
            for (int i = 0; i < 10; i++) {
                rabbitTemplate.convertAndSend(TestContig.EXCHANGE_NAME, TestContig.ROUTING_KEY, "123");
            }
            return "1";

        } catch (Exception e) {
            log.error("Failed to publish user created event", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to publish event: " + e.getMessage());
            return response.toString();
        }
    }
} 
