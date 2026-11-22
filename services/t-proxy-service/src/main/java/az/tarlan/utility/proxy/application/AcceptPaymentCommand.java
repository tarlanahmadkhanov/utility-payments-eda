package az.tarlan.utility.proxy.application;

import az.tarlan.utility.proxy.domain.ServiceType;

import java.util.Objects;

public record AcceptPaymentCommand(
        String customerId,
        String idempotencyKey,
        String accountNumber,
        ServiceType serviceType,
        long amountMinor,
        String currency) {

    public AcceptPaymentCommand {
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(accountNumber, "accountNumber");
        Objects.requireNonNull(serviceType, "serviceType");
        Objects.requireNonNull(currency, "currency");
    }
}
