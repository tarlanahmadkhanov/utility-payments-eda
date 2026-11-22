package az.tarlan.utility.proxy.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record IdempotencyKey(String customerId, String key, String requestHash, UUID paymentId, Instant createdAt) {

    public IdempotencyKey {
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(requestHash, "requestHash");
        Objects.requireNonNull(paymentId, "paymentId");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
