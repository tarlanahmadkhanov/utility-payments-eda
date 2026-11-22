package az.tarlan.utility.proxy.application.port;

import az.tarlan.utility.proxy.domain.IdempotencyKey;

import java.util.Optional;

/**
 * Storage of idempotency keys (ADR-0003). The caller inserts the payment and the key in one database transaction;
 * the payment goes first, because the key references it.
 */
public interface IdempotencyKeyRepository {

    /**
     * Inserts the key unless a key with the same customer and key value already exists. If a concurrent transaction
     * has inserted the same key and is not finished yet, the call waits for it to finish.
     *
     * @return {@code true} if the key was inserted, {@code false} if it already existed
     */
    boolean insertIfAbsent(IdempotencyKey key);

    /** Finds the key of the customer. */
    Optional<IdempotencyKey> find(String customerId, String idempotencyKey);
}
