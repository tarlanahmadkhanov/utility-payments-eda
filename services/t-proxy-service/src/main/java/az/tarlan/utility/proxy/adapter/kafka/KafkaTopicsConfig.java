package az.tarlan.utility.proxy.adapter.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration(proxyBeanMethods = false)
public class KafkaTopicsConfig {

    @Bean
    NewTopic paymentEventsTopic() {
        return TopicBuilder.name(KafkaTopics.PAYMENT_EVENTS).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic walletEventsTopic() {
        return TopicBuilder.name(KafkaTopics.WALLET_EVENTS).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic partnerEventsTopic() {
        return TopicBuilder.name(KafkaTopics.PARTNER_EVENTS).partitions(3).replicas(1).build();
    }
}
