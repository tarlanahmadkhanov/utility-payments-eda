package az.tarlan.utility.proxy.application.port;

import az.tarlan.utility.proxy.domain.Payment;

public interface PaymentEventPublisher {

    void publishPaymentRequested(Payment payment);
}
