package az.tarlan.utility.proxy.adapter.web;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class AcceptPaymentIT extends AbstractKafkaWebIT {

    private static final Duration EVENT_WINDOW = Duration.ofSeconds(2);

    private static final String BODY = """
            {"accountNumber":"AZ-ELEC-0042","serviceType":"ELECTRICITY","amountMinor":2550,"currency":"AZN"}""";

    private static String body(String account, String service, String amount, String currency) {
        return """
                {"accountNumber":%s,"serviceType":%s,"amountMinor":%s,"currency":%s}"""
                .formatted(account, service, amount, currency);
    }

    private List<String> eventsOf(String paymentId) {
        return readPaymentEvents(EVENT_WINDOW).getOrDefault(paymentId, List.of());
    }

    @Test
    void newPaymentIsAcceptedAndPaymentRequestedIsPublished() {
        String customerId = newCustomerId();

        Response response = createPayment(customerId, newKey(), BODY);

        assertThat(response.status()).isEqualTo(202);
        String paymentId = response.json("$.paymentId");
        assertThat(response.headers().getFirst(HttpHeaders.LOCATION)).isEqualTo("/payments/" + paymentId);
        assertThat(response.headers().getFirst(HttpHeaders.RETRY_AFTER)).isNotNull();
        assertThat(response.<String>json("$.status")).isEqualTo("ACCEPTED");
        assertThat(response.<String>json("$.accountNumber")).isEqualTo("AZ-ELEC-0042");
        assertThat(response.<String>json("$.serviceType")).isEqualTo("ELECTRICITY");
        assertThat(response.<Integer>json("$.amountMinor")).isEqualTo(2550);
        assertThat(response.<String>json("$.currency")).isEqualTo("AZN");
        assertThat(storedStatus(paymentId)).isEqualTo("ACCEPTED");

        List<String> events = eventsOf(paymentId);
        assertThat(events).hasSize(1);
        String event = events.getFirst();
        assertThat(JsonPath.<String>read(event, "$.type")).isEqualTo("PaymentRequested");
        assertThat(JsonPath.<Integer>read(event, "$.version")).isEqualTo(1);
        assertThat(JsonPath.<String>read(event, "$.correlationId")).isEqualTo(paymentId);
        assertThat(JsonPath.<Object>read(event, "$.causationId")).isNull();
        assertThat(UUID.fromString(JsonPath.read(event, "$.eventId"))).isNotNull();
        assertThat(Instant.parse(JsonPath.<String>read(event, "$.occurredAt")))
                .isEqualTo(OffsetDateTime.parse(response.json("$.createdAt")).toInstant());
        assertThat(JsonPath.<String>read(event, "$.payload.paymentId")).isEqualTo(paymentId);
        assertThat(JsonPath.<String>read(event, "$.payload.customerId")).isEqualTo(customerId);
        assertThat(JsonPath.<String>read(event, "$.payload.accountNumber")).isEqualTo("AZ-ELEC-0042");
        assertThat(JsonPath.<String>read(event, "$.payload.serviceType")).isEqualTo("ELECTRICITY");
        assertThat(JsonPath.<Integer>read(event, "$.payload.amountMinor")).isEqualTo(2550);
        assertThat(JsonPath.<String>read(event, "$.payload.currency")).isEqualTo("AZN");
    }

    @Test
    void repeatedKeyWithSameBodyReturnsSamePaymentWithoutSecondEvent() {
        String customerId = newCustomerId();
        String key = newKey();

        Response first = createPayment(customerId, key, BODY);
        Response second = createPayment(customerId, key, BODY);

        assertThat(first.status()).isEqualTo(202);
        assertThat(second.status()).isEqualTo(202);
        assertThat(second.<String>json("$.paymentId")).isEqualTo(first.<String>json("$.paymentId"));
        assertThat(second.headers().getFirst(HttpHeaders.LOCATION)).isEqualTo(first.headers().getFirst(HttpHeaders.LOCATION));
        assertThat(countPayments(customerId)).isEqualTo(1);
        assertThat(eventsOf(first.json("$.paymentId"))).hasSize(1);
    }

    @Test
    void repeatedKeyWithReformattedBodyIsStillTheSameRequest() {
        String customerId = newCustomerId();
        String key = newKey();
        String reformatted = """
                {
                  "currency" : "AZN",
                  "amountMinor" : 2550,
                  "serviceType" : "ELECTRICITY",
                  "accountNumber" : "AZ-ELEC-0042"
                }""";

        Response first = createPayment(customerId, key, BODY);
        Response second = createPayment(customerId, key, reformatted);

        assertThat(second.status()).isEqualTo(202);
        assertThat(second.<String>json("$.paymentId")).isEqualTo(first.<String>json("$.paymentId"));
        assertThat(countPayments(customerId)).isEqualTo(1);
    }

    @Test
    void sameKeyWithDifferentBodyIsRejectedWith422() {
        String customerId = newCustomerId();
        String key = newKey();
        Response first = createPayment(customerId, key, BODY);

        Response second = createPayment(customerId, key, BODY.replace("2550", "2551"));

        assertThat(second.status()).isEqualTo(422);
        assertThat(second.isProblemJson()).isTrue();
        assertThat(second.<Integer>json("$.status")).isEqualTo(422);
        assertThat(countPayments(customerId)).isEqualTo(1);
        assertThat(storedStatus(first.json("$.paymentId"))).isEqualTo("ACCEPTED");
        assertThat(eventsOf(first.json("$.paymentId"))).hasSize(1);
    }

    @Test
    void sameKeyOfDifferentCustomersCreatesIndependentPayments() {
        String key = newKey();

        Response first = createPayment(newCustomerId(), key, BODY);
        Response second = createPayment(newCustomerId(), key, BODY);

        assertThat(first.status()).isEqualTo(202);
        assertThat(second.status()).isEqualTo(202);
        assertThat(second.<String>json("$.paymentId")).isNotEqualTo(first.<String>json("$.paymentId"));
        assertThat(countPayments()).isEqualTo(2);
    }

    @Test
    void concurrentIdenticalRequestsYieldOnePaymentAndOneEvent() throws Exception {
        int rounds = 10;
        String customerId = newCustomerId();
        List<String> paymentIds = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < rounds; i++) {
                String key = newKey();
                CyclicBarrier barrier = new CyclicBarrier(2);
                Callable<Response> call = () -> {
                    barrier.await(30, TimeUnit.SECONDS);
                    return createPayment(customerId, key, BODY);
                };
                Future<Response> first = pool.submit(call);
                Future<Response> second = pool.submit(call);

                Response a = first.get(60, TimeUnit.SECONDS);
                Response b = second.get(60, TimeUnit.SECONDS);

                assertThat(a.status()).isEqualTo(202);
                assertThat(b.status()).isEqualTo(202);
                assertThat(a.<String>json("$.paymentId")).isEqualTo(b.<String>json("$.paymentId"));
                paymentIds.add(a.json("$.paymentId"));
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(countPayments(customerId)).isEqualTo(rounds);
        Map<String, List<String>> events = readPaymentEvents(EVENT_WINDOW);
        assertThat(paymentIds).allSatisfy(id -> assertThat(events.getOrDefault(id, List.of())).hasSize(1));
    }

    static Stream<Arguments> invalidRequests() {
        String customer = "customer-invalid";
        String key = "key-invalid";
        return Stream.of(
                Arguments.of("missing Idempotency-Key", customer, null, BODY),
                Arguments.of("missing X-Customer-Id", null, key, BODY),
                Arguments.of("malformed JSON", customer, key, "{"),
                Arguments.of("missing field", customer, key,
                        "{\"accountNumber\":\"AZ-1\",\"serviceType\":\"WATER\",\"amountMinor\":100}"),
                Arguments.of("lowercase serviceType", customer, key, body("\"AZ-1\"", "\"water\"", "100", "\"AZN\"")),
                Arguments.of("unknown serviceType", customer, key, body("\"AZ-1\"", "\"SEWAGE\"", "100", "\"AZN\"")),
                Arguments.of("zero amount", customer, key, body("\"AZ-1\"", "\"WATER\"", "0", "\"AZN\"")),
                Arguments.of("negative amount", customer, key, body("\"AZ-1\"", "\"WATER\"", "-5", "\"AZN\"")),
                Arguments.of("fractional amount", customer, key, body("\"AZ-1\"", "\"WATER\"", "10.5", "\"AZN\"")),
                Arguments.of("lowercase currency", customer, key, body("\"AZ-1\"", "\"WATER\"", "100", "\"azn\"")),
                Arguments.of("empty accountNumber", customer, key, body("\"\"", "\"WATER\"", "100", "\"AZN\"")),
                Arguments.of("leading space in accountNumber", customer, key,
                        body("\" AZ-1\"", "\"WATER\"", "100", "\"AZN\"")),
                Arguments.of("trailing space in accountNumber", customer, key,
                        body("\"AZ-1 \"", "\"WATER\"", "100", "\"AZN\"")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRequests")
    void invalidRequestIsRejectedWith400AndNothingIsStored(String description, String customerId, String key,
                                                          String body) {
        Response response = createPayment(customerId, key, body);

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.isProblemJson()).isTrue();
        assertThat(response.<Integer>json("$.status")).isEqualTo(400);
        assertThat(countPayments()).isZero();
    }

    @Test
    void createdPaymentCanBeReadByItsOwner() {
        String customerId = newCustomerId();
        String paymentId = createPayment(customerId, newKey(), BODY).json("$.paymentId");

        Response response = getPayment(customerId, paymentId);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.<String>json("$.paymentId")).isEqualTo(paymentId);
        assertThat(response.<String>json("$.status")).isEqualTo("ACCEPTED");
        assertThat(response.headers().getFirst(HttpHeaders.RETRY_AFTER)).isNotNull();
    }

    @Test
    void paymentOfAnotherCustomerIsReportedAsNotFound() {
        String paymentId = createPayment(newCustomerId(), newKey(), BODY).json("$.paymentId");

        Response response = getPayment(newCustomerId(), paymentId);

        assertThat(response.status()).isEqualTo(404);
        assertThat(response.isProblemJson()).isTrue();
    }

    @Test
    void malformedPaymentIdIsRejectedWith400() {
        Response response = getPayment(newCustomerId(), "not-a-uuid");

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.isProblemJson()).isTrue();
    }
}
