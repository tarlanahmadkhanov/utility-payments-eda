package az.tarlan.utility.proxy.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Idempotency record of a request (ADR-0003). The key belongs to a customer: the pair
 * ({@code customerId}, {@code key}) is unique.
 *
 * @param requestHash hex SHA-256 of the canonical request body; a repeated request with the same key must carry the same hash
 */
public record IdempotencyKey(String customerId, String key, String requestHash, UUID paymentId, Instant createdAt) {

    public IdempotencyKey {
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(requestHash, "requestHash");
        Objects.requireNonNull(paymentId, "paymentId");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
