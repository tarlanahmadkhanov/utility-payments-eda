package az.tarlan.utility.proxy.adapter.persistence;

import az.tarlan.utility.proxy.application.port.IdempotencyKeyRepository;
import az.tarlan.utility.proxy.application.port.PaymentRepository;
import az.tarlan.utility.proxy.domain.IdempotencyKey;
import az.tarlan.utility.proxy.domain.Payment;
import az.tarlan.utility.proxy.domain.ServiceType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JooqIdempotencyKeyRepositoryIT extends AbstractPersistenceIT {

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);
    private static final String HASH_A = "a".repeat(64);
    private static final String HASH_B = "b".repeat(64);

    @Autowired
    private IdempotencyKeyRepository keys;

    @Autowired
    private PaymentRepository payments;

    @Test
    void firstInsertWinsAndIsReadBack() {
        UUID paymentId = newPayment("customer-1");
        IdempotencyKey key = new IdempotencyKey("customer-1", "key-1", HASH_A, paymentId, NOW);

        assertThat(keys.insertIfAbsent(key)).isTrue();

        assertThat(keys.find("customer-1", "key-1")).contains(key);
    }

    @Test
    void repeatedInsertOfSameKeyIsRejectedAndKeepsFirstRecord() {
        UUID firstPayment = newPayment("customer-1");
        UUID secondPayment = newPayment("customer-1");
        IdempotencyKey first = new IdempotencyKey("customer-1", "key-1", HASH_A, firstPayment, NOW);
        IdempotencyKey second = new IdempotencyKey("customer-1", "key-1", HASH_B, secondPayment, NOW.plusSeconds(1));
        keys.insertIfAbsent(first);

        assertThat(keys.insertIfAbsent(second)).isFalse();

        assertThat(keys.find("customer-1", "key-1")).contains(first);
    }

    @Test
    void sameKeyStringOfDifferentCustomersDoesNotCollide() {
        UUID paymentOne = newPayment("customer-1");
        UUID paymentTwo = newPayment("customer-2");

        assertThat(keys.insertIfAbsent(new IdempotencyKey("customer-1", "key-1", HASH_A, paymentOne, NOW))).isTrue();
        assertThat(keys.insertIfAbsent(new IdempotencyKey("customer-2", "key-1", HASH_A, paymentTwo, NOW))).isTrue();

        assertThat(keys.find("customer-1", "key-1").orElseThrow().paymentId()).isEqualTo(paymentOne);
        assertThat(keys.find("customer-2", "key-1").orElseThrow().paymentId()).isEqualTo(paymentTwo);
    }

    @Test
    void unknownKeyIsNotFound() {
        assertThat(keys.find("customer-1", "missing")).isEmpty();
    }

    @Test
    void keyForUnknownPaymentViolatesForeignKey() {
        IdempotencyKey key = new IdempotencyKey("customer-1", "key-1", HASH_A, UUID.randomUUID(), NOW);

        assertThatThrownBy(() -> keys.insertIfAbsent(key)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void paymentCannotBeBoundToTwoKeys() {
        UUID paymentId = newPayment("customer-1");
        keys.insertIfAbsent(new IdempotencyKey("customer-1", "key-1", HASH_A, paymentId, NOW));

        assertThatThrownBy(() -> keys.insertIfAbsent(new IdempotencyKey("customer-1", "key-2", HASH_A, paymentId, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID newPayment(String customerId) {
        Payment payment = Payment.accepted(UUID.randomUUID(), customerId, "ACC-1", ServiceType.WATER, 1000L, "AZN", NOW);
        payments.insert(payment);
        return payment.id();
    }
}
