package az.tarlan.utility.proxy.domain;

/**
 * Payment status derived by the proxy from events of other services (see docs/events.md, section 5).
 * The set of values must stay in sync with the CHECK constraint on {@code payments.status} and with the OpenAPI contract.
 */
public enum PaymentStatus {
    ACCEPTED,
    FUNDS_RESERVED,
    PARTNER_CONFIRMED,
    COMPENSATING,
    UNKNOWN,
    COMPLETED,
    FAILED;

    /** A terminal status never changes. */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }
}
