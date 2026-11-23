package az.tarlan.utility.proxy.adapter.web;

import com.jayway.jsonpath.JsonPath;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static az.tarlan.utility.proxy.adapter.persistence.jooq.Tables.IDEMPOTENCY_KEYS;
import static az.tarlan.utility.proxy.adapter.persistence.jooq.Tables.PAYMENTS;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.kafka.admin.auto-create=false", "spring.kafka.listener.auto-startup=false"})
public abstract class AbstractWebIT {

    // One PostgreSQL for the whole test run (see AbstractPersistenceIT for why).
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    static {
        POSTGRES.start();
    }

    @Value("${local.server.port}")
    private int port;

    @Autowired
    protected DSLContext dsl;

    @BeforeEach
    void cleanDatabase() {
        dsl.deleteFrom(IDEMPOTENCY_KEYS).execute();
        dsl.deleteFrom(PAYMENTS).execute();
    }

    protected record Response(int status, HttpHeaders headers, String body) {

        public <T> T json(String path) {
            return JsonPath.read(body, path);
        }

        public boolean isProblemJson() {
            MediaType contentType = headers.getContentType();
            return contentType != null && MediaType.APPLICATION_PROBLEM_JSON.isCompatibleWith(contentType);
        }
    }

    protected Response send(HttpMethod method, String path, Map<String, String> headers, String body) {
        RestClient.RequestBodySpec request = RestClient.create("http://localhost:" + port)
                .method(method)
                .uri(path);
        headers.forEach(request::header);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).body(body);
        }
        return request.exchange((req, res) -> new Response(
                res.getStatusCode().value(),
                res.getHeaders(),
                StreamUtils.copyToString(res.getBody(), StandardCharsets.UTF_8)));
    }

    protected Response get(String path) {
        return send(HttpMethod.GET, path, Map.of(), null);
    }

    protected Response getPayment(String customerId, String paymentId) {
        return send(HttpMethod.GET, "/payments/" + paymentId, Map.of("X-Customer-Id", customerId), null);
    }

    protected Response createPayment(String customerId, String idempotencyKey, String body) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (customerId != null) {
            headers.put("X-Customer-Id", customerId);
        }
        if (idempotencyKey != null) {
            headers.put("Idempotency-Key", idempotencyKey);
        }
        return send(HttpMethod.POST, "/payments", headers, body);
    }

    protected static String newCustomerId() {
        return "customer-" + UUID.randomUUID();
    }

    protected static String newKey() {
        return UUID.randomUUID().toString();
    }

    protected int countPayments() {
        return dsl.fetchCount(PAYMENTS);
    }

    protected int countPayments(String customerId) {
        return dsl.fetchCount(PAYMENTS, PAYMENTS.CUSTOMER_ID.eq(customerId));
    }

    protected String storedStatus(String paymentId) {
        return dsl.select(PAYMENTS.STATUS).from(PAYMENTS)
                .where(PAYMENTS.ID.eq(UUID.fromString(paymentId)))
                .fetchOne(PAYMENTS.STATUS);
    }
}
