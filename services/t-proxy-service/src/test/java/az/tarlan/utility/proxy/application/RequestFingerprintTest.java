package az.tarlan.utility.proxy.application;

import az.tarlan.utility.proxy.domain.ServiceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class RequestFingerprintTest {

    private static final AcceptPaymentCommand BASE = command("AZ-ELEC-0042", ServiceType.ELECTRICITY, 2550, "AZN");

    private static AcceptPaymentCommand command(String account, ServiceType service, long amount, String currency) {
        return new AcceptPaymentCommand("customer-1", "key-1", account, service, amount, currency);
    }

    static Stream<AcceptPaymentCommand> variants() {
        return Stream.of(
                command("AZ-ELEC-0043", ServiceType.ELECTRICITY, 2550, "AZN"),
                command("AZ-ELEC-0042", ServiceType.WATER, 2550, "AZN"),
                command("AZ-ELEC-0042", ServiceType.ELECTRICITY, 2551, "AZN"),
                command("AZ-ELEC-0042", ServiceType.ELECTRICITY, 2550, "USD"),
                command("az-elec-0042", ServiceType.ELECTRICITY, 2550, "AZN"));
    }

    @Test
    void producesLowercaseHexSha256() {
        assertThat(RequestFingerprint.of(BASE)).matches("^[0-9a-f]{64}$");
    }

    @Test
    void isDeterministic() {
        assertThat(RequestFingerprint.of(BASE)).isEqualTo(RequestFingerprint.of(
                command("AZ-ELEC-0042", ServiceType.ELECTRICITY, 2550, "AZN")));
    }

    @Test
    void ignoresCustomerAndIdempotencyKey() {
        AcceptPaymentCommand other = new AcceptPaymentCommand("customer-2", "key-2", "AZ-ELEC-0042",
                ServiceType.ELECTRICITY, 2550, "AZN");

        assertThat(RequestFingerprint.of(other)).isEqualTo(RequestFingerprint.of(BASE));
    }

    @ParameterizedTest
    @MethodSource("variants")
    void changesWhenAnyHashedFieldChanges(AcceptPaymentCommand variant) {
        assertThat(RequestFingerprint.of(variant)).isNotEqualTo(RequestFingerprint.of(BASE));
    }

    @Test
    void lengthPrefixPreventsCollisionsBetweenAdjacentFields() {
        // Without a length prefix both commands would hash the same byte sequence "123AZ".
        AcceptPaymentCommand first = command("A", ServiceType.WATER, 12, "3AZ");
        AcceptPaymentCommand second = command("A", ServiceType.WATER, 123, "AZ");

        assertThat(RequestFingerprint.of(first)).isNotEqualTo(RequestFingerprint.of(second));
    }
}
