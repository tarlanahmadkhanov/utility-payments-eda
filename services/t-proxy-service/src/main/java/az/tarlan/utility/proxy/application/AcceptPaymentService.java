package az.tarlan.utility.proxy.application;

import az.tarlan.utility.proxy.application.port.PaymentEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class AcceptPaymentService {

    private final PaymentRegistrar registrar;
    private final PaymentEventPublisher publisher;

    public AcceptPaymentService(PaymentRegistrar registrar, PaymentEventPublisher publisher) {
        this.registrar = registrar;
        this.publisher = publisher;
    }

    public AcceptPaymentResult accept(AcceptPaymentCommand command) {
        String requestHash = RequestFingerprint.of(command);
        return switch (registrar.register(command, requestHash)) {
            case RegistrationResult.Created created -> {
                publisher.publishPaymentRequested(created.payment());
                yield new AcceptPaymentResult.Accepted(created.payment());
            }
            case RegistrationResult.Existing existing -> new AcceptPaymentResult.Accepted(existing.payment());
            case RegistrationResult.Conflict conflict -> new AcceptPaymentResult.IdempotencyKeyReused();
        };
    }
}
