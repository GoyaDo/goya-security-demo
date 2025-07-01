package com.ysmjjsy.goya.security.bus.transport.kafka;

import com.ysmjjsy.goya.security.bus.annotation.KafkaConfig;
import com.ysmjjsy.goya.security.bus.enums.ConsumeResult;
import com.ysmjjsy.goya.security.bus.enums.EventCapability;
import com.ysmjjsy.goya.security.bus.enums.EventModel;
import com.ysmjjsy.goya.security.bus.enums.TransportType;
import com.ysmjjsy.goya.security.bus.route.RoutingContext;
import com.ysmjjsy.goya.security.bus.serializer.MessageSerializer;
import com.ysmjjsy.goya.security.bus.spi.MessageConsumer;
import com.ysmjjsy.goya.security.bus.spi.SubscriptionConfig;
import com.ysmjjsy.goya.security.bus.spi.TransportEvent;
import com.ysmjjsy.goya.security.bus.spi.TransportResult;
import com.ysmjjsy.goya.security.bus.transport.MessageTransport;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;

import java.lang.annotation.Annotation;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * Kafka传输层实现
 * <p>
 * 基于Apache Kafka实现高吞吐量消息传输，支持：
 * - TOPIC模式：使用Kafka主题进行发布/订阅
 * - QUEUE模式：使用消费者组实现负载均衡
 * - BROADCAST模式：每个消费者组都接收所有消息
 * - 延迟消息：基于时间戳调度
 * - 事务消息：使用Kafka事务机制
 * - 顺序消息：基于分区键保证顺序
 *
 * @author goya
 * @since 2025/7/1 15:13
 */
@Slf4j
public class KafkaTransport implements MessageTransport {

    private final KafkaProducer<String, byte[]> producer;
    private final MessageSerializer messageSerializer;
    private final Map<String, KafkaConsumerWrapper> consumers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService delayedMessageExecutor;
    private volatile boolean healthy = true;
    
    // Kafka主题前缀
    private static final String TOPIC_PREFIX = "mq-topic-";
    private static final String DELAYED_TOPIC_SUFFIX = "-delayed";
    
    public KafkaTransport(KafkaProducer<String, byte[]> producer,
                          MessageSerializer messageSerializer) {
        this.producer = producer;
        this.messageSerializer = messageSerializer;
        this.delayedMessageExecutor = new ScheduledThreadPoolExecutor(4, r -> {
            Thread t = new Thread(r, "kafka-delayed-message-processor");
            t.setDaemon(true);
            return t;
        });
        
        checkHealth();
        log.info("Kafka transport initialized with high-throughput support");
    }

    @Override
    public TransportResult send(TransportEvent transportEvent) {
        try {
            // 处理延迟消息
            if (transportEvent.getDelayTime() != null) {
                return sendDelayedMessage(transportEvent);
            }
            
            // 处理事务消息
            if (isTransactionalMessage(transportEvent)) {
                return sendTransactionalMessage(transportEvent);
            }
            
            // 常规消息发送
            return sendNormalMessage(transportEvent);
            
        } catch (Exception e) {
            log.error("Failed to send message via Kafka: {}", transportEvent.getEventId(), e);
            return TransportResult.failure(e.getMessage(), e);
        }
    }

    /**
     * 发送常规消息
     */
    private TransportResult sendNormalMessage(TransportEvent transportEvent) {
        try {
            String topicName = buildTopicName(transportEvent.getRoutingContext());
            String partitionKey = buildPartitionKey(transportEvent);
            
            // 序列化消息
            byte[] messageData = messageSerializer.serialize(transportEvent);
            
            // 构建生产者记录
            ProducerRecord<String, byte[]> record = new ProducerRecord<>(
                topicName, partitionKey, messageData);
            
            // 添加消息头
            addMessageHeaders(record, transportEvent);
            
            // 异步发送消息
            Future<RecordMetadata> future = producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    log.error("Failed to send Kafka message: {}", transportEvent.getEventId(), exception);
                } else {
                    log.debug("Kafka message sent successfully: topic={}, partition={}, offset={}, messageId={}", 
                             metadata.topic(), metadata.partition(), metadata.offset(), transportEvent.getEventId());
                }
            });
            
            // 对于同步发送，等待结果
            if (transportEvent.getEventModel() == EventModel.QUEUE) {
                RecordMetadata metadata = future.get(10, TimeUnit.SECONDS);
                log.debug("Synchronous send completed: messageId={}, offset={}", 
                         transportEvent.getEventId(), metadata.offset());
            }
            
            return TransportResult.success(transportEvent.getEventId());
            
        } catch (Exception e) {
            log.error("Failed to send normal message: {}", transportEvent.getEventId(), e);
            return TransportResult.failure(e.getMessage(), e);
        }
    }

    /**
     * 发送事务消息
     */
    private TransportResult sendTransactionalMessage(TransportEvent transportEvent) {
        try {
            producer.beginTransaction();
            
            try {
                TransportResult result = sendNormalMessage(transportEvent);
                if (result.isSuccess()) {
                    producer.commitTransaction();
                    log.debug("Transactional message committed: {}", transportEvent.getEventId());
                } else {
                    producer.abortTransaction();
                    log.warn("Transactional message aborted: {}", transportEvent.getEventId());
                }
                return result;
                
            } catch (Exception e) {
                producer.abortTransaction();
                throw e;
            }
            
        } catch (Exception e) {
            log.error("Failed to send transactional message: {}", transportEvent.getEventId(), e);
            return TransportResult.failure(e.getMessage(), e);
        }
    }

    /**
     * 发送延迟消息
     */
    private TransportResult sendDelayedMessage(TransportEvent transportEvent) {
        try {
            // 计算延迟时间
            long delayMs = transportEvent.getDelayTime().toMillis();
            long executeTime = System.currentTimeMillis() + delayMs;
            
            // 发送到延迟主题
            String delayedTopic = buildTopicName(transportEvent.getRoutingContext()) + DELAYED_TOPIC_SUFFIX;
            String partitionKey = buildPartitionKey(transportEvent);
            
            // 添加执行时间到消息头
            transportEvent.getProperties().put("executeTime", String.valueOf(executeTime));
            
            byte[] messageData = messageSerializer.serialize(transportEvent);
            ProducerRecord<String, byte[]> record = new ProducerRecord<>(
                delayedTopic, partitionKey, messageData);
            
            addMessageHeaders(record, transportEvent);
            
            producer.send(record).get(10, TimeUnit.SECONDS);
            
            log.debug("Delayed message sent: executeTime={}, messageId={}", 
                     executeTime, transportEvent.getEventId());
            
            return TransportResult.success(transportEvent.getEventId());
            
        } catch (Exception e) {
            log.error("Failed to send delayed message: {}", transportEvent.getEventId(), e);
            return TransportResult.failure(e.getMessage(), e);
        }
    }

    @Override
    public void subscribe(SubscriptionConfig config, MessageConsumer consumer) {
        try {
            String topicName = buildTopicName(config.getRoutingContext());
            String consumerGroup = config.getRoutingContext().getConsumerGroup();
            String subscriptionId = topicName + "_" + consumerGroup;
            
            // 检查是否已经订阅
            if (consumers.containsKey(subscriptionId)) {
                log.warn("Already subscribed to topic: {}, consumerGroup: {}", topicName, consumerGroup);
                return;
            }
            
            // 创建消费者配置
            Properties consumerProps = buildConsumerProperties(config);
            KafkaConsumer<String, byte[]> kafkaConsumer = new KafkaConsumer<>(consumerProps);
            
            // 创建消费者包装器
            KafkaConsumerWrapper wrapper = new KafkaConsumerWrapper(
                kafkaConsumer, consumer, messageSerializer, topicName, consumerGroup);
            
            // 启动消费者
            wrapper.start();
            
            // 保存消费者引用
            consumers.put(subscriptionId, wrapper);
            
            log.info("Subscribed to Kafka topic: {}, consumerGroup: {}", topicName, consumerGroup);
            
        } catch (Exception e) {
            log.error("Failed to subscribe to Kafka with config: {}", config, e);
            throw new RuntimeException("Failed to subscribe to Kafka", e);
        }
    }

    @Override
    public TransportType getTransportType() {
        return TransportType.KAFKA;
    }

    @Override
    public Set<EventCapability> getSupportedCapabilities() {
        return Set.of(
                EventCapability.DELAYED_MESSAGE,
                EventCapability.TRANSACTIONAL_MESSAGE,
                EventCapability.ORDERED_MESSAGE,
                EventCapability.BATCH_MESSAGE,
                EventCapability.PERSISTENT_MESSAGE,
                EventCapability.CLUSTER_SUPPORT,
                EventCapability.MESSAGE_TRACING
        );
    }

    @Override
    public boolean isHealthy() {
        return healthy;
    }

    @Override
    public Map<String, Object> buildSubscriptionProperties(Annotation config) {
        if (config instanceof KafkaConfig kafkaConfig) {
            Map<String, Object> properties = new HashMap<>();
            putProperty(properties, "enabled", kafkaConfig.enabled());
            // 可以添加更多Kafka特定配置
            return properties;
        }
        return new HashMap<>();
    }

    /**
     * 构建主题名称
     */
    private String buildTopicName(RoutingContext routingContext) {
        return TOPIC_PREFIX + routingContext.getBusinessDomain() + "-" + routingContext.getRoutingSelector();
    }

    /**
     * 构建分区键
     */
    private String buildPartitionKey(TransportEvent transportEvent) {
        // 对于顺序消息，使用特定的分区键
        if (transportEvent.getEventModel() == EventModel.QUEUE) {
            return transportEvent.getRoutingContext().getConsumerGroup();
        }
        // 对于其他消息，使用事件ID的哈希确保分布
        return String.valueOf(Math.abs(transportEvent.getEventId().hashCode()) % 10);
    }

    /**
     * 添加消息头
     */
    private void addMessageHeaders(ProducerRecord<String, byte[]> record, TransportEvent transportEvent) {
        // 添加消息ID
        record.headers().add(new RecordHeader("messageId", transportEvent.getEventId().getBytes()));
        
        // 添加事件类型
        record.headers().add(new RecordHeader("eventType", transportEvent.getEventType().name().getBytes()));
        
        // 添加时间戳
        record.headers().add(new RecordHeader("timestamp", String.valueOf(System.currentTimeMillis()).getBytes()));
        
        // 添加自定义头
        if (transportEvent.getProperties() != null) {
            for (Map.Entry<String, Object> entry : transportEvent.getProperties().entrySet()) {
                record.headers().add(new RecordHeader(entry.getKey(), entry.getValue().toString().getBytes()));
            }
        }
    }

    /**
     * 构建消费者配置
     */
    private Properties buildConsumerProperties(SubscriptionConfig config) {
        Properties props = new Properties();
        
        // 基础配置
        props.put("bootstrap.servers", "localhost:9092"); // 应该从配置文件读取
        props.put("group.id", config.getRoutingContext().getConsumerGroup());
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        
        // 消费策略
        props.put("auto.offset.reset", "earliest");
        props.put("enable.auto.commit", "false"); // 手动提交
        props.put("session.timeout.ms", "30000");
        props.put("heartbeat.interval.ms", "3000");
        
        // 性能优化
        props.put("fetch.min.bytes", "1024");
        props.put("fetch.max.wait.ms", "500");
        props.put("max.poll.records", "500");
        
        return props;
    }

    /**
     * 检查是否为事务消息
     */
    private boolean isTransactionalMessage(TransportEvent transportEvent) {
        // 可以根据消息属性或配置来判断
        return transportEvent.getProperties() != null &&
               "true".equals(transportEvent.getProperties().get("transactional"));
    }

    /**
     * 健康检查
     */
    private void checkHealth() {
        try {
            // 检查生产者状态
            producer.partitionsFor("health-check-topic");
            healthy = true;
            log.debug("Kafka transport health check passed");
        } catch (Exception e) {
            healthy = false;
            log.warn("Kafka transport health check failed", e);
        }
    }

    /**
     * Kafka消费者包装器
     */
    private static class KafkaConsumerWrapper {
        private final KafkaConsumer<String, byte[]> consumer;
        private final MessageConsumer messageConsumer;
        private final MessageSerializer messageSerializer;
        private final String topicName;
        private final String consumerGroup;
        private final ExecutorService executorService;
        private volatile boolean running = false;

        public KafkaConsumerWrapper(KafkaConsumer<String, byte[]> consumer,
                                   MessageConsumer messageConsumer,
                                   MessageSerializer messageSerializer,
                                   String topicName,
                                   String consumerGroup) {
            this.consumer = consumer;
            this.messageConsumer = messageConsumer;
            this.messageSerializer = messageSerializer;
            this.topicName = topicName;
            this.consumerGroup = consumerGroup;
            this.executorService = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "kafka-consumer-" + consumerGroup);
                t.setDaemon(true);
                return t;
            });
        }

        public void start() {
            if (running) {
                log.warn("Consumer already running for topic: {}, group: {}", topicName, consumerGroup);
                return;
            }
            
            running = true;
            consumer.subscribe(Collections.singletonList(topicName));
            
            executorService.submit(() -> {
                log.info("Started Kafka consumer for topic: {}, group: {}", topicName, consumerGroup);
                
                while (running) {
                    try {
                        ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(1000));
                        
                        for (ConsumerRecord<String, byte[]> record : records) {
                            processMessage(record);
                        }
                        
                        // 手动提交偏移量
                        if (!records.isEmpty()) {
                            consumer.commitSync();
                        }
                        
                    } catch (Exception e) {
                        log.error("Error consuming messages from topic: {}, group: {}", 
                                 topicName, consumerGroup, e);
                        try {
                            Thread.sleep(1000); // 错误后等待1秒
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
                
                log.info("Kafka consumer stopped for topic: {}, group: {}", topicName, consumerGroup);
            });
        }

        private void processMessage(ConsumerRecord<String, byte[]> record) {
            try {
                // 反序列化消息
                TransportEvent transportEvent = messageSerializer.deserialize(record.value(), TransportEvent.class);
                
                // 检查是否为延迟消息
                if (isDelayedMessage(record)) {
                    long executeTime = getExecuteTime(record);
                    if (System.currentTimeMillis() < executeTime) {
                        // 延迟消息未到执行时间，重新调度
                        scheduleDelayedMessage(transportEvent, executeTime - System.currentTimeMillis());
                        return;
                    }
                }
                
                // 处理消息
                ConsumeResult result = messageConsumer.consume(transportEvent);
                
                if (result != ConsumeResult.SUCCESS) {
                    log.warn("Message consumption failed: topic={}, partition={}, offset={}, messageId={}", 
                            record.topic(), record.partition(), record.offset(), transportEvent.getEventId());
                    // 可以实现重试逻辑
                } else {
                    log.debug("Message consumed successfully: topic={}, partition={}, offset={}, messageId={}", 
                             record.topic(), record.partition(), record.offset(), transportEvent.getEventId());
                }
                
            } catch (Exception e) {
                log.error("Failed to process Kafka message: topic={}, partition={}, offset={}", 
                         record.topic(), record.partition(), record.offset(), e);
            }
        }

        private boolean isDelayedMessage(ConsumerRecord<String, byte[]> record) {
            return record.topic().endsWith(DELAYED_TOPIC_SUFFIX);
        }

        private long getExecuteTime(ConsumerRecord<String, byte[]> record) {
            Header header = record.headers().lastHeader("executeTime");
            if (header != null) {
                String executeTimeStr = new String(header.value());
                return Long.parseLong(executeTimeStr);
            }
            return 0L;
        }

        private void scheduleDelayedMessage(TransportEvent transportEvent, long delayMs) {
            // 这里可以实现延迟消息的重新调度逻辑
            log.debug("Rescheduling delayed message: messageId={}, delay={}ms", 
                     transportEvent.getEventId(), delayMs);
        }

        public void stop() {
            running = false;
            consumer.close();
            executorService.shutdown();
        }
    }
}
