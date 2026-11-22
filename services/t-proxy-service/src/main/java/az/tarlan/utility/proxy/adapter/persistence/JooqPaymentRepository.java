package az.tarlan.utility.proxy.adapter.persistence;

import az.tarlan.utility.proxy.adapter.persistence.jooq.tables.records.PaymentsRecord;
import az.tarlan.utility.proxy.application.port.PaymentRepository;
import az.tarlan.utility.proxy.application.port.StatusUpdateResult;
import az.tarlan.utility.proxy.domain.Payment;
import az.tarlan.utility.proxy.domain.PaymentStatus;
import az.tarlan.utility.proxy.domain.ServiceType;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static az.tarlan.utility.proxy.adapter.persistence.jooq.Tables.PAYMENTS;

@Repository
public class JooqPaymentRepository implements PaymentRepository {

    private final DSLContext dsl;

    public JooqPaymentRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void insert(Payment payment) {
        dsl.insertInto(PAYMENTS)
                .set(PAYMENTS.ID, payment.id())
                .set(PAYMENTS.CUSTOMER_ID, payment.customerId())
                .set(PAYMENTS.ACCOUNT_NUMBER, payment.accountNumber())
                .set(PAYMENTS.SERVICE_TYPE, payment.serviceType().name())
                .set(PAYMENTS.AMOUNT_MINOR, payment.amountMinor())
                .set(PAYMENTS.CURRENCY, payment.currency())
                .set(PAYMENTS.STATUS, payment.status().name())
                .set(PAYMENTS.CREATED_AT, payment.createdAt().atOffset(ZoneOffset.UTC))
                .set(PAYMENTS.UPDATED_AT, payment.updatedAt().atOffset(ZoneOffset.UTC))
                .execute();
    }

    @Override
    public Optional<Payment> findByIdAndCustomerId(UUID id, String customerId) {
        return dsl.selectFrom(PAYMENTS)
                .where(PAYMENTS.ID.eq(id))
                .and(PAYMENTS.CUSTOMER_ID.eq(customerId))
                .fetchOptional()
                .map(JooqPaymentRepository::toDomain);
    }

    @Override
    public StatusUpdateResult updateStatus(UUID paymentId, PaymentStatus target, Set<PaymentStatus> allowedFrom,
                                           Instant updatedAt) {
        if (allowedFrom.isEmpty()) {
            return currentStatusResult(paymentId);
        }
        int updated = dsl.update(PAYMENTS)
                .set(PAYMENTS.STATUS, target.name())
                .set(PAYMENTS.UPDATED_AT, updatedAt.atOffset(ZoneOffset.UTC))
                .where(PAYMENTS.ID.eq(paymentId))
                .and(PAYMENTS.STATUS.in(allowedFrom.stream().map(PaymentStatus::name).toList()))
                .execute();
        if (updated == 1) {
            return new StatusUpdateResult.Applied();
        }
        return currentStatusResult(paymentId);
    }

    private StatusUpdateResult currentStatusResult(UUID paymentId) {
        return dsl.select(PAYMENTS.STATUS)
                .from(PAYMENTS)
                .where(PAYMENTS.ID.eq(paymentId))
                .fetchOptional(PAYMENTS.STATUS)
                .<StatusUpdateResult>map(status -> new StatusUpdateResult.NotApplied(PaymentStatus.valueOf(status)))
                .orElseGet(StatusUpdateResult.PaymentNotFound::new);
    }

    private static Payment toDomain(PaymentsRecord r) {
        return new Payment(
                r.getId(),
                r.getCustomerId(),
                r.getAccountNumber(),
                ServiceType.valueOf(r.getServiceType()),
                r.getAmountMinor(),
                r.getCurrency(),
                PaymentStatus.valueOf(r.getStatus()),
                r.getCreatedAt().toInstant(),
                r.getUpdatedAt().toInstant());
    }
}
