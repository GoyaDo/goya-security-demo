package com.ysmjjsy.goya.security.bus.core;

import com.ysmjjsy.goya.security.bus.api.IEvent;
import com.ysmjjsy.goya.security.bus.api.IEventListener;
import com.ysmjjsy.goya.security.bus.enums.EventModel;
import com.ysmjjsy.goya.security.bus.enums.TransportType;
import com.ysmjjsy.goya.security.bus.route.RoutingContext;
import com.ysmjjsy.goya.security.bus.spi.SubscriptionConfig;
import com.ysmjjsy.goya.security.bus.spi.TransportEvent;
import com.ysmjjsy.goya.security.bus.transport.MqParamsUtils;

import java.util.Map;

/**
 * <p></p>
 *
 * @author goya
 * @since 2025/7/3 15:16
 */
public interface ListenerManage {

    /**
     * 将对象放入缓存
     *
     * @param name   名称
     * @param object 对象
     */
    void putCache(String name, Object object);

    /**
     * 获取对象
     *
     * @param name  名称
     * @param clazz 对象
     * @return 对象
     */
    <T> T getCache(String name, Class<T> clazz);

    SubscriptionConfig getSubscriptionConfig(EventModel eventModel,
                                             TransportType transportType,
                                             Map<String, Object> properties,
                                             String eventKey,
                                             RoutingContext routingContext,
                                             Class<? extends IEvent> event);

    /**
     * 发送之前构建的一些前置信息
     * @param transportEvent
     */
   default void buildMqInfosAfter(TransportEvent transportEvent) throws ClassNotFoundException {
       SubscriptionConfig subscriptionConfig = getSubscriptionConfig(
               transportEvent.getEventModel(),
               transportEvent.getTransportType(),
               MqParamsUtils.buildSubscriptionProperties(transportEvent.getTransportType()),
               transportEvent.getOriginEventKey(),
               transportEvent.getRoutingContext(),
               (Class<? extends IEvent>) Class.forName(transportEvent.getEventClass())
       );
       createMqInfosAfter(subscriptionConfig);
   }

    /**
     * 创建mq信息
     * @param config
     */
    void createMqInfosAfter(SubscriptionConfig config);

    /**
     * 构建订阅配置
     *
     * @param eventModel    消息模型
     * @param transportType 传输层类型
     * @param properties    配置
     * @param eventKey      事件键
     * @param listener      监听器
     * @param event         事件
     * @return 订阅配置
     */
    SubscriptionConfig subscribeByConfig(
            EventModel eventModel,
            TransportType transportType,
            Map<String, Object> properties,
            String eventKey,
            IEventListener<? extends IEvent> listener,
            Class<? extends IEvent> event
    );

    /**
     * 订阅到传输层
     *
     * @param config    订阅配置
     * @param listener  监听器
     * @param eventKey  事件键
     * @param event     事件
     */
    void subscribeToTransport(SubscriptionConfig config,
                              IEventListener<? extends IEvent> listener,
                              String eventKey,
                              Class<? extends IEvent> event);
}
