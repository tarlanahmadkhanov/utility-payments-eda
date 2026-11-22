package az.tarlan.utility.proxy.adapter.web;

import az.tarlan.utility.proxy.api.model.Payment;
import az.tarlan.utility.proxy.api.model.PaymentStatus;
import az.tarlan.utility.proxy.api.model.ServiceType;

import java.time.ZoneOffset;

final class PaymentApiMapper {

    private PaymentApiMapper() {
    }

    static Payment toApi(az.tarlan.utility.proxy.domain.Payment payment) {
        return new Payment()
                .paymentId(payment.id())
                .status(PaymentStatus.fromValue(payment.status().name()))
                .accountNumber(payment.accountNumber())
                .serviceType(ServiceType.fromValue(payment.serviceType().name()))
                .amountMinor(payment.amountMinor())
                .currency(payment.currency())
                .createdAt(payment.createdAt().atOffset(ZoneOffset.UTC))
                .updatedAt(payment.updatedAt().atOffset(ZoneOffset.UTC));
    }
}
