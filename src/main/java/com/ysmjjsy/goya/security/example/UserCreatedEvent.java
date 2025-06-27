package com.ysmjjsy.goya.security.example;

import com.ysmjjsy.goya.security.bus.domain.AbstractBaseEvent;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * 用户创建事件示例
 *
 * @author goya
 * @since 2025/6/24
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class UserCreatedEvent extends AbstractBaseEvent {
    private String userId;
    private String username;
    private String email;
    private String phone;
    private String department;
} 