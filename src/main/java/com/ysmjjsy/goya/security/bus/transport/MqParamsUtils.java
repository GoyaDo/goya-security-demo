package com.ysmjjsy.goya.security.bus.transport;

import com.ysmjjsy.goya.security.bus.annotation.RabbitConfig;
import com.ysmjjsy.goya.security.bus.enums.TransportType;
import com.ysmjjsy.goya.security.bus.transport.rabbitmq.RabbitMQConstants;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.core.QueueBuilder;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * <p></p>
 *
 * @author goya
 * @since 2025/7/3 16:13
 */
@UtilityClass
public class MqParamsUtils {

    public static Map<String, Object> buildSubscriptionProperties(TransportType transportType) {
       switch (transportType){
           case RABBITMQ :{
               return buildSubscriptionProperties(new RabbitConfig(){

                   @Override
                   public Class<? extends Annotation> annotationType() {
                       return null;
                   }

                   @Override
                   public boolean enabled() {
                       return true;
                   }

                   @Override
                   public boolean queueDurable() {
                       return true;
                   }

                   @Override
                   public boolean queueAutoDelete() {
                       return false;
                   }

                   @Override
                   public boolean exchangeDurable() {
                       return true;
                   }

                   @Override
                   public boolean exchangeAutoDelete() {
                       return false;
                   }

                   @Override
                   public int concurrentConsumers() {
                       return 1;
                   }

                   @Override
                   public int messageTTL() {
                       return 0;
                   }

                   @Override
                   public int expires() {
                       return 0;
                   }

                   @Override
                   public long maxLength() {
                       return 0;
                   }

                   @Override
                   public int maxLengthBytes() {
                       return 0;
                   }

                   @Override
                   public String overflow() {
                       return "";
                   }

                   @Override
                   public String dlx() {
                       return "";
                   }

                   @Override
                   public String dlrk() {
                       return "";
                   }

                   @Override
                   public int maxPriority() {
                       return 0;
                   }

                   @Override
                   public boolean lazy() {
                       return false;
                   }

                   @Override
                   public String locator() {
                       return "";
                   }

                   @Override
                   public boolean singleActiveConsumer() {
                       return true;
                   }

                   @Override
                   public boolean quorum() {
                       return false;
                   }

                   @Override
                   public boolean stream() {
                       return false;
                   }

                   @Override
                   public int deliveryLimit() {
                       return 0;
                   }
               });
           }
           case KAFKA :{
               return Collections.emptyMap();
           }
           case REDIS:{
               return Collections.emptyMap();
           }
           default :{
               return Collections.emptyMap();
           }
       }
    }

    public static Map<String, Object> buildSubscriptionProperties(Annotation config) {
        if (config instanceof RabbitConfig rabbitConfig) {
            Map<String, Object> properties = new HashMap<>();
            putProperty(properties, RabbitMQConstants.QUEUE_DURABLE, rabbitConfig.queueDurable());
            putProperty(properties, RabbitMQConstants.EXCHANGE_DURABLE, rabbitConfig.exchangeDurable());
            putProperty(properties, RabbitMQConstants.QUEUE_AUTO_DELETE, rabbitConfig.queueAutoDelete());
            putProperty(properties, RabbitMQConstants.EXCHANGE_AUTO_DELETE, rabbitConfig.exchangeAutoDelete());
            putProperty(properties, RabbitMQConstants.CONCURRENT_CONSUMERS, rabbitConfig.concurrentConsumers());

            putProperty(properties, RabbitMQConstants.X_MESSAGE_TTL, rabbitConfig.messageTTL());
            putProperty(properties, RabbitMQConstants.X_EXPIRES, rabbitConfig.expires());
            putProperty(properties, RabbitMQConstants.X_MAX_LENGTH, rabbitConfig.maxLength());
            putProperty(properties, RabbitMQConstants.X_MAX_LENGTH_BYTES, rabbitConfig.maxLengthBytes());
            QueueBuilder.Overflow overflow = convertOverflow(rabbitConfig.overflow());
            if (Objects.nonNull(overflow)) {
                putProperty(properties, RabbitMQConstants.X_OVERFLOW, rabbitConfig.overflow());
            }
            putProperty(properties, RabbitMQConstants.X_DEAD_LETTER_EXCHANGE, rabbitConfig.dlx());
            putProperty(properties, RabbitMQConstants.X_DEAD_LETTER_ROUTING_KEY, rabbitConfig.dlrk());
            putProperty(properties, RabbitMQConstants.X_MAX_PRIORITY, rabbitConfig.maxPriority());
            if (rabbitConfig.lazy()) {
                putProperty(properties, RabbitMQConstants.X_QUEUE_MODE, "lazy");
            }
            QueueBuilder.LeaderLocator leaderLocator = convertLocator(rabbitConfig.locator());
            if (Objects.nonNull(leaderLocator)) {
                putProperty(properties, RabbitMQConstants.X_QUEUE_MASTER_LOCATION, rabbitConfig.locator());
            }
            putProperty(properties, RabbitMQConstants.X_SINGLE_ACTIVE_CONSUMER, rabbitConfig.singleActiveConsumer());
            if (rabbitConfig.quorum()) {
                putProperty(properties, RabbitMQConstants.X_QUEUE_TYPE, "quorum");
            }
            if (rabbitConfig.stream()) {
                putProperty(properties, RabbitMQConstants.X_QUEUE_TYPE, "stream");
            }
            putProperty(properties, RabbitMQConstants.X_DELIVERY_LIMIT, rabbitConfig.deliveryLimit());
            return properties;
        }
        return Collections.emptyMap();
    }

    public static void putProperty(Map<String, Object> properties, String key, Object value) {
        if (value !=null){
            if (value instanceof String){
                String valueStr = (String) value;
                if (StringUtils.isBlank(valueStr)){
                    return;
                }
            }
            if (value instanceof Integer){
                int valueInt = (int) value;
                if (valueInt == 0){
                    return;
                }
            }

            if (value instanceof Long){
                long valueInt = (Long) value;
                if (valueInt == 0L){
                    return;
                }
            }
            properties.put(key, value);
        }
    }

    private QueueBuilder.LeaderLocator convertLocator(String locator) {
        if (StringUtils.isBlank(locator)) {
            return null;
        }
        switch (locator) {
            case "min-masters":
                return QueueBuilder.LeaderLocator.minLeaders;
            case "client-local":
                return QueueBuilder.LeaderLocator.clientLocal;
            case "random":
                return QueueBuilder.LeaderLocator.random;
        }
        return null;
    }

    private QueueBuilder.Overflow convertOverflow(String overflow) {
        if (StringUtils.isBlank(overflow)) {
            return null;
        }
        switch (overflow) {
            case "drop-head":
                return QueueBuilder.Overflow.dropHead;
            case "reject-publish":
                return QueueBuilder.Overflow.rejectPublish;
        }
        return null;
    }
}
