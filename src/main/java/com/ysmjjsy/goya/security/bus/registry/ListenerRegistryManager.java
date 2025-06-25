package com.ysmjjsy.goya.security.bus.registry;

import com.ysmjjsy.goya.security.bus.domain.IEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 监听器注册状态管理器
 * 跟踪和管理分布式环境中的监听器注册状态
 * 
 * @author goya
 * @since 2025/6/25
 */
@Slf4j
@Component
public class ListenerRegistryManager {

    /**
     * 本地注册的监听器类型
     */
    private final Set<Class<? extends IEvent>> localEventTypes = ConcurrentHashMap.newKeySet();

    /**
     * 远程订阅的监听器类型
     */
    private final Set<Class<? extends IEvent>> remoteEventTypes = ConcurrentHashMap.newKeySet();

    /**
     * 事件类型到主题的映射
     */
    private final Map<Class<? extends IEvent>, Set<String>> eventTopicMapping = new ConcurrentHashMap<>();

    /**
     * 注册本地监听器类型
     */
    public void registerLocalEventType(Class<? extends IEvent> eventType, String topic) {
        localEventTypes.add(eventType);
        eventTopicMapping.computeIfAbsent(eventType, k -> ConcurrentHashMap.newKeySet()).add(topic);
        log.debug("注册本地事件类型: {} (主题: {})", eventType.getSimpleName(), topic);
    }

    /**
     * 注册远程监听器类型
     */
    public void registerRemoteEventType(Class<? extends IEvent> eventType, String topic) {
        remoteEventTypes.add(eventType);
        eventTopicMapping.computeIfAbsent(eventType, k -> ConcurrentHashMap.newKeySet()).add(topic);
        log.debug("注册远程事件类型: {} (主题: {})", eventType.getSimpleName(), topic);
    }

    /**
     * 检查是否有本地监听器
     */
    public boolean hasLocalListener(Class<? extends IEvent> eventType) {
        return localEventTypes.contains(eventType);
    }

    /**
     * 检查是否有远程订阅
     */
    public boolean hasRemoteSubscription(Class<? extends IEvent> eventType) {
        return remoteEventTypes.contains(eventType);
    }

    /**
     * 检查事件类型是否已注册（本地或远程）
     */
    public boolean isEventTypeRegistered(Class<? extends IEvent> eventType) {
        return hasLocalListener(eventType) || hasRemoteSubscription(eventType);
    }

    /**
     * 获取事件类型的所有主题
     */
    public Set<String> getTopicsForEventType(Class<? extends IEvent> eventType) {
        return eventTopicMapping.getOrDefault(eventType, Collections.emptySet());
    }

    /**
     * 获取所有已注册的事件类型
     */
    public Set<Class<? extends IEvent>> getAllRegisteredEventTypes() {
        Set<Class<? extends IEvent>> allTypes = new HashSet<>(localEventTypes);
        allTypes.addAll(remoteEventTypes);
        return allTypes;
    }

    /**
     * 获取只有本地监听器的事件类型
     */
    public Set<Class<? extends IEvent>> getLocalOnlyEventTypes() {
        Set<Class<? extends IEvent>> localOnly = new HashSet<>(localEventTypes);
        localOnly.removeAll(remoteEventTypes);
        return localOnly;
    }

    /**
     * 获取只有远程订阅的事件类型
     */
    public Set<Class<? extends IEvent>> getRemoteOnlyEventTypes() {
        Set<Class<? extends IEvent>> remoteOnly = new HashSet<>(remoteEventTypes);
        remoteOnly.removeAll(localEventTypes);
        return remoteOnly;
    }

    /**
     * 获取既有本地又有远程的事件类型
     */
    public Set<Class<? extends IEvent>> getBothLocalAndRemoteEventTypes() {
        Set<Class<? extends IEvent>> both = new HashSet<>(localEventTypes);
        both.retainAll(remoteEventTypes);
        return both;
    }

    /**
     * 获取注册统计信息
     */
    public Map<String, Object> getRegistryStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("localEventTypes", localEventTypes.size());
        stats.put("remoteEventTypes", remoteEventTypes.size());
        stats.put("totalEventTypes", getAllRegisteredEventTypes().size());
        stats.put("localOnlyEventTypes", getLocalOnlyEventTypes().size());
        stats.put("remoteOnlyEventTypes", getRemoteOnlyEventTypes().size());
        stats.put("bothLocalAndRemoteEventTypes", getBothLocalAndRemoteEventTypes().size());
        stats.put("totalTopics", eventTopicMapping.values().stream().mapToInt(Set::size).sum());
        return stats;
    }

    /**
     * 打印注册状态摘要
     */
    public void printRegistrySummary() {
        log.debug("=== 监听器注册状态摘要 ===");
        log.debug("本地事件类型: {}", localEventTypes.size());
        log.debug("远程事件类型: {}", remoteEventTypes.size());
        log.debug("总事件类型: {}", getAllRegisteredEventTypes().size());
        
        if (log.isDebugEnabled()) {
            log.debug("本地事件类型详情: {}", localEventTypes.stream()
                    .map(Class::getSimpleName).toArray());
            log.debug("远程事件类型详情: {}", remoteEventTypes.stream()
                    .map(Class::getSimpleName).toArray());
        }
    }
} 