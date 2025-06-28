package com.ysmjjsy.goya.security.bus.domain;

import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.ysmjjsy.goya.security.bus.api.IEvent;
import com.ysmjjsy.goya.security.bus.enums.EventStatus;
import lombok.Data;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * 基础事件实现类
 * <p>
 * 提供 IEvent 接口的默认实现，业务事件可以继承此类
 * 自动生成事件ID和创建时间，提供线程安全的元数据和属性管理
 *
 * @author goya
 * @since 2025/6/24
 */
@Data
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "@type")
@SuperBuilder
public abstract class AbstractBaseEvent implements IEvent {

    /**
     * 事件ID - 全局唯一标识符
     */
    protected String eventId;

    /**
     * 事件类型
     */
    protected String eventKey;

    /**
     * 事件创建时间
     */
    protected final LocalDateTime createTime = LocalDateTime.now();

    /**
     * 消息优先级
     */
    protected int priority = 0;

    /**
     * 事件状态
     */
    protected EventStatus eventStatus;

    /**
     * 默认构造函数
     */
    protected AbstractBaseEvent() {
        this.eventId = IdUtil.fastSimpleUUID();
        this.eventKey = this.getClass().getSimpleName();
    }

    /**
     * 默认构造函数
     */
    protected AbstractBaseEvent(String eventKey) {
        this.eventId = IdUtil.fastSimpleUUID();
        this.eventKey = eventKey;
    }
} 