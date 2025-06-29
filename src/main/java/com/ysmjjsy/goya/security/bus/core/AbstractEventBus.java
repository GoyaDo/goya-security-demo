package com.ysmjjsy.goya.security.bus.core;

import com.ysmjjsy.goya.security.bus.BusException;
import com.ysmjjsy.goya.security.bus.api.IEvent;
import com.ysmjjsy.goya.security.bus.api.IEventBus;
import com.ysmjjsy.goya.security.bus.api.PublishResult;
import com.ysmjjsy.goya.security.bus.decision.DecisionResult;
import com.ysmjjsy.goya.security.bus.decision.MessageConfigDecision;
import com.ysmjjsy.goya.security.bus.encry.MessageEncryptor;
import com.ysmjjsy.goya.security.bus.enums.EventStatus;
import com.ysmjjsy.goya.security.bus.enums.TransportType;
import com.ysmjjsy.goya.security.bus.serializer.MessageSerializer;
import com.ysmjjsy.goya.security.bus.spi.EventRecord;
import com.ysmjjsy.goya.security.bus.spi.TransportEvent;
import com.ysmjjsy.goya.security.bus.spi.TransportResult;
import com.ysmjjsy.goya.security.bus.store.EventStore;
import com.ysmjjsy.goya.security.bus.transport.MessageTransport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.StringUtils;

import java.util.concurrent.CompletableFuture;

/**
 * 默认事件总线实现
 * <p>
 * 实现 IEventBus 接口，提供消息发布的核心功能
 * 集成智能决策引擎，支持多种传输层和路由策略
 * <p>
 * TODO
 * 1. 发布时一些特定参数底层适配需要优化
 * 2. 发布时需要查看监听器是否存在
 * 3. 异常逻辑
 * @author goya
 * @since 2025/6/24
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractEventBus implements IEventBus {

    private final MessageConfigDecision messageConfigDecision;
    private final TaskExecutor busTaskExecutor;
    private final LocalEventBus localEventBus;
    private final MessageSerializer messageSerializer;
    private final EventStore eventStore;
    private final RetryTemplate retryTemplate;

    @Override
    public PublishResult publish(IEvent event, MessageConfigHint hint) {
        if (event == null) {
            return PublishResult.failure("Event cannot be null");
        }

        try {
            // 验证事件
            validateEvent(event);

            // 智能决策
            DecisionResult decision = messageConfigDecision.decide(event, hint);
            log.debug("Decision result for event {}: {}", event.getEventId(), decision);

            event.setEventStatus(EventStatus.PENDING);

            if (decision.isLocalRecord()) {
                eventStore.save(EventRecord.builder().event(event)
                        .decision(decision).build());
            }

            // 根据路由范围决定处理方式
            if (TransportType.LOCAL.equals(decision.getTransportType())) {
                return publishLocal(event, decision);
            } else {
                return publishAuto(event, decision);
            }

        } catch (Exception e) {
            log.error("Failed to publish event: {}", event.getEventId(), e);
            return PublishResult.failure("Failed to publish event: " + e.getMessage(), e);
        }
    }

    /**
     * 仅本地发布
     */
    private PublishResult publishLocal(IEvent event, DecisionResult decision) {
        log.debug("Publishing event {} locally", event.getEventId());
        boolean success = localEventBus.publish(event, decision);
        if (success) {
            return PublishResult.success(event.getEventId(), TransportType.LOCAL);
        } else {
            return PublishResult.failure("No local listeners found for event: " + event.getEventKey());
        }
    }

    /**
     * 自动路由（混合模式）
     */
    private PublishResult publishAuto(IEvent event, DecisionResult decision) {
        log.debug("Publishing event {} in auto mode", event.getEventId());
        return sendToRemoteTransport(event, decision);
    }

    /**
     * 发送到远程传输层
     */
    private PublishResult sendToRemoteTransport(IEvent event, DecisionResult decision) {

        MessageTransport transport = getTransportByDecision(decision);
        if (transport == null) {
            return PublishResult.failure("No transport registered for type: " + decision.getTransportType());
        }
        try {
            // 构建传输消息
            TransportEvent transportEvent = buildTransportEvent(event, decision);

            TransportResult result = sendEvent(transportEvent, transport);
            if (result.isSuccess()) {
                return PublishResult.success(result.getMessageId(), decision.getTransportType());
            } else {
                return PublishResult.failure("Transport send failed: " + result.getErrorMessage(), result.getThrowable());
            }

        } catch (Exception e) {
            return PublishResult.failure("Failed to send via transport: " + e.getMessage(), e);
        }
    }

    /**
     * 根据决策结果获取传输层
     */
    private MessageTransport getTransportByDecision(DecisionResult decision) {
        return messageConfigDecision.getRegisteredTransports().get(decision.getTransportType());
    }

    /**
     * 构建传输消息
     */
    private TransportEvent buildTransportEvent(IEvent event, DecisionResult decision) {
        // 序列化事件
        byte[] body = messageSerializer.serialize(event);

        // 处理加密
        if (Boolean.TRUE.equals(decision.getEnableEncryption())) {
            try {
                MessageEncryptor encryptor = new MessageEncryptor();
                MessageEncryptor.EncryptionResult encryptionResult =
                        encryptor.encrypt(body, MessageEncryptor.EncryptionType.AES_128);
                if (encryptionResult.isEncrypted()) {
                    body = encryptionResult.getData();
                    log.debug("Message encrypted: {} -> {} bytes",
                            encryptionResult.getOriginalSize(), encryptionResult.getEncryptedSize());
                }
            } catch (Exception e) {
                log.warn("Failed to encrypt message, using original data", e);
            }
        }

        // 构建传输消息
        TransportEvent.TransportEventBuilder builder = TransportEvent.builder()
                .eventId(event.getEventId())
                .originEventKey(event.getEventKey())
                .eventClass(event.getClass().getName())
                .createTime(event.getCreateTime())
                .eventStatus(event.getEventStatus())
                .priority(event.getPriority())
                .body(body)
                .routingContext(decision.getRoutingContext())
                .eventModel(decision.getEventModel())
                .eventType(decision.getEventType())
                .reliabilityLevel(decision.getReliabilityLevel())
                .transportType(decision.getTransportType())
                .delayTime(decision.getDelayTime())
                .deliverTime(decision.getDeliverTime())
                .sequenceKey(decision.getSequenceKey())
                .ttl(decision.getTtl())
                .localRecord(decision.isLocalRecord())
                .persistent(decision.isPersistent())
                .retryTimes(decision.getRetryTimes())
                .enableCompression(decision.getEnableCompression())
                .enableEncryption(decision.getEnableEncryption())
                .idempotence(decision.getIdempotence())
                .transactionalId(decision.getTransactionalId())
                .properties(decision.getProperties());
        return builder.build();
    }

    /**
     * 验证事件
     */
    private void validateEvent(IEvent event) {
        if (!StringUtils.hasText(event.getEventId())) {
            throw new IllegalArgumentException("Event ID cannot be empty");
        }
        if (!StringUtils.hasText(event.getEventKey())) {
            throw new IllegalArgumentException("Event type cannot be empty");
        }
    }

    /**
     * 发送可靠消息（带重试）
     */
    private TransportResult sendEvent(TransportEvent transportEvent, MessageTransport transport) {
        CompletableFuture<TransportResult> resultCompletableFuture = CompletableFuture.supplyAsync(() -> {
            try {
                // 更新状态为发送中
                eventStore.updateStatus(transportEvent.getEventId(), EventStatus.SENDING, null);
                transportEvent.setEventStatus(EventStatus.SENDING);
                // 发送消息
                TransportResult result = transport.send(transportEvent);

                if (result.isSuccess()) {
                    // 发送成功
                    eventStore.updateStatus(transportEvent.getEventId(), EventStatus.SUCCESS, null);
                    log.debug("Reliable message sent successfully: {}", transportEvent.getEventId());
                    transportEvent.setEventStatus(EventStatus.SUCCESS);
                    return result;
                } else {
                    // 发送失败，准备重试
                    return handleSendFailure(transportEvent, transport, result.getErrorMessage(), result.getThrowable());
                }
            } catch (BusException e) {
                log.warn("Failed to send event {}: {}", transportEvent.getEventId(), e.getMessage());
                return TransportResult.failure(e.getMessage(), e);
            } catch (Exception e) {
                log.error("Failed to send reliable message: {}", transportEvent.getEventId(), e);
                return handleSendFailure(transportEvent, transport, e.getMessage(), e);
            }
        }, busTaskExecutor);
        return resultCompletableFuture.join();
    }

    /**
     * 处理发送失败
     */
    private TransportResult handleSendFailure(TransportEvent transportEvent, MessageTransport transport, String errorMessage, Throwable throwable) {
        try {
            SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
            retryPolicy.setMaxAttempts(transportEvent.getRetryTimes());
            retryTemplate.setRetryPolicy(retryPolicy);

            TransportResult result = retryTemplate.execute(context -> {
                log.warn("Retrying to send event: {}, attempt {}", transportEvent.getEventId(), context.getRetryCount() + 1);
                return transport.send(transportEvent);
            });

            if (result.isSuccess()) {
                eventStore.updateStatus(transportEvent.getEventId(), EventStatus.SUCCESS, null);
                log.debug("Retry succeeded for event: {}", transportEvent.getEventId());
                transportEvent.setEventStatus(EventStatus.SUCCESS);
            } else {
                eventStore.updateStatus(transportEvent.getEventId(), EventStatus.FAILED, errorMessage);
                log.error("Retry failed for event: {}, error: {}", transportEvent.getEventId(), result.getErrorMessage());
                transportEvent.setEventStatus(EventStatus.FAILED);
            }

            return result;
        } catch (Exception ex) {
            log.error("Retries exhausted for event: {}, error: {}", transportEvent.getEventId(), ex.getMessage(), ex);
            eventStore.updateStatus(transportEvent.getEventId(), EventStatus.FAILED, ex.getMessage());
            transportEvent.setEventStatus(EventStatus.FAILED);
            return TransportResult.failure("Retries exhausted: " + errorMessage, ex);
        }
    }
} 