package az.tarlan.utility.proxy.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A payment as the proxy knows it: the data of the accepted request plus the status derived from events.
 *
 * @param amountMinor amount in minor units of the currency (always positive, enforced by the database)
 * @param currency    ISO 4217 code
 */
public record Payment(
        UUID id,
        String customerId,
        String accountNumber,
        ServiceType serviceType,
        long amountMinor,
        String currency,
        PaymentStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public Payment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(accountNumber, "accountNumber");
        Objects.requireNonNull(serviceType, "serviceType");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /** A newly accepted payment: status {@link PaymentStatus#ACCEPTED}, both timestamps equal to {@code now}. */
    public static Payment accepted(UUID id, String customerId, String accountNumber, ServiceType serviceType,
                                   long amountMinor, String currency, Instant now) {
        return new Payment(id, customerId, accountNumber, serviceType, amountMinor, currency,
                PaymentStatus.ACCEPTED, now, now);
    }
}
