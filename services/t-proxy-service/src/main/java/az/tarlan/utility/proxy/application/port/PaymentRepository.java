package az.tarlan.utility.proxy.application.port;

import az.tarlan.utility.proxy.domain.Payment;
import az.tarlan.utility.proxy.domain.PaymentStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Storage of payments. Implemented in the persistence adapter on top of jOOQ. */
public interface PaymentRepository {

    /** Inserts a new payment. */
    void insert(Payment payment);

    /**
     * Finds a payment of the given customer. A payment of another customer is reported as absent
     * (the API answers {@code 404}, so it never reveals that the payment exists).
     */
    Optional<Payment> findByIdAndCustomerId(UUID id, String customerId);

    /**
     * Conditional status update: changes the status to {@code target} only if the current status is one of
     * {@code allowedFrom}, in a single atomic statement. Terminal statuses are never changed, because the caller
     * never includes them in {@code allowedFrom}.
     *
     * @param updatedAt value for {@code updated_at} when the status is changed
     */
    StatusUpdateResult updateStatus(UUID paymentId, PaymentStatus target, Set<PaymentStatus> allowedFrom,
                                    Instant updatedAt);
}
