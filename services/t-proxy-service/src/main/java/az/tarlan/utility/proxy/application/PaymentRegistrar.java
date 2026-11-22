package az.tarlan.utility.proxy.application;

import az.tarlan.utility.proxy.application.port.IdempotencyKeyRepository;
import az.tarlan.utility.proxy.application.port.PaymentRepository;
import az.tarlan.utility.proxy.domain.IdempotencyKey;
import az.tarlan.utility.proxy.domain.Payment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

@Component
public class PaymentRegistrar {

    private final PaymentRepository payments;
    private final IdempotencyKeyRepository idempotencyKeys;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public PaymentRegistrar(PaymentRepository payments, IdempotencyKeyRepository idempotencyKeys,
                            PlatformTransactionManager transactionManager, Clock clock) {
        this.payments = payments;
        this.idempotencyKeys = idempotencyKeys;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public RegistrationResult register(AcceptPaymentCommand command, String requestHash) {
        return Objects.requireNonNull(transaction.execute(status -> registerInTransaction(command, requestHash, status)));
    }

    private RegistrationResult registerInTransaction(AcceptPaymentCommand command, String requestHash,
                                                     TransactionStatus status) {
        var known = idempotencyKeys.find(command.customerId(), command.idempotencyKey());
        if (known.isPresent()) {
            return resolveExisting(known.get(), requestHash);
        }

        Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        Payment payment = Payment.accepted(UUID.randomUUID(), command.customerId(), command.accountNumber(),
                command.serviceType(), command.amountMinor(), command.currency(), now);
        payments.insert(payment);

        IdempotencyKey key = new IdempotencyKey(command.customerId(), command.idempotencyKey(), requestHash,
                payment.id(), now);
        if (idempotencyKeys.insertIfAbsent(key)) {
            return new RegistrationResult.Created(payment);
        }

        status.setRollbackOnly();
        IdempotencyKey winner = idempotencyKeys.find(command.customerId(), command.idempotencyKey())
                .orElseThrow(() -> new IllegalStateException("Idempotency key vanished after a conflict"));
        return resolveExisting(winner, requestHash);
    }

    private RegistrationResult resolveExisting(IdempotencyKey known, String requestHash) {
        if (!known.requestHash().equals(requestHash)) {
            return new RegistrationResult.Conflict();
        }
        Payment payment = payments.findByIdAndCustomerId(known.paymentId(), known.customerId())
                .orElseThrow(() -> new IllegalStateException(
                        "Payment " + known.paymentId() + " referenced by an idempotency key is missing"));
        return new RegistrationResult.Existing(payment);
    }
}
