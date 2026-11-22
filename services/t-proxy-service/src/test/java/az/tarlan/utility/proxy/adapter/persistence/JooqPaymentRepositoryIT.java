package az.tarlan.utility.proxy.adapter.persistence;

import az.tarlan.utility.proxy.application.port.PaymentRepository;
import az.tarlan.utility.proxy.application.port.StatusUpdateResult;
import az.tarlan.utility.proxy.domain.Payment;
import az.tarlan.utility.proxy.domain.PaymentEventType;
import az.tarlan.utility.proxy.domain.PaymentStatus;
import az.tarlan.utility.proxy.domain.ServiceType;
import az.tarlan.utility.proxy.domain.TransitionOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JooqPaymentRepositoryIT extends AbstractPersistenceIT {

    private static final String CUSTOMER = "customer-1";

    // PostgreSQL stores timestamptz with microsecond precision.
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @Autowired
    private PaymentRepository repository;

    @Test
    void insertedPaymentIsFoundByIdAndCustomer() {
        Payment payment = payment(PaymentStatus.ACCEPTED);

        repository.insert(payment);

        assertThat(repository.findByIdAndCustomerId(payment.id(), CUSTOMER)).contains(payment);
    }

    @Test
    void paymentOfAnotherCustomerIsNotFound() {
        Payment payment = payment(PaymentStatus.ACCEPTED);
        repository.insert(payment);

        assertThat(repository.findByIdAndCustomerId(payment.id(), "customer-2")).isEmpty();
    }

    @Test
    void unknownPaymentIsNotFound() {
        assertThat(repository.findByIdAndCustomerId(UUID.randomUUID(), CUSTOMER)).isEmpty();
    }

    @Test
    void duplicatePaymentIdIsRejected() {
        Payment payment = payment(PaymentStatus.ACCEPTED);
        repository.insert(payment);

        assertThatThrownBy(() -> repository.insert(payment)).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void allowedTransitionIsApplied() {
        Payment payment = payment(PaymentStatus.ACCEPTED);
        repository.insert(payment);
        Instant updatedAt = NOW.plusSeconds(5);

        StatusUpdateResult result = repository.updateStatus(payment.id(), PaymentStatus.FUNDS_RESERVED,
                PaymentEventType.FUNDS_RESERVED.applicableFrom(), updatedAt);

        assertThat(result).isInstanceOf(StatusUpdateResult.Applied.class);
        Payment stored = repository.findByIdAndCustomerId(payment.id(), CUSTOMER).orElseThrow();
        assertThat(stored.status()).isEqualTo(PaymentStatus.FUNDS_RESERVED);
        assertThat(stored.updatedAt()).isEqualTo(updatedAt);
        assertThat(stored.createdAt()).isEqualTo(payment.createdAt());
    }

    @Test
    void jumpForwardIsApplied() {
        Payment payment = payment(PaymentStatus.ACCEPTED);
        repository.insert(payment);

        StatusUpdateResult result = repository.updateStatus(payment.id(), PaymentStatus.COMPLETED,
                PaymentEventType.FUNDS_CAPTURED.applicableFrom(), NOW.plusSeconds(1));

        assertThat(result).isInstanceOf(StatusUpdateResult.Applied.class);
        assertThat(statusOf(payment)).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    void duplicateEventIsNotAppliedAndKeepsUpdatedAt() {
        Payment payment = payment(PaymentStatus.FUNDS_RESERVED);
        repository.insert(payment);

        StatusUpdateResult result = repository.updateStatus(payment.id(), PaymentStatus.FUNDS_RESERVED,
                PaymentEventType.FUNDS_RESERVED.applicableFrom(), NOW.plusSeconds(1));

        assertThat(result).isEqualTo(new StatusUpdateResult.NotApplied(PaymentStatus.FUNDS_RESERVED));
        assertThat(repository.findByIdAndCustomerId(payment.id(), CUSTOMER).orElseThrow().updatedAt())
                .isEqualTo(payment.updatedAt());
    }

    @Test
    void lateEventDoesNotMoveStatusBack() {
        Payment payment = payment(PaymentStatus.PARTNER_CONFIRMED);
        repository.insert(payment);

        StatusUpdateResult result = repository.updateStatus(payment.id(), PaymentStatus.FUNDS_RESERVED,
                PaymentEventType.FUNDS_RESERVED.applicableFrom(), NOW.plusSeconds(1));

        assertThat(result).isEqualTo(new StatusUpdateResult.NotApplied(PaymentStatus.PARTNER_CONFIRMED));
        assertThat(statusOf(payment)).isEqualTo(PaymentStatus.PARTNER_CONFIRMED);
    }

    @Test
    void terminalStatusIsAbsorbing() {
        Payment payment = payment(PaymentStatus.COMPLETED);
        repository.insert(payment);

        StatusUpdateResult result = repository.updateStatus(payment.id(), PaymentStatus.FAILED,
                PaymentEventType.FUNDS_RELEASED.applicableFrom(), NOW.plusSeconds(1));

        assertThat(result).isEqualTo(new StatusUpdateResult.NotApplied(PaymentStatus.COMPLETED));
        assertThat(statusOf(payment)).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    void contradictingEventIsNotApplied() {
        Payment payment = payment(PaymentStatus.COMPENSATING);
        repository.insert(payment);

        StatusUpdateResult result = repository.updateStatus(payment.id(), PaymentStatus.COMPLETED,
                PaymentEventType.FUNDS_CAPTURED.applicableFrom(), NOW.plusSeconds(1));

        assertThat(result).isEqualTo(new StatusUpdateResult.NotApplied(PaymentStatus.COMPENSATING));
        assertThat(statusOf(payment)).isEqualTo(PaymentStatus.COMPENSATING);
    }

    @Test
    void updateOfUnknownPaymentReportsNotFound() {
        StatusUpdateResult result = repository.updateStatus(UUID.randomUUID(), PaymentStatus.FUNDS_RESERVED,
                PaymentEventType.FUNDS_RESERVED.applicableFrom(), NOW);

        assertThat(result).isInstanceOf(StatusUpdateResult.PaymentNotFound.class);
    }

    @Test
    void emptyAllowedSetNeverApplies() {
        Payment payment = payment(PaymentStatus.ACCEPTED);
        repository.insert(payment);

        StatusUpdateResult result = repository.updateStatus(payment.id(), PaymentStatus.FAILED, Set.of(), NOW);

        assertThat(result).isEqualTo(new StatusUpdateResult.NotApplied(PaymentStatus.ACCEPTED));
    }

    @ParameterizedTest(name = "{0} while {1}")
    @MethodSource("everyEventInEveryStatus")
    void conditionalUpdateFollowsTransitionMatrix(PaymentEventType event, PaymentStatus current) {
        Payment payment = payment(current);
        repository.insert(payment);

        StatusUpdateResult result = repository.updateStatus(payment.id(), event.targetStatus(),
                event.applicableFrom(), NOW.plusSeconds(1));

        if (event.decide(current) == TransitionOutcome.APPLY) {
            assertThat(result).isInstanceOf(StatusUpdateResult.Applied.class);
            assertThat(statusOf(payment)).isEqualTo(event.targetStatus());
        } else {
            assertThat(result).isEqualTo(new StatusUpdateResult.NotApplied(current));
            assertThat(statusOf(payment)).isEqualTo(current);
        }
    }

    static Stream<Arguments> everyEventInEveryStatus() {
        return EnumSet.allOf(PaymentEventType.class).stream()
                .flatMap(event -> EnumSet.allOf(PaymentStatus.class).stream()
                        .map(status -> Arguments.of(event, status)));
    }

    private PaymentStatus statusOf(Payment payment) {
        return repository.findByIdAndCustomerId(payment.id(), CUSTOMER).orElseThrow().status();
    }

    private static Payment payment(PaymentStatus status) {
        return new Payment(UUID.randomUUID(), CUSTOMER, "ACC-1", ServiceType.ELECTRICITY, 1500L, "AZN",
                status, NOW, NOW);
    }
}
