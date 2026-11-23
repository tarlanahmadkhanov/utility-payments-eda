package az.tarlan.utility.proxy.application;

import az.tarlan.utility.proxy.domain.PaymentEventType;

import java.util.Objects;
import java.util.UUID;

public record IncomingPaymentEvent(String eventId, PaymentEventType type, UUID paymentId) {

    public IncomingPaymentEvent {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(paymentId, "paymentId");
    }
}
