package com.ysmjjsy.goya.security.bus.context;

import com.google.common.collect.Maps;
import com.ysmjjsy.goya.security.bus.annotation.IListener;
import com.ysmjjsy.goya.security.bus.api.IEvent;
import com.ysmjjsy.goya.security.bus.api.IEventListener;
import com.ysmjjsy.goya.security.bus.configuration.properties.BusProperties;
import com.ysmjjsy.goya.security.bus.core.DefaultListenerManage;
import com.ysmjjsy.goya.security.bus.core.ListenerManage;
import com.ysmjjsy.goya.security.bus.core.LocalEventBus;
import com.ysmjjsy.goya.security.bus.enums.ConsumeResult;
import com.ysmjjsy.goya.security.bus.enums.TransportType;
import com.ysmjjsy.goya.security.bus.transport.MqParamsUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.StringUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * 事件监听器Bean后处理器
 * <p>
 * 扫描Spring容器中标注了@IListener注解的Bean和方法
 * 自动注册到本地事件总线和远程传输层
 *
 * @author goya
 * @since 2025/6/24
 */
@Slf4j
@RequiredArgsConstructor
public class EventListenerBeanPostProcessor implements BeanPostProcessor {

    private final BusProperties busProperties;
    private final LocalEventBus localEventBus;
    private final ApplicationContext applicationContext;

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> beanClass = bean.getClass();

        // 处理方法级别的@IListener注解
        Method[] methods = beanClass.getDeclaredMethods();
        for (Method method : methods) {
            IListener methodAnnotation = AnnotationUtils.findAnnotation(method, IListener.class);
            if (methodAnnotation != null) {
                processMethodLevelListener(bean, method, methodAnnotation, beanName);
            }
        }

        return bean;
    }

    /**
     * 处理方法级别的监听器
     */
    private void processMethodLevelListener(Object bean, Method method, IListener annotation, String beanName) {
        if (!annotation.enabled()) {
            log.debug("Method listener {}.{} is disabled, skipping registration", beanName, method.getName());
            return;
        }

        try {
            // 验证方法签名
            if (!isValidListenerMethod(method)) {
                log.warn("Invalid listener method signature: {}.{}", beanName, method.getName());
                return;
            }

            // 解析事件类型
            String eventKey = resolveEventTypeFromMethod(method, annotation);
            if (!StringUtils.hasText(eventKey)) {
                log.warn("Could not resolve event type for method listener: {}.{}", beanName, method.getName());
                return;
            }

            // 创建方法监听器包装器
            MethodListenerWrapper wrapper = new MethodListenerWrapper(bean, method);

            // 注册到本地事件总线
            localEventBus.registerListener(eventKey, wrapper);

            // 注册到远程传输层（如果需要）
            registerToRemoteTransport(annotation, eventKey, wrapper, getEventClass(method));

            log.info("Registered method-level listener: {}.{} for event type: {}", beanName, method.getName(), eventKey);

        } catch (Exception e) {
            log.error("Failed to register method-level listener: {}.{}", beanName, method.getName(), e);
        }
    }

    /**
     * 从方法参数解析事件类型
     */
    private String resolveEventTypeFromMethod(Method method, IListener annotation) {
        // 优先使用注解中指定的事件类型
        if (StringUtils.hasText(annotation.eventKey())) {
            return annotation.eventKey();
        }

        // 从方法参数推断
        Class<?>[] paramTypes = method.getParameterTypes();
        if (paramTypes.length > 0 && IEvent.class.isAssignableFrom(paramTypes[0])) {
            return extractEventTypeFromClass(paramTypes[0]);
        }

        return null;
    }

    private Class<? extends IEvent> getEventClass(Method method) {
        // 从方法参数推断
        Class<?>[] paramTypes = method.getParameterTypes();
        if (paramTypes.length > 0 && IEvent.class.isAssignableFrom(paramTypes[0])) {
            return (Class<? extends IEvent>) paramTypes[0];
        }
        return null;
    }

    /**
     * 从事件类提取事件类型
     */
    private String extractEventTypeFromClass(Class<?> eventClass) {
        // 如果是具体的事件类，使用类名
        if (!eventClass.equals(IEvent.class)) {
            return eventClass.getSimpleName();
        }
        return null;
    }

    /**
     * 验证监听器方法签名
     */
    private boolean isValidListenerMethod(Method method) {
        Class<?>[] paramTypes = method.getParameterTypes();
        Class<?> returnType = method.getReturnType();

        // 检查参数：应该有一个参数，且继承自IEvent
        if (paramTypes.length != 1) {
            return false;
        }
        if (!IEvent.class.isAssignableFrom(paramTypes[0])) {
            return false;
        }

        // 检查返回值：应该是ConsumeResult或void
        return returnType.equals(Void.TYPE) ||
                returnType.equals(ConsumeResult.class);
    }

    /**
     * 注册到远程传输层
     */
    private void registerToRemoteTransport(IListener annotation, String eventKey,
                                           IEventListener<? extends IEvent> listener, Class<? extends IEvent> event) {
        try {
            Annotation annotationConfig;

            TransportType transportType = annotation.transportType();

            if (annotation.transportType().equals(TransportType.LOCAL)) {
                transportType = busProperties.getDefaultTransport();
            }

            switch (transportType) {
                case KAFKA:
                    annotationConfig = annotation.kafkaConfig();
                    break;
                case RABBITMQ:
                    annotationConfig = annotation.rabbitConfig();
                    break;
                default:
                    annotationConfig = null;
                    break;
            }

            Map<String, Object> properties;
            if (annotationConfig == null) {
                properties = Maps.newHashMap();
            } else {
                properties = MqParamsUtils.buildSubscriptionProperties(annotationConfig);
            }

            ListenerManage listenerManage = applicationContext.getBean(DefaultListenerManage.class);

            for (Map.Entry<String, ListenerManage> entry : applicationContext.getBeansOfType(ListenerManage.class).entrySet()) {
                String key = entry.getKey();
                ListenerManage value = entry.getValue();
                if (org.apache.commons.lang3.StringUtils.containsIgnoreCase(key, transportType.name())) {
                    listenerManage = value;
                }
            }

            // 构建订阅配置
            listenerManage.subscribeByConfig(
                    annotation.eventModel(),
                    annotation.transportType(),
                    properties,
                    eventKey,
                    listener,
                    event
            );
        } catch (Exception e) {
            log.error("Failed to register remote transport subscription for event type: {}", eventKey, e);
        }
    }

    /**
     * 方法监听器包装器
     */
    private static class MethodListenerWrapper implements IEventListener<IEvent> {
        private final Object bean;
        private final Method method;

        public MethodListenerWrapper(Object bean, Method method) {
            this.bean = bean;
            this.method = method;
            this.method.setAccessible(true);
        }

        @Override
        public ConsumeResult onEvent(IEvent event) {
            try {
                Object result = method.invoke(bean, event);
                if (result instanceof ConsumeResult) {
                    return (ConsumeResult) result;
                } else {
                    return ConsumeResult.SUCCESS;
                }
            } catch (Exception e) {
                log.error("Method listener execution failed", e);
                return ConsumeResult.RETRY;
            }
        }
    }
} 