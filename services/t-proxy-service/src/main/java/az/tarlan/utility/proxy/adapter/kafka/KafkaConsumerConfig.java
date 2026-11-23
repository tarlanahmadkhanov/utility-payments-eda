package az.tarlan.utility.proxy.adapter.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration(proxyBeanMethods = false)
public class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    private static final long INITIAL_BACKOFF_MS = 1_000L;
    private static final double BACKOFF_MULTIPLIER = 2.0;
    private static final long MAX_BACKOFF_MS = 30_000L;

    @Bean
    DefaultErrorHandler kafkaErrorHandler() {
        ExponentialBackOff backOff = new ExponentialBackOff(INITIAL_BACKOFF_MS, BACKOFF_MULTIPLIER);
        backOff.setMaxInterval(MAX_BACKOFF_MS);
        DefaultErrorHandler handler = new DefaultErrorHandler((record, exception) ->
                log.error("Skipping invalid message topic={} partition={} offset={}: {}",
                        record.topic(), record.partition(), record.offset(),
                        NestedExceptionUtils.getMostSpecificCause(exception).getMessage()),
                backOff);
        handler.addNotRetryableExceptions(InvalidIncomingEventException.class);
        return handler;
    }
}
