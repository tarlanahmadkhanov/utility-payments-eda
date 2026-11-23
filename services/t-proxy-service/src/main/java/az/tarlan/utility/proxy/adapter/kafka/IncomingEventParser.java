package az.tarlan.utility.proxy.adapter.kafka;

import az.tarlan.utility.proxy.application.IncomingPaymentEvent;
import az.tarlan.utility.proxy.domain.PaymentEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;
import java.util.UUID;

@Component
public class IncomingEventParser {

    private static final Logger log = LoggerFactory.getLogger(IncomingEventParser.class);

    private final JsonMapper jsonMapper;

    public IncomingEventParser(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public Optional<IncomingPaymentEvent> parse(String value) {
        JsonNode root = readTree(value);
        if (!root.isObject()) {
            throw new InvalidIncomingEventException("Message is not a JSON object");
        }
        String typeName = requiredText(root.path("type"), "type");
        Optional<PaymentEventType> type = PaymentEventType.fromType(typeName);
        if (type.isEmpty()) {
            log.info("Ignoring event of unknown type {}", typeName);
            return Optional.empty();
        }
        UUID paymentId = parseUuid(requiredText(root.path("payload").path("paymentId"), "payload.paymentId"));
        JsonNode eventId = root.path("eventId");
        return Optional.of(new IncomingPaymentEvent(eventId.isString() ? eventId.asString() : null, type.get(), paymentId));
    }

    private JsonNode readTree(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidIncomingEventException("Message is empty");
        }
        try {
            return jsonMapper.readTree(value);
        } catch (JacksonException e) {
            throw new InvalidIncomingEventException("Message is not valid JSON", e);
        }
    }

    private static String requiredText(JsonNode node, String field) {
        if (!node.isString() || node.asString().isBlank()) {
            throw new InvalidIncomingEventException("Field " + field + " is missing or not a string");
        }
        return node.asString();
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new InvalidIncomingEventException("Field payload.paymentId is not a UUID", e);
        }
    }
}
