package com.ysmjjsy.goya.security.bus.transport.rabbitmq;

import com.ysmjjsy.goya.security.bus.properties.BusProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.Properties;

/**
 * RabbitMQ 管理工具
 * 
 * 提供队列管理功能：
 * - 清理不兼容的队列
 * - 队列状态检查
 * - 队列重建
 *
 * @author goya
 * @since 2025/1/17
 */
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "bus.rabbitmq", name = "enabled", havingValue = "true")
public class RabbitMQManagementTool {

    private final RabbitAdmin rabbitAdmin;
    private final BusProperties busProperties;

    /**
     * 清理指定队列（如果存在参数不兼容的情况）
     */
    public boolean cleanupQueue(String queueName) {
        try {
            // 检查队列是否存在
            Properties queueProperties = rabbitAdmin.getQueueProperties(queueName);
            if (queueProperties != null) {
                log.info("Queue '{}' exists, attempting to delete for cleanup", queueName);
                
                // 删除队列
                boolean deleted = rabbitAdmin.deleteQueue(queueName);
                if (deleted) {
                    log.info("Successfully deleted queue: {}", queueName);
                    return true;
                } else {
                    log.warn("Failed to delete queue: {}", queueName);
                    return false;
                }
            } else {
                log.debug("Queue '{}' does not exist, no cleanup needed", queueName);
                return true;
            }
        } catch (Exception e) {
            log.error("Error during queue cleanup for '{}': {}", queueName, e.getMessage());
            return false;
        }
    }

    /**
     * 检查队列是否存在
     */
    public boolean queueExists(String queueName) {
        try {
            Properties queueProperties = rabbitAdmin.getQueueProperties(queueName);
            return queueProperties != null;
        } catch (Exception e) {
            log.debug("Queue '{}' does not exist or cannot be accessed: {}", queueName, e.getMessage());
            return false;
        }
    }

    /**
     * 获取队列信息
     */
    public Properties getQueueInfo(String queueName) {
        try {
            return rabbitAdmin.getQueueProperties(queueName);
        } catch (Exception e) {
            log.debug("Cannot get queue info for '{}': {}", queueName, e.getMessage());
            return null;
        }
    }

    /**
     * 清理所有事件总线相关的队列
     */
    public void cleanupAllEventQueues() {
        String queuePrefix = busProperties.getRabbitmq().getQueuePrefix();
        log.info("Starting cleanup of all event queues with prefix: {}", queuePrefix);
        
        // 注意：RabbitAdmin没有直接列出所有队列的方法
        // 这里我们只是提供一个清理接口，具体的队列名需要外部提供
        log.warn("Manual queue cleanup may be required. Please check RabbitMQ management interface for queues with prefix: {}", queuePrefix);
    }

    /**
     * 强制重建队列
     */
    public Queue forceRebuildQueue(String queueName, String topic) {
        log.info("Force rebuilding queue: {}", queueName);
        
        try {
            // 先删除队列
            cleanupQueue(queueName);
            
            // 等待一下确保删除完成
            Thread.sleep(1000);
            
            // 重新创建队列
            BusProperties.RabbitMQ config = busProperties.getRabbitmq();
            Queue newQueue = QueueBuilder.durable(queueName).build();
            
            rabbitAdmin.declareQueue(newQueue);
            log.info("Successfully rebuilt queue: {}", queueName);
            
            return newQueue;
            
        } catch (Exception e) {
            log.error("Failed to force rebuild queue '{}': {}", queueName, e.getMessage());
            throw new RuntimeException("Failed to rebuild queue: " + queueName, e);
        }
    }
} 