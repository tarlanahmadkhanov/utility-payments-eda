package az.tarlan.utility.proxy.adapter.web;

import az.tarlan.utility.proxy.application.port.PaymentRepository;
import az.tarlan.utility.proxy.domain.Payment;
import az.tarlan.utility.proxy.domain.PaymentStatus;
import az.tarlan.utility.proxy.domain.ServiceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static az.tarlan.utility.proxy.adapter.persistence.jooq.Tables.PAYMENTS;
import static org.assertj.core.api.Assertions.assertThat;

class GetPaymentIT extends AbstractWebIT {

    @Autowired
    PaymentRepository paymentRepository;

    private UUID paymentWithStatus(String customerId, PaymentStatus status) {
        UUID id = UUID.randomUUID();
        paymentRepository.insert(Payment.accepted(id, customerId, "AZ-ELEC-0042", ServiceType.ELECTRICITY,
                2550, "AZN", Instant.parse("2026-11-23T08:00:00Z")));
        dsl.update(PAYMENTS).set(PAYMENTS.STATUS, status.name()).where(PAYMENTS.ID.eq(id)).execute();
        return id;
    }

    // Retry-After is only a polling hint: present for every non-terminal status, absent once the payment is final.
    @ParameterizedTest
    @EnumSource(PaymentStatus.class)
    void everyStatusIsReturnedAndRetryAfterOnlyForNonTerminalOnes(PaymentStatus status) {
        String customerId = newCustomerId();
        UUID id = paymentWithStatus(customerId, status);

        Response response = getPayment(customerId, id.toString());

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.<String>json("$.status")).isEqualTo(status.name());
        String retryAfter = response.headers().getFirst("Retry-After");
        if (status.isTerminal()) {
            assertThat(retryAfter).isNull();
        } else {
            assertThat(retryAfter).isEqualTo("1");
        }
    }

    @Test
    void responseCarriesAllPaymentFields() {
        String customerId = newCustomerId();
        UUID id = paymentWithStatus(customerId, PaymentStatus.FUNDS_RESERVED);

        Response response = getPayment(customerId, id.toString());

        assertThat(response.<String>json("$.paymentId")).isEqualTo(id.toString());
        assertThat(response.<String>json("$.accountNumber")).isEqualTo("AZ-ELEC-0042");
        assertThat(response.<String>json("$.serviceType")).isEqualTo("ELECTRICITY");
        assertThat(response.<Integer>json("$.amountMinor")).isEqualTo(2550);
        assertThat(response.<String>json("$.currency")).isEqualTo("AZN");
        assertThat(response.<String>json("$.createdAt")).isNotBlank();
        assertThat(response.<String>json("$.updatedAt")).isNotBlank();
    }

    @Test
    void unknownPaymentIsReportedAsNotFoundProblem() {
        Response response = getPayment(newCustomerId(), UUID.randomUUID().toString());

        assertThat(response.status()).isEqualTo(404);
        assertThat(response.isProblemJson()).isTrue();
    }

    @Test
    void missingCustomerHeaderIsRejectedWith400() {
        Response response = send(HttpMethod.GET, "/payments/" + UUID.randomUUID(), Map.of(), null);

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.isProblemJson()).isTrue();
    }
}
