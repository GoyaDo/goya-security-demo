package com.ysmjjsy.goya.security.bus.domain;

import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.ysmjjsy.goya.security.bus.api.IEvent;
import lombok.Data;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
    protected String eventType;

    /**
     * 事件创建时间
     */
    protected final LocalDateTime createTime = LocalDateTime.now();

    /**
     * 框架元数据 - 线程安全
     */
    protected final Map<String, Object> metadata = new ConcurrentHashMap<>();

    /**
     * 业务属性 - 线程安全
     */
    protected final Map<String, String> properties = new ConcurrentHashMap<>();

    /**
     * 消息优先级
     */
    protected int priority = 0;

    /**
     * 默认构造函数
     */
    protected AbstractBaseEvent() {
        this.eventId = IdUtil.fastSimpleUUID();
        this.eventType = this.getClass().getSimpleName();
    }

    /**
     * 默认构造函数
     */
    protected AbstractBaseEvent(String eventType) {
        this.eventId = IdUtil.fastSimpleUUID();
        this.eventType = eventType;
    }

    @Override
    public void setProperty(String key, String value) {
        if (key != null && value != null) {
            properties.put(key, value);
        }
    }

    @Override
    public void setMetadata(String key, Object value) {
        if (key != null && value != null) {
            metadata.put(key, value);
        }
    }

    /**
     * 获取业务属性值
     *
     * @param key 属性键
     * @return 属性值，如果不存在则返回null
     */
    public String getProperty(String key) {
        return properties.get(key);
    }

    /**
     * 获取框架元数据值
     *
     * @param key 元数据键
     * @return 元数据值，如果不存在则返回null
     */
    public Object getMetadataValue(String key) {
        return metadata.get(key);
    }

    /**
     * 批量设置业务属性
     *
     * @param props 属性映射
     */
    public void setProperties(Map<String, String> props) {
        if (props != null) {
            properties.putAll(props);
        }
    }

    /**
     * 批量设置框架元数据
     *
     * @param meta 元数据映射
     */
    public void setMetadataMap(Map<String, Object> meta) {
        if (meta != null) {
            metadata.putAll(meta);
        }
    }
} 