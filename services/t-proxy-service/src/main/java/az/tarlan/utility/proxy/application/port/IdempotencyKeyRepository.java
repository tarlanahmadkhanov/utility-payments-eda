package az.tarlan.utility.proxy.application.port;

import az.tarlan.utility.proxy.domain.IdempotencyKey;

import java.util.Optional;

public interface IdempotencyKeyRepository {

    boolean insertIfAbsent(IdempotencyKey key);

    Optional<IdempotencyKey> find(String customerId, String idempotencyKey);
}
