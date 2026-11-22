package az.tarlan.utility.proxy.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static az.tarlan.utility.proxy.domain.PaymentStatus.ACCEPTED;
import static az.tarlan.utility.proxy.domain.PaymentStatus.COMPENSATING;
import static az.tarlan.utility.proxy.domain.PaymentStatus.COMPLETED;
import static az.tarlan.utility.proxy.domain.PaymentStatus.FAILED;
import static az.tarlan.utility.proxy.domain.PaymentStatus.FUNDS_RESERVED;
import static az.tarlan.utility.proxy.domain.PaymentStatus.PARTNER_CONFIRMED;
import static az.tarlan.utility.proxy.domain.PaymentStatus.UNKNOWN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the transition rules against the matrix of docs/events.md, section 5.
 * The matrix below is written independently of the implementation (as a table), on purpose:
 * A = applied, - = ignored (late or duplicate), ! = contradiction.
 */
class PaymentEventTypeTest {

    private static final List<PaymentStatus> COLUMNS =
            List.of(ACCEPTED, FUNDS_RESERVED, PARTNER_CONFIRMED, COMPENSATING, UNKNOWN, COMPLETED, FAILED);

    private static final String MATRIX = """
            FundsReserved                 A - - - - - -
            FundsReservationFailed        A ! ! ! ! ! -
            PartnerPaymentConfirmed       A A - ! A - !
            PartnerPaymentFailed          A A ! - A ! -
            PartnerPaymentOutcomeUnknown  A A - - - - -
            FundsCaptured                 A A A ! A - !
            FundsReleased                 A A ! A A ! -
            """;

    static Stream<Arguments> matrix() {
        return MATRIX.lines().map(String::strip).filter(line -> !line.isEmpty()).flatMap(line -> {
            String[] cells = line.split("\\s+");
            if (cells.length != COLUMNS.size() + 1) {
                throw new IllegalStateException("Malformed matrix row: " + line);
            }
            PaymentEventType event = PaymentEventType.fromType(cells[0]).orElseThrow();
            return IntStream.range(0, COLUMNS.size())
                    .mapToObj(i -> Arguments.of(event, COLUMNS.get(i), outcome(cells[i + 1])));
        });
    }

    private static TransitionOutcome outcome(String code) {
        return switch (code) {
            case "A" -> TransitionOutcome.APPLY;
            case "-" -> TransitionOutcome.IGNORE;
            case "!" -> TransitionOutcome.CONTRADICTION;
            default -> throw new IllegalStateException("Unknown matrix code: " + code);
        };
    }

    @ParameterizedTest(name = "{0} in {1} -> {2}")
    @MethodSource("matrix")
    void decidesAccordingToMatrix(PaymentEventType event, PaymentStatus current, TransitionOutcome expected) {
        assertEquals(expected, event.decide(current));
    }

    @Test
    void matrixCoversEveryEventTypeAndStatus() {
        long rows = MATRIX.lines().filter(line -> !line.isBlank()).count();
        assertEquals(PaymentEventType.values().length, rows);
        assertEquals(Set.copyOf(List.of(PaymentStatus.values())), Set.copyOf(COLUMNS));
    }

    @ParameterizedTest
    @EnumSource(PaymentEventType.class)
    void terminalStatusIsNeverASourceOfATransition(PaymentEventType event) {
        for (PaymentStatus status : PaymentStatus.values()) {
            if (status.isTerminal()) {
                assertFalse(event.applicableFrom().contains(status), event + " must not apply in " + status);
                assertNotEquals(TransitionOutcome.APPLY, event.decide(status));
            }
        }
    }

    @ParameterizedTest
    @EnumSource(PaymentEventType.class)
    void applicableFromMatchesAppliedCellsOfTheMatrix(PaymentEventType event) {
        Set<PaymentStatus> expected = EnumSet.noneOf(PaymentStatus.class);
        matrix().map(Arguments::get)
                .filter(args -> args[0] == event && args[2] == TransitionOutcome.APPLY)
                .forEach(args -> expected.add((PaymentStatus) args[1]));
        assertEquals(expected, event.applicableFrom());
    }

    @Test
    void targetStatusesMatchTheEventCatalog() {
        Map<PaymentEventType, PaymentStatus> expected = Map.of(
                PaymentEventType.FUNDS_RESERVED, FUNDS_RESERVED,
                PaymentEventType.FUNDS_RESERVATION_FAILED, FAILED,
                PaymentEventType.PARTNER_PAYMENT_CONFIRMED, PARTNER_CONFIRMED,
                PaymentEventType.PARTNER_PAYMENT_FAILED, COMPENSATING,
                PaymentEventType.PARTNER_PAYMENT_OUTCOME_UNKNOWN, UNKNOWN,
                PaymentEventType.FUNDS_CAPTURED, COMPLETED,
                PaymentEventType.FUNDS_RELEASED, FAILED);
        for (PaymentEventType event : PaymentEventType.values()) {
            assertEquals(expected.get(event), event.targetStatus(), event.name());
        }
    }

    @Test
    void everyStatusIsReachable() {
        Set<PaymentStatus> reachable = EnumSet.of(ACCEPTED);
        for (PaymentEventType event : PaymentEventType.values()) {
            reachable.add(event.targetStatus());
        }
        assertEquals(EnumSet.allOf(PaymentStatus.class), reachable);
    }

    @Test
    void findsEventByEnvelopeTypeName() {
        assertEquals(PaymentEventType.FUNDS_CAPTURED, PaymentEventType.fromType("FundsCaptured").orElseThrow());
        assertTrue(PaymentEventType.fromType("PaymentRequested").isEmpty());
        assertTrue(PaymentEventType.fromType("nonsense").isEmpty());
    }
}
