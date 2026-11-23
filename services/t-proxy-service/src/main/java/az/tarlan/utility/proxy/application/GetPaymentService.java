package az.tarlan.utility.proxy.application;

import az.tarlan.utility.proxy.application.port.PaymentRepository;
import az.tarlan.utility.proxy.domain.Payment;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class GetPaymentService {

    private final PaymentRepository paymentRepository;

    public GetPaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    public Optional<Payment> find(UUID paymentId, String customerId) {
        return paymentRepository.findByIdAndCustomerId(paymentId, customerId);
    }
}
