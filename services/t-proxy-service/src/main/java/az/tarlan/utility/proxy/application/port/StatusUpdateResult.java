package az.tarlan.utility.proxy.application.port;

import az.tarlan.utility.proxy.domain.PaymentStatus;

public sealed interface StatusUpdateResult {

    record Applied() implements StatusUpdateResult {
    }

    record NotApplied(PaymentStatus currentStatus) implements StatusUpdateResult {
    }

    record PaymentNotFound() implements StatusUpdateResult {
    }
}
