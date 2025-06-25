package com.ysmjjsy.goya.security.controller;

import com.ysmjjsy.goya.security.bus.transport.rabbitmq.RabbitMqEvent;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * <p>测试事件</p>
 *
 * @author goya
 * @since 2025/6/24 23:22
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TestEvent extends RabbitMqEvent<TestEvent> {

    private String message;
}
