package az.tarlan.utility.proxy.application;

import az.tarlan.utility.proxy.domain.Payment;

public sealed interface AcceptPaymentResult {

    record Accepted(Payment payment) implements AcceptPaymentResult {
    }

    record IdempotencyKeyReused() implements AcceptPaymentResult {
    }
}
