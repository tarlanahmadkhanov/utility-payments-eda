package az.tarlan.utility.proxy.adapter.kafka;

import az.tarlan.utility.proxy.application.ApplyPaymentEventService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventListener {

    private final IncomingEventParser parser;
    private final ApplyPaymentEventService applyPaymentEventService;

    public PaymentEventListener(IncomingEventParser parser, ApplyPaymentEventService applyPaymentEventService) {
        this.parser = parser;
        this.applyPaymentEventService = applyPaymentEventService;
    }

    @KafkaListener(topics = {KafkaTopics.WALLET_EVENTS, KafkaTopics.PARTNER_EVENTS})
    public void onMessage(ConsumerRecord<String, String> record) {
        parser.parse(record.value()).ifPresent(applyPaymentEventService::apply);
    }
}
