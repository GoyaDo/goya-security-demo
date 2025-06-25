package com.ysmjjsy.goya.security.bus.transport;


import cn.hutool.extra.spring.SpringUtil;
import com.ysmjjsy.goya.security.bus.context.PropertyResolver;
import com.ysmjjsy.goya.security.bus.domain.IEvent;
import com.ysmjjsy.goya.security.bus.enums.BusRemoteType;
import com.ysmjjsy.goya.security.bus.enums.EventRoutingStrategy;
import com.ysmjjsy.goya.security.bus.listener.IEventListener;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationContext;

import java.util.concurrent.CompletableFuture;

/**
 * 事件传输接口
 * 抽象不同消息中间件的传输机制
 *
 * @author goya
 * @since 2025/6/13 17:56
 */
public interface EventTransport {

    /**
     * 获取传输类型名称
     *
     * @return 传输类型
     */
    BusRemoteType getTransportType();

    /**
     * 发送事件到远程
     *
     * @param event 要发送的事件
     * @return 发送结果
     */
    CompletableFuture<TransportResult> send(IEvent<?> event);

    /**
     * 订阅远程事件
     *
     * @param topic     订阅的主题/队列
     * @param listener  事件监听器
     * @param eventType 事件类型
     */
    <E extends IEvent<E>> void subscribe(String topic, IEventListener<E> listener, Class<E> eventType);

    /**
     * 取消订阅
     *
     * @param topic    主题/队列
     * @param listener 事件监听器
     */
    void unsubscribe(String topic, IEventListener<?> listener);

    /**
     * 启动传输组件
     */
    void start();

    /**
     * 停止传输组件
     */
    void stop();

    /**
     * 是否是本地事件
     *
     * @param event 事件
     * @return 是否是本地事件
     */
    default boolean check(IEvent<?> event) {
        ApplicationContext applicationContext = SpringUtil.getApplicationContext();
        String applicationName = PropertyResolver.getApplicationName(applicationContext.getEnvironment());
        if (StringUtils.isEmpty(applicationName)) {
            return false;
        }
        EventRoutingStrategy routingStrategy = event.getRoutingStrategy();

        // 如果是本地事件,并且服务名一致则表示为自调用,不需要调用
        if (EventRoutingStrategy.LOCAL_ONLY.equals(routingStrategy)
                && StringUtils.equals(applicationName, event.getOriginalService())) {
            return false;
        }

        return applicationName.equals(event.getOriginalService());
    }

    /**
     * 是否支持事务
     *
     * @return 是否支持事务
     */
    default boolean supportsTransaction() {
        return false;
    }

    /**
     * 是否支持消息顺序保证
     *
     * @return 是否支持顺序
     */
    default boolean supportsOrdering() {
        return false;
    }
}