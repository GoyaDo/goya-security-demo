package com.ysmjjsy.goya.security.bus.spi;

import com.ysmjjsy.goya.security.bus.api.IEvent;
import com.ysmjjsy.goya.security.bus.decision.DecisionResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 消息记录实体
 * 
 * 用于Outbox模式的消息持久化，记录消息的完整信息和状态
 *
 * @author goya
 * @since 2025/6/24
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventRecord {

    /**
     * 事件
     */
    private IEvent event;

    /**
     * 决策结果
     */
    private DecisionResult decision;
} 