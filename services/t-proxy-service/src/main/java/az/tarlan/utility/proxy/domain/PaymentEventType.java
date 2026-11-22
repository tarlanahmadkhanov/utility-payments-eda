package az.tarlan.utility.proxy.domain;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum PaymentEventType {

    FUNDS_RESERVED("FundsReserved",
            PaymentStatus.FUNDS_RESERVED,
            statuses(PaymentStatus.ACCEPTED),
            statuses()),

    FUNDS_RESERVATION_FAILED("FundsReservationFailed",
            PaymentStatus.FAILED,
            statuses(PaymentStatus.ACCEPTED),
            statuses(PaymentStatus.FUNDS_RESERVED, PaymentStatus.PARTNER_CONFIRMED, PaymentStatus.COMPENSATING,
                    PaymentStatus.UNKNOWN, PaymentStatus.COMPLETED)),

    PARTNER_PAYMENT_CONFIRMED("PartnerPaymentConfirmed",
            PaymentStatus.PARTNER_CONFIRMED,
            statuses(PaymentStatus.ACCEPTED, PaymentStatus.FUNDS_RESERVED, PaymentStatus.UNKNOWN),
            statuses(PaymentStatus.COMPENSATING, PaymentStatus.FAILED)),

    PARTNER_PAYMENT_FAILED("PartnerPaymentFailed",
            PaymentStatus.COMPENSATING,
            statuses(PaymentStatus.ACCEPTED, PaymentStatus.FUNDS_RESERVED, PaymentStatus.UNKNOWN),
            statuses(PaymentStatus.PARTNER_CONFIRMED, PaymentStatus.COMPLETED)),

    PARTNER_PAYMENT_OUTCOME_UNKNOWN("PartnerPaymentOutcomeUnknown",
            PaymentStatus.UNKNOWN,
            statuses(PaymentStatus.ACCEPTED, PaymentStatus.FUNDS_RESERVED),
            statuses()),

    FUNDS_CAPTURED("FundsCaptured",
            PaymentStatus.COMPLETED,
            statuses(PaymentStatus.ACCEPTED, PaymentStatus.FUNDS_RESERVED, PaymentStatus.PARTNER_CONFIRMED,
                    PaymentStatus.UNKNOWN),
            statuses(PaymentStatus.COMPENSATING, PaymentStatus.FAILED)),

    FUNDS_RELEASED("FundsReleased",
            PaymentStatus.FAILED,
            statuses(PaymentStatus.ACCEPTED, PaymentStatus.FUNDS_RESERVED, PaymentStatus.COMPENSATING,
                    PaymentStatus.UNKNOWN),
            statuses(PaymentStatus.PARTNER_CONFIRMED, PaymentStatus.COMPLETED));

    private static final Map<String, PaymentEventType> BY_TYPE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(PaymentEventType::typeName, Function.identity()));

    private final String typeName;
    private final PaymentStatus targetStatus;
    private final Set<PaymentStatus> applicableFrom;
    private final Set<PaymentStatus> contradictedIn;

    PaymentEventType(String typeName, PaymentStatus targetStatus, Set<PaymentStatus> applicableFrom,
                     Set<PaymentStatus> contradictedIn) {
        this.typeName = typeName;
        this.targetStatus = targetStatus;
        this.applicableFrom = applicableFrom;
        this.contradictedIn = contradictedIn;
    }

    public String typeName() {
        return typeName;
    }

    public PaymentStatus targetStatus() {
        return targetStatus;
    }

    public Set<PaymentStatus> applicableFrom() {
        return applicableFrom;
    }

    public TransitionOutcome decide(PaymentStatus current) {
        if (applicableFrom.contains(current)) {
            return TransitionOutcome.APPLY;
        }
        if (contradictedIn.contains(current)) {
            return TransitionOutcome.CONTRADICTION;
        }
        return TransitionOutcome.IGNORE;
    }

    public static Optional<PaymentEventType> fromType(String typeName) {
        return Optional.ofNullable(BY_TYPE.get(typeName));
    }

    private static Set<PaymentStatus> statuses(PaymentStatus... statuses) {
        Set<PaymentStatus> result = EnumSet.noneOf(PaymentStatus.class);
        result.addAll(Arrays.asList(statuses));
        return Collections.unmodifiableSet(result);
    }
}
