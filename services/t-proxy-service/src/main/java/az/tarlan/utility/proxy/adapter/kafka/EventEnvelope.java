package az.tarlan.utility.proxy.adapter.kafka;

import java.time.Instant;
import java.util.UUID;

public record EventEnvelope<T>(
        UUID eventId,
        String type,
        int version,
        UUID correlationId,
        UUID causationId,
        Instant occurredAt,
        T payload) {
}
