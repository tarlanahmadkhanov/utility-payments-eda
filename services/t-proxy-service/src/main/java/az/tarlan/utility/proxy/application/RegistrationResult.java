package az.tarlan.utility.proxy.application;

import az.tarlan.utility.proxy.domain.Payment;

public sealed interface RegistrationResult {

    record Created(Payment payment) implements RegistrationResult {
    }

    record Existing(Payment payment) implements RegistrationResult {
    }

    record Conflict() implements RegistrationResult {
    }
}
