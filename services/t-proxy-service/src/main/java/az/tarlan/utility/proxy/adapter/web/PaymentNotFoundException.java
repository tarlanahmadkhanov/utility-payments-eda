package az.tarlan.utility.proxy.adapter.web;

import java.util.UUID;

public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(UUID paymentId) {
        super("Payment " + paymentId + " not found");
    }
}
