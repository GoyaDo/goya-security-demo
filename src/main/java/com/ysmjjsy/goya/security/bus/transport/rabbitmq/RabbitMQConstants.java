package com.ysmjjsy.goya.security.bus.transport.rabbitmq;

/**
 * <p></p>
 *
 * @author goya
 * @since 2025/6/29 16:53
 */
public interface RabbitMQConstants {

    /**
     * 是否持久化(队列)
     */
    String QUEUE_DURABLE = "queueDurable";

    /**
     * 是否删除队列
     */
    String QUEUE_AUTO_DELETE = "queueAutoDelete";

    /**
     * 是否持久化(交换机)
     */
    String EXCHANGE_DURABLE = "exchangeDurable";

    /**
     * 若没有队列绑定时是否自动删除
     */
    String EXCHANGE_AUTO_DELETE = "exchangeAutoDelete";

    /**
     * 并发数
     */
    String CONCURRENT_CONSUMERS = "concurrentConsumers";

    /**
     * 消息过期时间
     */
    String X_MESSAGE_TTL = "x-message-ttl";

    String X_EXPIRES = "x-expires";

    String X_MAX_LENGTH = "x-max-length";

    String X_MAX_LENGTH_BYTES = "x-max-length-bytes";

    String X_OVERFLOW = "x-overflow";

    String X_DEAD_LETTER_EXCHANGE = "x-dead-letter-exchange";

    String X_DEAD_LETTER_ROUTING_KEY = "x-dead-letter-routing-key";

    String X_MAX_PRIORITY = "x-max-priority";

    String X_QUEUE_MODE = "x-queue-mode";

    String X_QUEUE_MASTER_LOCATION = "x-queue-master-location";

    String X_SINGLE_ACTIVE_CONSUMER = "x-single-active-consumer";

    String X_QUEUE_TYPE = "x-queue-type";

    String X_DELIVERY_LIMIT = "x-delivery-limit";

}
