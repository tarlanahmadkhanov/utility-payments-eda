package az.tarlan.utility.proxy.adapter.kafka;

import az.tarlan.utility.proxy.domain.ServiceType;

import java.util.UUID;

public record PaymentRequestedPayload(
        UUID paymentId,
        String customerId,
        String accountNumber,
        ServiceType serviceType,
        long amountMinor,
        String currency) {
}
