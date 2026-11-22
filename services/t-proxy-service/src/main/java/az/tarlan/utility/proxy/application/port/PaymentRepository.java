package az.tarlan.utility.proxy.application.port;

import az.tarlan.utility.proxy.domain.Payment;
import az.tarlan.utility.proxy.domain.PaymentStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface PaymentRepository {

    void insert(Payment payment);

    Optional<Payment> findByIdAndCustomerId(UUID id, String customerId);

    StatusUpdateResult updateStatus(UUID paymentId, PaymentStatus target, Set<PaymentStatus> allowedFrom,
                                    Instant updatedAt);
}
