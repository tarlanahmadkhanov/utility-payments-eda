package az.tarlan.utility.proxy.application.port;

import az.tarlan.utility.proxy.domain.PaymentStatus;

/** Result of the conditional status update (see {@link PaymentRepository#updateStatus}). */
public sealed interface StatusUpdateResult {

    /** The status was changed. */
    record Applied() implements StatusUpdateResult {
    }

    /** The payment exists, but its current status is not among the allowed source statuses. */
    record NotApplied(PaymentStatus currentStatus) implements StatusUpdateResult {
    }

    /** There is no payment with this id. */
    record PaymentNotFound() implements StatusUpdateResult {
    }
}
