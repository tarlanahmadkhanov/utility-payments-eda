package az.tarlan.utility.proxy.adapter.persistence;

import az.tarlan.utility.proxy.adapter.persistence.jooq.tables.records.IdempotencyKeysRecord;
import az.tarlan.utility.proxy.application.port.IdempotencyKeyRepository;
import az.tarlan.utility.proxy.domain.IdempotencyKey;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.ZoneOffset;
import java.util.Optional;

import static az.tarlan.utility.proxy.adapter.persistence.jooq.Tables.IDEMPOTENCY_KEYS;

@Repository
public class JooqIdempotencyKeyRepository implements IdempotencyKeyRepository {

    private final DSLContext dsl;

    public JooqIdempotencyKeyRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public boolean insertIfAbsent(IdempotencyKey key) {
        return dsl.insertInto(IDEMPOTENCY_KEYS)
                .set(IDEMPOTENCY_KEYS.CUSTOMER_ID, key.customerId())
                .set(IDEMPOTENCY_KEYS.IDEMPOTENCY_KEY, key.key())
                .set(IDEMPOTENCY_KEYS.REQUEST_HASH, key.requestHash())
                .set(IDEMPOTENCY_KEYS.PAYMENT_ID, key.paymentId())
                .set(IDEMPOTENCY_KEYS.CREATED_AT, key.createdAt().atOffset(ZoneOffset.UTC))
                .onConflict(IDEMPOTENCY_KEYS.CUSTOMER_ID, IDEMPOTENCY_KEYS.IDEMPOTENCY_KEY)
                .doNothing()
                .execute() == 1;
    }

    @Override
    public Optional<IdempotencyKey> find(String customerId, String idempotencyKey) {
        return dsl.selectFrom(IDEMPOTENCY_KEYS)
                .where(IDEMPOTENCY_KEYS.CUSTOMER_ID.eq(customerId))
                .and(IDEMPOTENCY_KEYS.IDEMPOTENCY_KEY.eq(idempotencyKey))
                .fetchOptional()
                .map(JooqIdempotencyKeyRepository::toDomain);
    }

    private static IdempotencyKey toDomain(IdempotencyKeysRecord r) {
        return new IdempotencyKey(
                r.getCustomerId(),
                r.getIdempotencyKey(),
                r.getRequestHash(),
                r.getPaymentId(),
                r.getCreatedAt().toInstant());
    }
}
