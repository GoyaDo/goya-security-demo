package com.ysmjjsy.goya.security.bus.context;

import com.ysmjjsy.goya.security.bus.annotation.IListener;
import com.ysmjjsy.goya.security.bus.bus.IEventBus;
import com.ysmjjsy.goya.security.bus.domain.IEvent;
import com.ysmjjsy.goya.security.bus.listener.IEventListener;
import com.ysmjjsy.goya.security.bus.processor.EventTypeResolver;
import com.ysmjjsy.goya.security.bus.processor.MethodIEventListenerWrapper;
import com.ysmjjsy.goya.security.bus.configuration.properties.BusProperties;
import com.ysmjjsy.goya.security.bus.registry.ListenerRegistryManager;
import com.ysmjjsy.goya.security.bus.transport.EventTransport;
import com.ysmjjsy.goya.security.bus.transport.rabbitmq.RabbitMqConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 事件监听器自动注册器
 * <p>
 * 功能：
 * 1. 扫描实现 IEventListener 接口的类，直接注册为监听器
 * 2. 扫描标注 @IListener 注解的方法，包装为 MethodIEventListenerWrapper 后注册
 * 3. 同时注册到本地事件总线和远程传输层
 * 4. 提供重复注册检查和性能优化
 *
 * @author goya
 * @since 2025/6/20 21:08
 */
@Slf4j
@RequiredArgsConstructor
public class EventListenerAutoRegistrar implements SmartInitializingSingleton, ApplicationContextAware {

    private final BusProperties busProperties;


    private ApplicationContext applicationContext;
    private final AtomicInteger registeredCount = new AtomicInteger(0);

    // 注册状态管理，避免重复注册
    private final Set<String> registeredInterfaceListeners = ConcurrentHashMap.newKeySet();
    private final Set<String> registeredMethodListeners = ConcurrentHashMap.newKeySet();

    // 缓存反射结果，提高性能
    private final Map<Class<?>, List<Method>> annotatedMethodsCache = new ConcurrentHashMap<>();

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterSingletonsInstantiated() {
        log.debug("=== 开始事件监听器自动注册 ===");

        try {
            // 获取必要的组件
            IEventBus eventBus = getEventBus();
            List<EventTransport> eventTransports = getEventTransports();

            if (eventBus == null) {
                log.warn("EventBus未找到，跳过事件监听器注册");
                return;
            }

            log.debug("发现 {} 个事件传输组件: {}", eventTransports.size(),
                    eventTransports.stream().map(EventTransport::getTransportType).collect(Collectors.toList()));

            // 注册监听器
            int interfaceListeners = registerInterfaceListeners(eventBus, eventTransports);
            int annotationListeners = registerAnnotationListeners(eventBus, eventTransports);

            int totalRegistered = interfaceListeners + annotationListeners;
            registeredCount.set(totalRegistered);

            log.debug("=== 事件监听器自动注册完成 ===");
            log.debug("接口监听器: {}, 注解监听器: {}, 总计: {}",
                    interfaceListeners, annotationListeners, totalRegistered);

            // 打印注册状态摘要
            ListenerRegistryManager registryManager = applicationContext.getBean(ListenerRegistryManager.class);
            registryManager.printRegistrySummary();

        } catch (Exception e) {
            log.error("事件监听器自动注册失败", e);
        }
    }

    /**
     * 获取事件总线实例
     */
    private IEventBus getEventBus() {
        try {
            return applicationContext.getBean(IEventBus.class);
        } catch (Exception e) {
            log.debug("EventBus不可用: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取所有事件传输组件
     */
    private List<EventTransport> getEventTransports() {
        try {
            Map<String, EventTransport> transportBeans = applicationContext.getBeansOfType(EventTransport.class);
            return new ArrayList<>(transportBeans.values());
        } catch (Exception e) {
            log.debug("EventTransports不可用: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 注册实现 IEventListener 接口的监听器
     * 直接注册原始监听器，不需要包装
     */
    private int registerInterfaceListeners(IEventBus eventBus, List<EventTransport> eventTransports) {
        Map<String, IEventListener> listeners = applicationContext.getBeansOfType(IEventListener.class);

        log.info("发现 {} 个 IEventListener 接口实现", listeners.size());

        int registered = 0;
        for (Map.Entry<String, IEventListener> entry : listeners.entrySet()) {
            String beanName = entry.getKey();
            IEventListener<?> listener = entry.getValue();

            // 检查是否已注册
            String listenerKey = generateInterfaceListenerKey(listener);
            if (registeredInterfaceListeners.contains(listenerKey)) {
                log.debug("跳过重复注册的接口监听器: {}", beanName);
                continue;
            }

            try {
                // 推断事件类型
                Class<? extends IEvent> eventType = EventTypeResolver.resolveEventTypeFromInterface(listener);
                String topic = listener.topic();

                // 包装为 MethodIEventListenerWrapper 以统一处理
                MethodIEventListenerWrapper wrapper = createInterfaceListenerWrapper(listener, eventType, topic);

                // 注册到本地事件总线
                registerToEventBus(eventBus, wrapper, eventType);

                // 注册到所有传输层
                registerToTransports(eventTransports, wrapper, eventType, topic);

                // 记录注册状态
                registeredInterfaceListeners.add(listenerKey);
                registered++;

                log.info("成功注册接口监听器: {} -> {} (topic: {})",
                        beanName, eventType.getSimpleName(), topic);

            } catch (Exception e) {
                log.error("注册接口监听器失败: {}", beanName, e);
            }
        }

        return registered;
    }

    /**
     * 注册标注 @IListener 注解的监听器方法
     */
    private int registerAnnotationListeners(IEventBus eventBus, List<EventTransport> eventTransports) {
        Map<String, Object> allBeans = applicationContext.getBeansOfType(Object.class);

        int registered = 0;
        for (Map.Entry<String, Object> entry : allBeans.entrySet()) {
            String beanName = entry.getKey();
            Object bean = entry.getValue();

            // 跳过Spring内部Bean和已知的框架Bean
            if (shouldSkipBean(beanName, bean)) {
                continue;
            }

            try {
                int methodListeners = registerAnnotationListenersFromBean(eventBus, eventTransports, bean);
                registered += methodListeners;

                if (methodListeners > 0) {
                    log.info("从Bean {} 注册了 {} 个注解监听器", beanName, methodListeners);
                }
            } catch (Exception e) {
                log.error("从Bean {} 注册注解监听器失败", beanName, e);
            }
        }

        return registered;
    }

    /**
     * 从指定Bean中注册注解监听器
     */
    private int registerAnnotationListenersFromBean(IEventBus eventBus,
                                                    List<EventTransport> eventTransports, Object bean) {
        Class<?> targetClass = EventTypeResolver.getUserClass(bean);
        List<Method> eventListenerMethods = getAnnotatedMethods(targetClass);

        if (eventListenerMethods.isEmpty()) {
            return 0;
        }

        int registered = 0;
        for (Method method : eventListenerMethods) {
            String methodKey = generateMethodListenerKey(bean, method);

            // 检查是否已注册
            if (registeredMethodListeners.contains(methodKey)) {
                log.debug("跳过重复注册的方法监听器: {}", methodKey);
                continue;
            }

            try {
                // 获取注解信息
                IListener annotation = AnnotatedElementUtils.findMergedAnnotation(method, IListener.class);
                if (annotation == null) {
                    log.warn("方法 {} 缺少 @IListener 注解", methodKey);
                    continue;
                }

                // 解析事件类型
                Class<? extends IEvent> eventType = EventTypeResolver.resolveEventTypeFromMethod(method);
                String topic = annotation.topic();
                if (StringUtils.isEmpty(topic)) {
                    topic = busProperties.getDefaultTopic();
                }

                // 创建方法适配器
                MethodIEventListenerWrapper adapter = createMethodListenerWrapper(bean, method, annotation, topic);

                // 注册到本地事件总线
                registerToEventBus(eventBus, adapter, eventType);

                // 注册到所有传输层
                registerToTransports(eventTransports, adapter, eventType, topic);

                // 记录注册状态
                registeredMethodListeners.add(methodKey);
                registered++;

                log.info("成功注册注解监听器: {}.{} -> {} (topic: {})",
                        targetClass.getSimpleName(), method.getName(), eventType.getSimpleName(), topic);

            } catch (Exception e) {
                log.error("注册注解监听器失败: {}.{}",
                        targetClass.getSimpleName(), method.getName(), e);
            }
        }

        return registered;
    }

    /**
     * 获取类中标注了@IListener注解的方法（使用缓存优化性能）
     */
    private List<Method> getAnnotatedMethods(Class<?> targetClass) {
        return annotatedMethodsCache.computeIfAbsent(targetClass, clazz -> {
            List<Method> methods = new ArrayList<>();
            ReflectionUtils.doWithMethods(clazz, method -> {
                IListener annotation = AnnotatedElementUtils.findMergedAnnotation(method, IListener.class);
                if (annotation != null) {
                    methods.add(method);
                }
            }, ReflectionUtils.USER_DECLARED_METHODS);
            return methods;
        });
    }

    /**
     * 创建接口监听器的包装器
     */
    private MethodIEventListenerWrapper createInterfaceListenerWrapper(IEventListener<?> listener,
                                                                       Class<? extends IEvent> eventType,
                                                                       String topic) {
        try {
            // 获取接口监听器的onEvent方法
            Method onEventMethod = findOnEventMethod(listener, eventType);

            // 创建虚拟注解
            IListener virtualAnnotation = createVirtualIListenerAnnotation(eventType, topic);

            return new MethodIEventListenerWrapper(listener, onEventMethod, virtualAnnotation, topic);
        } catch (Exception e) {
            throw new IllegalStateException("无法为接口监听器创建包装器: " + listener.getClass().getName(), e);
        }
    }

    /**
     * 查找监听器的onEvent方法
     */
    private Method findOnEventMethod(IEventListener<?> listener, Class<? extends IEvent> eventType)
            throws NoSuchMethodException {
        // 首先尝试直接查找匹配事件类型的方法
        try {
            return listener.getClass().getMethod("onEvent", eventType);
        } catch (NoSuchMethodException e) {
            // 如果找不到，尝试查找参数为IEvent的方法
            try {
                return listener.getClass().getMethod("onEvent", IEvent.class);
            } catch (NoSuchMethodException ex) {
                // 如果还是找不到，抛出原始异常
                throw e;
            }
        }
    }

    /**
     * 创建虚拟的@IListener注解实例
     */
    private IListener createVirtualIListenerAnnotation(Class<? extends IEvent> eventType, String topic) {
        return new IListener() {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return IListener.class;
            }

            @Override
            public Class<?>[] value() {
                return new Class[]{eventType};
            }

            @Override
            public Class<?>[] events() {
                return value();
            }

            @Override
            public String topic() {
                return topic != null ? topic : "";
            }

            @Override
            public String condition() {
                return "";
            }

            @Override
            public RabbitMqConfig rabbitmq() {
                return null;
            }
        };
    }

    /**
     * 创建方法监听器的包装器
     */
    private MethodIEventListenerWrapper createMethodListenerWrapper(Object bean, Method method,
                                                                    IListener annotation, String topic) {
        return new MethodIEventListenerWrapper(bean, method, annotation, topic);
    }

    /**
     * 注册监听器到事件总线
     */
    private void registerToEventBus(IEventBus eventBus, MethodIEventListenerWrapper wrapper,
                                    Class<? extends IEvent> eventType) {
        try {
            eventBus.subscribe(wrapper, eventType);

            // 注册到状态管理器
            ListenerRegistryManager registryManager = applicationContext.getBean(ListenerRegistryManager.class);
            String topic = wrapper.getTopic();
            registryManager.registerLocalEventType(eventType, topic != null ? topic : "");

            log.debug("注册到EventBus: {} -> {}", wrapper.getClass().getSimpleName(), eventType.getSimpleName());
        } catch (Exception e) {
            log.error("注册到EventBus失败: {} -> {}", wrapper.getClass().getSimpleName(), eventType.getSimpleName(), e);
            throw e;
        }
    }

    /**
     * 注册监听器到所有传输层
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void registerToTransports(List<EventTransport> eventTransports,
                                      MethodIEventListenerWrapper wrapper,
                                      Class<? extends IEvent> eventType,
                                      String topic) {
        if (eventTransports.isEmpty() || topic == null || topic.trim().isEmpty()) {
            return;
        }

        // 注册到所有传输层
        for (EventTransport transport : eventTransports) {
            try {
                transport.subscribe(topic, wrapper, (Class) eventType);

                ListenerRegistryManager registryManager = applicationContext.getBean(ListenerRegistryManager.class);
                registryManager.registerRemoteEventType(eventType, topic);

                log.debug("注册到{}: {} -> {} (topic: {})",
                        transport.getTransportType(), wrapper.getClass().getSimpleName(),
                        eventType.getSimpleName(), topic);
            } catch (Exception e) {
                log.error("注册到{}失败: {} -> {} (topic: {})",
                        transport.getTransportType(), wrapper.getClass().getSimpleName(),
                        eventType.getSimpleName(), topic, e);
            }
        }
    }

    /**
     * 生成接口监听器的唯一标识
     */
    private String generateInterfaceListenerKey(IEventListener<?> listener) {
        return listener.getClass().getName() + "#" + listener.topic();
    }

    /**
     * 生成方法监听器的唯一标识
     */
    private String generateMethodListenerKey(Object bean, Method method) {
        return bean.getClass().getName() + "#" + method.getName() + "#" +
                Arrays.toString(method.getParameterTypes());
    }

    /**
     * 判断是否应该跳过某个Bean的扫描
     */
    private boolean shouldSkipBean(String beanName, Object bean) {
        String className = bean.getClass().getName();

        // 跳过Spring框架内部Bean
        if (className.startsWith("org.springframework.") ||
                className.startsWith("org.apache.") ||
                className.startsWith("com.sun.") ||
                className.startsWith("java.") ||
                className.startsWith("javax.") ||
                beanName.startsWith("org.springframework.") ||
                beanName.contains("CGLIB") ||
                beanName.contains("EnhancerBy")) {
            return true;
        }

        // 跳过已知的事件总线组件，避免循环
        return bean instanceof IEventBus ||
                bean instanceof EventTransport ||
                bean instanceof EventListenerAutoRegistrar;
    }
}