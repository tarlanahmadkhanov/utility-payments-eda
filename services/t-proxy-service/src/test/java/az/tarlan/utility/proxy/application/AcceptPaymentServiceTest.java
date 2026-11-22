package az.tarlan.utility.proxy.application;

import az.tarlan.utility.proxy.application.port.PaymentEventPublisher;
import az.tarlan.utility.proxy.domain.Payment;
import az.tarlan.utility.proxy.domain.ServiceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AcceptPaymentServiceTest {

    private static final AcceptPaymentCommand COMMAND = new AcceptPaymentCommand(
            "customer-1", "key-1", "AZ-ELEC-0042", ServiceType.ELECTRICITY, 2550, "AZN");

    @Mock
    PaymentRegistrar registrar;

    @Mock
    PaymentEventPublisher publisher;

    @InjectMocks
    AcceptPaymentService service;

    private static Payment payment() {
        return Payment.accepted(UUID.randomUUID(), "customer-1", "AZ-ELEC-0042", ServiceType.ELECTRICITY,
                2550, "AZN", Instant.parse("2026-11-22T17:48:31.512Z"));
    }

    @Test
    void createdPaymentIsPublishedAndAccepted() {
        Payment payment = payment();
        when(registrar.register(eq(COMMAND), any())).thenReturn(new RegistrationResult.Created(payment));

        AcceptPaymentResult result = service.accept(COMMAND);

        assertThat(result).isEqualTo(new AcceptPaymentResult.Accepted(payment));
        verify(publisher).publishPaymentRequested(payment);
    }

    @Test
    void existingPaymentIsAcceptedWithoutPublishing() {
        Payment payment = payment();
        when(registrar.register(eq(COMMAND), any())).thenReturn(new RegistrationResult.Existing(payment));

        AcceptPaymentResult result = service.accept(COMMAND);

        assertThat(result).isEqualTo(new AcceptPaymentResult.Accepted(payment));
        verifyNoInteractions(publisher);
    }

    @Test
    void conflictReportsKeyReuseWithoutPublishing() {
        when(registrar.register(eq(COMMAND), any())).thenReturn(new RegistrationResult.Conflict());

        AcceptPaymentResult result = service.accept(COMMAND);

        assertThat(result).isInstanceOf(AcceptPaymentResult.IdempotencyKeyReused.class);
        verifyNoInteractions(publisher);
    }

    @Test
    void publicationFailurePropagatesAfterRegistration() {
        Payment payment = payment();
        when(registrar.register(eq(COMMAND), any())).thenReturn(new RegistrationResult.Created(payment));
        doThrow(new EventPublicationException("broker down", new RuntimeException()))
                .when(publisher).publishPaymentRequested(payment);

        assertThatThrownBy(() -> service.accept(COMMAND)).isInstanceOf(EventPublicationException.class);
        verify(registrar).register(eq(COMMAND), any());
    }

    @Test
    void registrarReceivesFingerprintOfTheCommand() {
        when(registrar.register(eq(COMMAND), any())).thenReturn(new RegistrationResult.Conflict());
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);

        service.accept(COMMAND);

        verify(registrar).register(eq(COMMAND), hash.capture());
        assertThat(hash.getValue()).isEqualTo(RequestFingerprint.of(COMMAND));
        verify(publisher, never()).publishPaymentRequested(any());
    }
}
