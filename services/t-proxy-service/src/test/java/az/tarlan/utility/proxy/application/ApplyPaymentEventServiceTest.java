package az.tarlan.utility.proxy.application;

import az.tarlan.utility.proxy.application.port.PaymentRepository;
import az.tarlan.utility.proxy.application.port.StatusUpdateResult;
import az.tarlan.utility.proxy.domain.PaymentEventType;
import az.tarlan.utility.proxy.domain.PaymentStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplyPaymentEventServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-23T08:00:00Z");

    @Mock
    PaymentRepository repository;

    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    ApplyPaymentEventService service;

    @BeforeEach
    void setUp() {
        service = new ApplyPaymentEventService(repository, meterRegistry, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static IncomingPaymentEvent event(PaymentEventType type, UUID paymentId) {
        return new IncomingPaymentEvent(UUID.randomUUID().toString(), type, paymentId);
    }

    private double contradictions(PaymentEventType type, PaymentStatus status) {
        var counter = meterRegistry.find(ApplyPaymentEventService.CONTRADICTIONS_METRIC)
                .tag("eventType", type.typeName())
                .tag("currentStatus", status.name())
                .counter();
        return counter == null ? 0 : counter.count();
    }

    @ParameterizedTest
    @EnumSource(PaymentEventType.class)
    void appliedEventUsesTargetStatusAndAllowedSourcesFromTheMatrix(PaymentEventType type) {
        UUID paymentId = UUID.randomUUID();
        when(repository.updateStatus(paymentId, type.targetStatus(), type.applicableFrom(), NOW))
                .thenReturn(new StatusUpdateResult.Applied());

        assertThat(service.apply(event(type, paymentId))).isEqualTo(ApplyPaymentEventResult.APPLIED);
    }

    @Test
    void eventForUnknownPaymentIsReportedAndNotCounted() {
        UUID paymentId = UUID.randomUUID();
        PaymentEventType type = PaymentEventType.FUNDS_RESERVED;
        when(repository.updateStatus(paymentId, type.targetStatus(), type.applicableFrom(), NOW))
                .thenReturn(new StatusUpdateResult.PaymentNotFound());

        assertThat(service.apply(event(type, paymentId))).isEqualTo(ApplyPaymentEventResult.UNKNOWN_PAYMENT);
        assertThat(meterRegistry.find(ApplyPaymentEventService.CONTRADICTIONS_METRIC).counter()).isNull();
    }

    @Test
    void duplicateEventIsIgnoredWithoutContradiction() {
        UUID paymentId = UUID.randomUUID();
        PaymentEventType type = PaymentEventType.FUNDS_RESERVED;
        when(repository.updateStatus(paymentId, type.targetStatus(), type.applicableFrom(), NOW))
                .thenReturn(new StatusUpdateResult.NotApplied(PaymentStatus.FUNDS_RESERVED));

        assertThat(service.apply(event(type, paymentId))).isEqualTo(ApplyPaymentEventResult.IGNORED);
        assertThat(meterRegistry.find(ApplyPaymentEventService.CONTRADICTIONS_METRIC).counter()).isNull();
    }

    @Test
    void lateExpectedEventInTerminalStatusIsIgnoredWithoutContradiction() {
        UUID paymentId = UUID.randomUUID();
        PaymentEventType type = PaymentEventType.PARTNER_PAYMENT_FAILED;
        when(repository.updateStatus(paymentId, type.targetStatus(), type.applicableFrom(), NOW))
                .thenReturn(new StatusUpdateResult.NotApplied(PaymentStatus.FAILED));

        assertThat(service.apply(event(type, paymentId))).isEqualTo(ApplyPaymentEventResult.IGNORED);
        assertThat(meterRegistry.find(ApplyPaymentEventService.CONTRADICTIONS_METRIC).counter()).isNull();
    }

    @Test
    void contradictingEventIsCountedByTypeAndCurrentStatus() {
        UUID paymentId = UUID.randomUUID();
        PaymentEventType type = PaymentEventType.FUNDS_RESERVATION_FAILED;
        when(repository.updateStatus(paymentId, type.targetStatus(), type.applicableFrom(), NOW))
                .thenReturn(new StatusUpdateResult.NotApplied(PaymentStatus.FUNDS_RESERVED));

        assertThat(service.apply(event(type, paymentId))).isEqualTo(ApplyPaymentEventResult.CONTRADICTION);
        assertThat(contradictions(type, PaymentStatus.FUNDS_RESERVED)).isEqualTo(1.0);
    }

    @Test
    void contradictionWithTerminalOutcomeIsCounted() {
        UUID paymentId = UUID.randomUUID();
        PaymentEventType type = PaymentEventType.FUNDS_CAPTURED;
        when(repository.updateStatus(paymentId, type.targetStatus(), type.applicableFrom(), NOW))
                .thenReturn(new StatusUpdateResult.NotApplied(PaymentStatus.FAILED));

        assertThat(service.apply(event(type, paymentId))).isEqualTo(ApplyPaymentEventResult.CONTRADICTION);
        assertThat(contradictions(type, PaymentStatus.FAILED)).isEqualTo(1.0);
    }

    @Test
    void statusChangedBetweenUpdateAndReadIsReportedAsFailureSoTheEventIsRetried() {
        UUID paymentId = UUID.randomUUID();
        PaymentEventType type = PaymentEventType.FUNDS_RESERVED;
        when(repository.updateStatus(paymentId, type.targetStatus(), type.applicableFrom(), NOW))
                .thenReturn(new StatusUpdateResult.NotApplied(PaymentStatus.ACCEPTED));

        assertThatThrownBy(() -> service.apply(event(type, paymentId)))
                .isInstanceOf(IllegalStateException.class);
    }
}
