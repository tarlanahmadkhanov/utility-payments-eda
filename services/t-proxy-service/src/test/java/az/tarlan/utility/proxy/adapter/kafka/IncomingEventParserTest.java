package az.tarlan.utility.proxy.adapter.kafka;

import az.tarlan.utility.proxy.application.IncomingPaymentEvent;
import az.tarlan.utility.proxy.domain.PaymentEventType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IncomingEventParserTest {

    private static final UUID PAYMENT_ID = UUID.fromString("5c1e9d3a-7b24-4f6e-a1c8-90e5b3d7f412");
    private static final String EVENT_ID = "0b7f4a52-3c1d-4e8a-9b6f-2d1c7e9a8f10";

    private final IncomingEventParser parser = new IncomingEventParser(JsonMapper.builder().build());

    private static String message(String type) {
        return """
                {"eventId":"%s","type":"%s","version":1,"correlationId":"%s","causationId":null,\
                "occurredAt":"2026-11-23T08:00:00.000Z","payload":{"paymentId":"%s"}}"""
                .formatted(EVENT_ID, type, PAYMENT_ID, PAYMENT_ID);
    }

    @ParameterizedTest
    @EnumSource(PaymentEventType.class)
    void everyKnownEventTypeIsParsed(PaymentEventType type) {
        Optional<IncomingPaymentEvent> parsed = parser.parse(message(type.typeName()));

        assertThat(parsed).contains(new IncomingPaymentEvent(EVENT_ID, type, PAYMENT_ID));
    }

    @Test
    void unknownFieldsAreIgnored() {
        String value = """
                {"type":"FundsReserved","futureField":{"a":1},"payload":{"paymentId":"%s","amountMinor":2550,"extra":[1,2]}}"""
                .formatted(PAYMENT_ID);

        Optional<IncomingPaymentEvent> parsed = parser.parse(value);

        assertThat(parsed).contains(new IncomingPaymentEvent(null, PaymentEventType.FUNDS_RESERVED, PAYMENT_ID));
    }

    @Test
    void unknownEventTypeIsSkippedEvenWithoutPayload() {
        assertThat(parser.parse("""
                {"type":"SomethingNew"}""")).isEmpty();
    }

    static Stream<Arguments> invalidMessages() {
        return Stream.of(
                Arguments.of("null value", null),
                Arguments.of("empty string", ""),
                Arguments.of("blank string", "   "),
                Arguments.of("not json", "not json"),
                Arguments.of("truncated json", "{\"type\":\"FundsReserved\""),
                Arguments.of("json array", "[]"),
                Arguments.of("json string", "\"text\""),
                Arguments.of("empty object", "{}"),
                Arguments.of("type is not a string", "{\"type\":1,\"payload\":{\"paymentId\":\"" + PAYMENT_ID + "\"}}"),
                Arguments.of("blank type", "{\"type\":\" \",\"payload\":{\"paymentId\":\"" + PAYMENT_ID + "\"}}"),
                Arguments.of("missing payload", "{\"type\":\"FundsReserved\"}"),
                Arguments.of("payload is not an object", "{\"type\":\"FundsReserved\",\"payload\":\"x\"}"),
                Arguments.of("missing paymentId", "{\"type\":\"FundsReserved\",\"payload\":{}}"),
                Arguments.of("paymentId is a number", "{\"type\":\"FundsReserved\",\"payload\":{\"paymentId\":42}}"),
                Arguments.of("paymentId is not a uuid", "{\"type\":\"FundsReserved\",\"payload\":{\"paymentId\":\"abc\"}}"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidMessages")
    void invalidMessageIsRejected(String description, String value) {
        assertThatThrownBy(() -> parser.parse(value)).isInstanceOf(InvalidIncomingEventException.class);
    }
}
