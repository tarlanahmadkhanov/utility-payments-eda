package az.tarlan.utility.proxy.domain;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Incoming events of other services that change the payment status, with the transition rules from
 * docs/events.md, section 5.
 *
 * <p>Statuses are not linearly ordered (the branches PARTNER_CONFIRMED, COMPENSATING and UNKNOWN are independent),
 * so every event carries an explicit set of source statuses it may be applied from, instead of a "rank" comparison.
 * The same set goes into the {@code WHERE} clause of the conditional UPDATE in the repository.
 * Terminal statuses are never in that set.
 */
public enum PaymentEventType {

    FUNDS_RESERVED("FundsReserved",
            PaymentStatus.FUNDS_RESERVED,
            set(PaymentStatus.ACCEPTED),
            set()),

    FUNDS_RESERVATION_FAILED("FundsReservationFailed",
            PaymentStatus.FAILED,
            set(PaymentStatus.ACCEPTED),
            set(PaymentStatus.FUNDS_RESERVED, PaymentStatus.PARTNER_CONFIRMED, PaymentStatus.COMPENSATING,
                    PaymentStatus.UNKNOWN, PaymentStatus.COMPLETED)),

    PARTNER_PAYMENT_CONFIRMED("PartnerPaymentConfirmed",
            PaymentStatus.PARTNER_CONFIRMED,
            set(PaymentStatus.ACCEPTED, PaymentStatus.FUNDS_RESERVED, PaymentStatus.UNKNOWN),
            set(PaymentStatus.COMPENSATING, PaymentStatus.FAILED)),

    PARTNER_PAYMENT_FAILED("PartnerPaymentFailed",
            PaymentStatus.COMPENSATING,
            set(PaymentStatus.ACCEPTED, PaymentStatus.FUNDS_RESERVED, PaymentStatus.UNKNOWN),
            set(PaymentStatus.PARTNER_CONFIRMED, PaymentStatus.COMPLETED)),

    PARTNER_PAYMENT_OUTCOME_UNKNOWN("PartnerPaymentOutcomeUnknown",
            PaymentStatus.UNKNOWN,
            set(PaymentStatus.ACCEPTED, PaymentStatus.FUNDS_RESERVED),
            set()),

    FUNDS_CAPTURED("FundsCaptured",
            PaymentStatus.COMPLETED,
            set(PaymentStatus.ACCEPTED, PaymentStatus.FUNDS_RESERVED, PaymentStatus.PARTNER_CONFIRMED,
                    PaymentStatus.UNKNOWN),
            set(PaymentStatus.COMPENSATING, PaymentStatus.FAILED)),

    FUNDS_RELEASED("FundsReleased",
            PaymentStatus.FAILED,
            set(PaymentStatus.ACCEPTED, PaymentStatus.FUNDS_RESERVED, PaymentStatus.COMPENSATING,
                    PaymentStatus.UNKNOWN),
            set(PaymentStatus.PARTNER_CONFIRMED, PaymentStatus.COMPLETED));

    private static final Map<String, PaymentEventType> BY_TYPE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(PaymentEventType::typeName, Function.identity()));

    private final String typeName;
    private final PaymentStatus targetStatus;
    private final Set<PaymentStatus> applicableFrom;
    private final Set<PaymentStatus> contradictingIn;

    PaymentEventType(String typeName, PaymentStatus targetStatus, Set<PaymentStatus> applicableFrom,
                     Set<PaymentStatus> contradictingIn) {
        this.typeName = typeName;
        this.targetStatus = targetStatus;
        this.applicableFrom = applicableFrom;
        this.contradictingIn = contradictingIn;
    }

    /** The value of {@code type} in the event envelope. */
    public String typeName() {
        return typeName;
    }

    /** The status the payment moves to when the event is applied. */
    public PaymentStatus targetStatus() {
        return targetStatus;
    }

    /** Current statuses from which the event is applied (used as the {@code WHERE} filter of the conditional UPDATE). */
    public Set<PaymentStatus> applicableFrom() {
        return Collections.unmodifiableSet(applicableFrom);
    }

    /**
     * Decides what to do with the event when the payment is in the {@code current} status.
     * An event in a terminal status is always ignored; it is a contradiction only if it contradicts the outcome
     * (for example, {@code FundsCaptured} for a failed payment).
     */
    public TransitionOutcome decide(PaymentStatus current) {
        if (applicableFrom.contains(current)) {
            return TransitionOutcome.APPLY;
        }
        if (contradictingIn.contains(current)) {
            return TransitionOutcome.CONTRADICTION;
        }
        return TransitionOutcome.IGNORE;
    }

    /** Finds the event by the value of {@code type} in the envelope; empty for an unknown type. */
    public static Optional<PaymentEventType> fromType(String typeName) {
        return Optional.ofNullable(BY_TYPE.get(typeName));
    }

    private static Set<PaymentStatus> set(PaymentStatus... statuses) {
        return statuses.length == 0 ? EnumSet.noneOf(PaymentStatus.class) : EnumSet.copyOf(List.of(statuses));
    }
}
