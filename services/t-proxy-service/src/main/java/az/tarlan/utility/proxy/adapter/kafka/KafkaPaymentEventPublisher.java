package az.tarlan.utility.proxy.adapter.kafka;

import az.tarlan.utility.proxy.application.EventPublicationException;
import az.tarlan.utility.proxy.application.port.PaymentEventPublisher;
import az.tarlan.utility.proxy.domain.Payment;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Component
public class KafkaPaymentEventPublisher implements PaymentEventPublisher {

    private static final String EVENT_TYPE = "PaymentRequested";
    private static final int EVENT_VERSION = 1;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;

    public KafkaPaymentEventPublisher(KafkaTemplate<String, String> kafkaTemplate, JsonMapper jsonMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void publishPaymentRequested(Payment payment) {
        String key = payment.id().toString();
        String value = serialize(payment);
        try {
            kafkaTemplate.send(KafkaTopics.PAYMENT_EVENTS, key, value).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EventPublicationException("Interrupted while publishing PaymentRequested for payment " + key, e);
        } catch (ExecutionException e) {
            throw new EventPublicationException("Failed to publish PaymentRequested for payment " + key, e.getCause());
        }
    }

    private String serialize(Payment payment) {
        PaymentRequestedPayload payload = new PaymentRequestedPayload(
                payment.id(),
                payment.customerId(),
                payment.accountNumber(),
                payment.serviceType(),
                payment.amountMinor(),
                payment.currency());
        EventEnvelope<PaymentRequestedPayload> envelope = new EventEnvelope<>(
                UUID.randomUUID(),
                EVENT_TYPE,
                EVENT_VERSION,
                payment.id(),
                null,
                payment.createdAt(),
                payload);
        try {
            return jsonMapper.writeValueAsString(envelope);
        } catch (JacksonException e) {
            throw new EventPublicationException("Failed to serialize PaymentRequested for payment " + payment.id(), e);
        }
    }
}
