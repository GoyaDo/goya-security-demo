package com.ysmjjsy.goya.security.example;

import com.ysmjjsy.goya.security.bus.api.IEventBus;
import com.ysmjjsy.goya.security.bus.api.PublishResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    /**
     * 测试发布用户创建事件
     */
    @GetMapping("/user/create")
    public Map<String, Object> testUserCreateEvent() {
        try {
            // 创建用户数据
            UserCreatedEvent userData = new UserCreatedEvent();

            // 创建事件
            log.info("Publishing user created event: {}", userData.getEventId());
            
            // 发布事件
//            PublishResult result = eventBus.publishDelayed(userData,Duration.ofSeconds(3));
            PublishResult result = eventBus.publish(userData);

            Map<String, Object> response = new HashMap<>();
            response.put("eventId", userData.getEventId());
            response.put("success", result.isSuccess());
            response.put("message", result.isSuccess() ? "Event published successfully" : result.getErrorMessage());
            response.put("transportType", result.getTransportType());
            response.put("userData", userData);

            return response;

        } catch (Exception e) {
            log.error("Failed to publish user created event", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to publish event: " + e.getMessage());
            return response;
        }
    }
} 
