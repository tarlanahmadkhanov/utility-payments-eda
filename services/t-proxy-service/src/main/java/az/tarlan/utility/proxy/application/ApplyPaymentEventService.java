package az.tarlan.utility.proxy.application;

import az.tarlan.utility.proxy.application.port.PaymentRepository;
import az.tarlan.utility.proxy.application.port.StatusUpdateResult;
import az.tarlan.utility.proxy.domain.PaymentEventType;
import az.tarlan.utility.proxy.domain.PaymentStatus;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;

@Service
public class ApplyPaymentEventService {

    static final String CONTRADICTIONS_METRIC = "payment.events.contradictions";

    private static final Logger log = LoggerFactory.getLogger(ApplyPaymentEventService.class);

    private final PaymentRepository paymentRepository;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    public ApplyPaymentEventService(PaymentRepository paymentRepository, MeterRegistry meterRegistry, Clock clock) {
        this.paymentRepository = paymentRepository;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    public ApplyPaymentEventResult apply(IncomingPaymentEvent event) {
        PaymentEventType type = event.type();
        StatusUpdateResult result = paymentRepository.updateStatus(
                event.paymentId(), type.targetStatus(), type.applicableFrom(), clock.instant());
        return switch (result) {
            case StatusUpdateResult.Applied applied -> {
                log.info("Applied {} (eventId={}) to payment {}: status is now {}",
                        type.typeName(), event.eventId(), event.paymentId(), type.targetStatus());
                yield ApplyPaymentEventResult.APPLIED;
            }
            case StatusUpdateResult.PaymentNotFound notFound -> {
                log.warn("Ignoring {} (eventId={}) for unknown payment {}",
                        type.typeName(), event.eventId(), event.paymentId());
                yield ApplyPaymentEventResult.UNKNOWN_PAYMENT;
            }
            case StatusUpdateResult.NotApplied notApplied -> handleNotApplied(event, notApplied.currentStatus());
        };
    }

    private ApplyPaymentEventResult handleNotApplied(IncomingPaymentEvent event, PaymentStatus currentStatus) {
        PaymentEventType type = event.type();
        return switch (type.decide(currentStatus)) {
            case CONTRADICTION -> {
                log.warn("Contradicting event {} (eventId={}) for payment {} in status {}: ignored",
                        type.typeName(), event.eventId(), event.paymentId(), currentStatus);
                meterRegistry.counter(CONTRADICTIONS_METRIC,
                        "eventType", type.typeName(),
                        "currentStatus", currentStatus.name()).increment();
                yield ApplyPaymentEventResult.CONTRADICTION;
            }
            case IGNORE -> {
                log.debug("Ignoring late or duplicate event {} (eventId={}) for payment {} in status {}",
                        type.typeName(), event.eventId(), event.paymentId(), currentStatus);
                yield ApplyPaymentEventResult.IGNORED;
            }
            case APPLY -> throw new IllegalStateException("Status of payment " + event.paymentId()
                    + " changed concurrently while applying " + type.typeName());
        };
    }
}
