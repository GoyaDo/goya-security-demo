package com.ysmjjsy.goya.security.bus.transport.rabbitmq;

import com.ysmjjsy.goya.security.bus.domain.IEvent;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * <p></p>
 *
 * @author goya
 * @since 2025/6/25 11:34
 */
@SuperBuilder
@ToString
@EqualsAndHashCode(callSuper = false)
@Getter
@NoArgsConstructor
@AllArgsConstructor
public abstract class RabbitMqEvent<E extends RabbitMqEvent<E>> extends IEvent<E> {

    private static final long serialVersionUID = 1L;

    private String exchange;

    private String routingKey;

    private String queueName;
}
