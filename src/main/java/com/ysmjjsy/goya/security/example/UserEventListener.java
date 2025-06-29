package com.ysmjjsy.goya.security.example;

import com.ysmjjsy.goya.security.bus.annotation.IListener;
import com.ysmjjsy.goya.security.bus.enums.ConsumeResult;
import lombok.extern.slf4j.Slf4j;

/**
 * 用户事件监听器示例
 *
 * @author goya
 * @since 2025/6/24
 */
@Slf4j
//@Component
@IListener
public class UserEventListener {

    /**
     * 方法级监听器示例 - 处理用户创建事件
     */
    @IListener(
            eventKey = "user.created.default"
    )
    public ConsumeResult handleUserCreated(UserCreatedEvent event) {
        try {
            log.info("Processing user created event: {}", event);
            return ConsumeResult.RETRY;
//            return ConsumeResult.SUCCESS;

        } catch (Exception e) {
            log.error("Failed to process user created event: {}", event.getEventId(), e);
            return ConsumeResult.RETRY;
        }
    }
} 