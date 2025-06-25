package com.ysmjjsy.goya.security.bus.processor;

import com.ysmjjsy.goya.security.bus.annotation.IListener;
import com.ysmjjsy.goya.security.bus.domain.IEvent;
import com.ysmjjsy.goya.security.bus.exception.EventHandleException;
import com.ysmjjsy.goya.security.bus.listener.IEventListener;
import com.ysmjjsy.goya.security.bus.transport.rabbitmq.RabbitMqConfig;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;

/**
 * 方法事件监听器适配器
 * 将标注@IListener的方法包装为IEventListener接口
 * <p>
 * 注意：此类不应该被Spring自动扫描为Bean，它是动态创建的包装器
 *
 * @author goya
 * @since 2025/6/13 18:00
 */
@Getter
public class MethodIEventListenerWrapper implements IEventListener<IEvent> {

    private static final Logger logger = LoggerFactory.getLogger(MethodIEventListenerWrapper.class);
    private static final SpelExpressionParser EXPRESSION_PARSER = new SpelExpressionParser();

    private final Object targetBean;
    private final Method targetMethod;
    private final IListener annotation;

    /**
     * 获取条件表达式
     */
    private final Expression conditionExpression;

    private final String topic;

    public MethodIEventListenerWrapper(Object targetBean, Method targetMethod, IListener annotation, String topic) {
        this.targetBean = targetBean;
        this.targetMethod = targetMethod;
        this.annotation = annotation;

        // 预编译SpEL表达式
        if (StringUtils.hasText(annotation.condition())) {
            this.conditionExpression = EXPRESSION_PARSER.parseExpression(annotation.condition());
        } else {
            this.conditionExpression = null;
        }

        if (org.apache.commons.lang3.StringUtils.isNotBlank(topic)) {
            this.topic = topic;
        } else {
            this.topic = annotation.topic();
        }

        // 确保方法可访问
        this.targetMethod.setAccessible(true);
    }

    @Override
    public void onEvent(IEvent event) throws EventHandleException {
        try {
            logger.debug("Processing event {} with method {}.{}",
                    event.getEventId(), targetBean.getClass().getSimpleName(), targetMethod.getName());

            // 调用目标方法
            targetMethod.invoke(targetBean, event);

            logger.debug("Event {} processed successfully by method {}.{}",
                    event.getEventId(), targetBean.getClass().getSimpleName(), targetMethod.getName());

        } catch (Exception e) {
            throw new EventHandleException(
                    event.getEventId(),
                    getListenerIdentifier(),
                    "Failed to process event in method listener", e);
        }
    }

    public boolean checkRules(IEvent event, String topic) {
        // 检查条件表达式
        if (!evaluateCondition(event)) {
            logger.debug("Event {} skipped by condition: {}", event.getEventId(), annotation.condition());
            return false;
        }

        if (!evaluateTopic(event.getTopic())
                && !evaluateTopic(topic)) {
            logger.debug("Event {} skipped by topic: {}", event.getEventId(), annotation.topic());
            return false;
        }

        return true;
    }

    @Override
    public String topic() {
        return annotation.topic();
    }

    /**
     * 获取RabbitMQ配置
     */
    public RabbitMqConfig getRabbitMqConfig() {
        return annotation.rabbitmq();
    }

    private boolean evaluateTopic(String topic) {
        return org.apache.commons.lang3.StringUtils.equals(this.topic, topic);
    }

    /**
     * 评估SpEL条件表达式
     *
     * @param event 事件对象
     * @return 是否满足条件
     */
    private boolean evaluateCondition(IEvent event) {
        if (conditionExpression == null) {
            return true;
        }

        try {
            StandardEvaluationContext context = new StandardEvaluationContext();
            context.setVariable("event", event);
            context.setVariable("eventType", event.getEventType());
            context.setVariable("eventId", event.getEventId());
            context.setVariable("topic", event.getTopic());

            Object result = conditionExpression.getValue(context);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            logger.warn("Error evaluating condition expression '{}' for event {}: {}",
                    annotation.condition(), event.getEventId(), e.getMessage());
            return false;
        }
    }

    /**
     * 获取监听器标识符
     */
    private String getListenerIdentifier() {
        return String.format("%s.%s",
                targetBean.getClass().getSimpleName(),
                targetMethod.getName());
    }

}