package com.ysmjjsy.goya.security.bus.spi;

import com.ysmjjsy.goya.security.bus.enums.ConsumeResult;

/**
 * 消息消费者接口
 * 
 * 传输层通过此接口回调消费者处理消息
 *
 * @author goya
 * @since 2025/6/24
 */
public interface MessageConsumer {

    /**
     * 消费单条消息
     *
     * @param message 传输消息
     * @return 消费结果
     */
    ConsumeResult consume(TransportEvent message);

    /**
     * 批量消费消息
     *
     * @param messages 消息列表
     * @return 消费结果
     */
    default ConsumeResult consumeBatch(java.util.List<TransportEvent> messages) {
        for (TransportEvent message : messages) {
            ConsumeResult result = consume(message);
            if (result != ConsumeResult.SUCCESS) {
                return result;
            }
        }
        return ConsumeResult.SUCCESS;
    }
} 