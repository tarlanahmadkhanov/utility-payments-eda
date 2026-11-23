package az.tarlan.utility.proxy.adapter.web;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static az.tarlan.utility.proxy.adapter.persistence.jooq.Tables.PAYMENTS;
import static org.assertj.core.api.Assertions.assertThat;

// Nothing listens on the broker address, so the synchronous publication fails after the payment is committed.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.kafka.bootstrap-servers=localhost:1",
        "spring.kafka.admin.auto-create=false",
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.producer.properties.max.block.ms=2000",
        "spring.kafka.producer.properties.request.timeout.ms=1000",
        "spring.kafka.producer.properties.delivery.timeout.ms=3000"})
class AcceptPaymentKafkaUnavailableIT extends AbstractWebIT {

    private static final String BODY = """
            {"accountNumber":"AZ-ELEC-0042","serviceType":"ELECTRICITY","amountMinor":2550,"currency":"AZN"}""";

    @Test
    void publicationFailureReturnsGeneric500AndLeavesPaymentAccepted() {
        String customerId = newCustomerId();
        String key = newKey();

        Response response = createPayment(customerId, key, BODY);

        assertThat(response.status()).isEqualTo(500);
        assertThat(response.isProblemJson()).isTrue();
        assertThat(response.<Integer>json("$.status")).isEqualTo(500);
        assertThat(response.body().toLowerCase()).doesNotContain("kafka", "exception", "timeout", "localhost", "broker");
        assertThat(countPayments(customerId)).isEqualTo(1);
        assertThat(dsl.select(PAYMENTS.STATUS).from(PAYMENTS).where(PAYMENTS.CUSTOMER_ID.eq(customerId))
                .fetchOne(PAYMENTS.STATUS)).isEqualTo("ACCEPTED");
    }

    @Test
    void retryAfterPublicationFailureReturnsExistingPaymentWithoutRepublishing() {
        String customerId = newCustomerId();
        String key = newKey();
        assertThat(createPayment(customerId, key, BODY).status()).isEqualTo(500);

        Response retry = createPayment(customerId, key, BODY);

        // A repeated publication attempt would fail with 500 again; 202 shows that none was made (known gap D5).
        assertThat(retry.status()).isEqualTo(202);
        assertThat(retry.<String>json("$.status")).isEqualTo("ACCEPTED");
        assertThat(countPayments(customerId)).isEqualTo(1);
    }
}
