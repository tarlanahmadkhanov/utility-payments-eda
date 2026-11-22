package az.tarlan.utility.proxy.domain;

public enum PaymentStatus {
    ACCEPTED,
    FUNDS_RESERVED,
    PARTNER_CONFIRMED,
    COMPENSATING,
    UNKNOWN,
    COMPLETED,
    FAILED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }
}
