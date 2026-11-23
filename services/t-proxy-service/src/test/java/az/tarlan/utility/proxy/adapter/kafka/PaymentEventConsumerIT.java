package az.tarlan.utility.proxy.adapter.kafka;

import az.tarlan.utility.proxy.adapter.web.AbstractKafkaWebIT;
import az.tarlan.utility.proxy.application.port.PaymentRepository;
import az.tarlan.utility.proxy.domain.Payment;
import az.tarlan.utility.proxy.domain.ServiceType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.TopicPartitionInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import static az.tarlan.utility.proxy.adapter.persistence.jooq.Tables.PAYMENTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@ExtendWith(OutputCaptureExtension.class)
class PaymentEventConsumerIT extends AbstractKafkaWebIT {

    private static final String CONTRADICTIONS_METRIC = "payment.events.contradictions";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    KafkaAdmin kafkaAdmin;

    @Autowired
    PaymentRepository paymentRepository;

    @Autowired
    MeterRegistry meterRegistry;

    @Value("${spring.kafka.consumer.group-id}")
    String groupId;

    private UUID acceptedPayment(String customerId) {
        UUID id = UUID.randomUUID();
        paymentRepository.insert(Payment.accepted(id, customerId, "AZ-ELEC-0042", ServiceType.ELECTRICITY,
                2550, "AZN", Instant.now()));
        return id;
    }

    private static String envelope(String type, UUID paymentId) {
        return """
                {"eventId":"%s","type":"%s","version":1,"correlationId":"%s","causationId":null,\
                "occurredAt":"2026-11-23T08:00:00.000Z","payload":{"paymentId":"%s"}}"""
                .formatted(UUID.randomUUID(), type, paymentId, paymentId);
    }

    private void publish(String topic, UUID paymentId, String value) {
        try {
            kafkaTemplate.send(topic, paymentId.toString(), value).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new IllegalStateException(e);
        }
    }

    private void wallet(String type, UUID paymentId) {
        publish(KafkaTopics.WALLET_EVENTS, paymentId, envelope(type, paymentId));
    }

    private void partner(String type, UUID paymentId) {
        publish(KafkaTopics.PARTNER_EVENTS, paymentId, envelope(type, paymentId));
    }

    private void awaitStatus(UUID paymentId, String status) {
        await().atMost(TIMEOUT).untilAsserted(() ->
                assertThat(storedStatus(paymentId.toString())).isEqualTo(status));
    }

    private void awaitApiStatus(String customerId, UUID paymentId, String status) {
        await().atMost(TIMEOUT).untilAsserted(() ->
                assertThat(getPayment(customerId, paymentId.toString()).<String>json("$.status")).isEqualTo(status));
    }

    // Waits until the consumer group has committed everything published to both inbound topics, so that
    // "nothing changed" assertions are made after the events were actually processed, not before.
    private void awaitConsumed() {
        try (AdminClient admin = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            await().atMost(TIMEOUT).pollInterval(Duration.ofMillis(200)).until(() -> isFullyConsumed(admin));
        }
    }

    private boolean isFullyConsumed(AdminClient admin) throws Exception {
        Map<TopicPartition, OffsetAndMetadata> committed =
                admin.listConsumerGroupOffsets(groupId).partitionsToOffsetAndMetadata().get();
        Map<TopicPartition, OffsetSpec> latest = new HashMap<>();
        for (String topic : List.of(KafkaTopics.WALLET_EVENTS, KafkaTopics.PARTNER_EVENTS)) {
            List<TopicPartitionInfo> partitions = admin.describeTopics(List.of(topic)).allTopicNames().get()
                    .get(topic).partitions();
            partitions.forEach(info -> latest.put(new TopicPartition(topic, info.partition()), OffsetSpec.latest()));
        }
        return admin.listOffsets(latest).all().get().entrySet().stream().allMatch(entry -> {
            OffsetAndMetadata offset = committed.get(entry.getKey());
            return (offset == null ? 0 : offset.offset()) >= entry.getValue().offset();
        });
    }

    private OffsetDateTime updatedAt(UUID paymentId) {
        return dsl.select(PAYMENTS.UPDATED_AT).from(PAYMENTS).where(PAYMENTS.ID.eq(paymentId))
                .fetchOne(PAYMENTS.UPDATED_AT);
    }

    private double contradictions(String eventType, String currentStatus) {
        Counter counter = meterRegistry.find(CONTRADICTIONS_METRIC)
                .tag("eventType", eventType)
                .tag("currentStatus", currentStatus)
                .counter();
        return counter == null ? 0 : counter.count();
    }

    private static int occurrences(CapturedOutput output, String text) {
        return output.getAll().split(java.util.regex.Pattern.quote(text), -1).length - 1;
    }

    @Test
    void successChainIsVisibleThroughGet() {
        String customerId = newCustomerId();
        UUID id = acceptedPayment(customerId);
        assertThat(getPayment(customerId, id.toString()).<String>json("$.status")).isEqualTo("ACCEPTED");

        wallet("FundsReserved", id);
        awaitApiStatus(customerId, id, "FUNDS_RESERVED");
        partner("PartnerPaymentConfirmed", id);
        awaitApiStatus(customerId, id, "PARTNER_CONFIRMED");
        wallet("FundsCaptured", id);
        awaitApiStatus(customerId, id, "COMPLETED");

        Response finalResponse = getPayment(customerId, id.toString());
        assertThat(finalResponse.headers().getFirst("Retry-After")).isNull();
    }

    @Test
    void partnerRejectionGoesThroughCompensatingToFailed() {
        UUID id = acceptedPayment(newCustomerId());

        wallet("FundsReserved", id);
        awaitStatus(id, "FUNDS_RESERVED");
        partner("PartnerPaymentFailed", id);
        awaitStatus(id, "COMPENSATING");
        wallet("FundsReleased", id);
        awaitStatus(id, "FAILED");
    }

    @Test
    void reservationFailureFailsThePaymentImmediately() {
        UUID id = acceptedPayment(newCustomerId());

        wallet("FundsReservationFailed", id);

        awaitStatus(id, "FAILED");
    }

    @Test
    void unknownPartnerOutcomeMovesPaymentToUnknown() {
        UUID id = acceptedPayment(newCustomerId());

        wallet("FundsReserved", id);
        awaitStatus(id, "FUNDS_RESERVED");
        partner("PartnerPaymentOutcomeUnknown", id);

        awaitStatus(id, "UNKNOWN");
    }

    @Test
    void duplicateEventChangesNothing() {
        UUID id = acceptedPayment(newCustomerId());
        wallet("FundsReserved", id);
        awaitStatus(id, "FUNDS_RESERVED");
        OffsetDateTime updatedAfterFirst = updatedAt(id);
        double before = contradictions("FundsReserved", "FUNDS_RESERVED");

        wallet("FundsReserved", id);
        awaitConsumed();

        assertThat(storedStatus(id.toString())).isEqualTo("FUNDS_RESERVED");
        assertThat(updatedAt(id)).isEqualTo(updatedAfterFirst);
        assertThat(contradictions("FundsReserved", "FUNDS_RESERVED")).isEqualTo(before);
    }

    // The two topics are independent, so FundsReleased can overtake PartnerPaymentFailed.
    @Test
    void fundsReleasedBeforePartnerPaymentFailedFailsThePaymentAndLateEventIsIgnored() {
        UUID id = acceptedPayment(newCustomerId());
        wallet("FundsReserved", id);
        awaitStatus(id, "FUNDS_RESERVED");

        wallet("FundsReleased", id);
        awaitStatus(id, "FAILED");
        OffsetDateTime updatedAfterFailure = updatedAt(id);
        double before = contradictions("PartnerPaymentFailed", "FAILED");
        partner("PartnerPaymentFailed", id);
        awaitConsumed();

        assertThat(storedStatus(id.toString())).isEqualTo("FAILED");
        assertThat(updatedAt(id)).isEqualTo(updatedAfterFailure);
        assertThat(contradictions("PartnerPaymentFailed", "FAILED")).isEqualTo(before);
    }

    @Test
    void partnerConfirmationBeforeReservationJumpsForwardAndLateReservationIsIgnored() {
        UUID id = acceptedPayment(newCustomerId());

        partner("PartnerPaymentConfirmed", id);
        awaitStatus(id, "PARTNER_CONFIRMED");
        OffsetDateTime updatedAfterJump = updatedAt(id);
        wallet("FundsReserved", id);
        awaitConsumed();

        assertThat(storedStatus(id.toString())).isEqualTo("PARTNER_CONFIRMED");
        assertThat(updatedAt(id)).isEqualTo(updatedAfterJump);
    }

    @Test
    void contradictingEventIsIgnoredWithWarningAndMetric(CapturedOutput output) {
        UUID id = acceptedPayment(newCustomerId());
        wallet("FundsReserved", id);
        awaitStatus(id, "FUNDS_RESERVED");
        double before = contradictions("FundsReservationFailed", "FUNDS_RESERVED");

        wallet("FundsReservationFailed", id);

        await().atMost(TIMEOUT).untilAsserted(() ->
                assertThat(contradictions("FundsReservationFailed", "FUNDS_RESERVED")).isEqualTo(before + 1));
        assertThat(storedStatus(id.toString())).isEqualTo("FUNDS_RESERVED");
        assertThat(output.getAll()).contains("Contradicting event FundsReservationFailed")
                .contains("for payment " + id);
    }

    @Test
    void eventContradictingTerminalOutcomeIsIgnoredAndCounted() {
        UUID id = acceptedPayment(newCustomerId());
        wallet("FundsCaptured", id);
        awaitStatus(id, "COMPLETED");
        double before = contradictions("FundsReservationFailed", "COMPLETED");

        wallet("FundsReservationFailed", id);

        await().atMost(TIMEOUT).untilAsserted(() ->
                assertThat(contradictions("FundsReservationFailed", "COMPLETED")).isEqualTo(before + 1));
        assertThat(storedStatus(id.toString())).isEqualTo("COMPLETED");
    }

    @Test
    void eventForUnknownPaymentIsLoggedAndConsumerKeepsWorking(CapturedOutput output) {
        UUID unknownId = UUID.randomUUID();
        UUID id = acceptedPayment(newCustomerId());

        wallet("FundsReserved", unknownId);
        wallet("FundsReserved", id);

        awaitStatus(id, "FUNDS_RESERVED");
        await().atMost(TIMEOUT).untilAsserted(() ->
                assertThat(output.getAll()).contains("for unknown payment " + unknownId));
    }

    @Test
    void messageWithUnknownFieldsAndExtraPayloadDataIsProcessed() {
        UUID id = acceptedPayment(newCustomerId());
        String value = """
                {"eventId":"%s","type":"FundsReserved","version":2,"futureField":{"a":1},\
                "payload":{"paymentId":"%s","amountMinor":2550,"currency":"AZN","extra":[1,2]}}"""
                .formatted(UUID.randomUUID(), id);

        publish(KafkaTopics.WALLET_EVENTS, id, value);

        awaitStatus(id, "FUNDS_RESERVED");
    }

    @Test
    void eventOfUnknownTypeIsSkippedAndConsumerKeepsWorking(CapturedOutput output) {
        UUID id = acceptedPayment(newCustomerId());
        int before = occurrences(output, "Ignoring event of unknown type SomethingNew");

        wallet("SomethingNew", id);
        wallet("FundsReserved", id);

        awaitStatus(id, "FUNDS_RESERVED");
        assertThat(occurrences(output, "Ignoring event of unknown type SomethingNew")).isGreaterThan(before);
    }

    static Stream<Arguments> invalidMessages() {
        return Stream.of(
                Arguments.of("not json", "not json"),
                Arguments.of("empty string", ""),
                Arguments.of("json array", "[]"),
                Arguments.of("empty object", "{}"),
                Arguments.of("missing paymentId", "{\"type\":\"FundsReserved\",\"payload\":{}}"),
                Arguments.of("paymentId is not a uuid", "{\"type\":\"FundsReserved\",\"payload\":{\"paymentId\":\"abc\"}}"),
                Arguments.of("tombstone", null));
    }

    // The invalid message and the valid one share a key, hence a partition: the valid one is processed
    // strictly after the invalid one, so a stuck consumer would show up as a timeout here.
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidMessages")
    void invalidMessageIsSkippedWithErrorAndConsumerKeepsWorking(String description, String value,
                                                                  CapturedOutput output) {
        UUID id = acceptedPayment(newCustomerId());
        int before = occurrences(output, "Skipping invalid message");

        publish(KafkaTopics.WALLET_EVENTS, id, value);
        wallet("FundsReserved", id);

        awaitStatus(id, "FUNDS_RESERVED");
        assertThat(occurrences(output, "Skipping invalid message")).isGreaterThan(before);
    }
}
