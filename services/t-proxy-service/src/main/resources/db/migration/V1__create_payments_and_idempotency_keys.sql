-- t-proxy-service: payments read model and idempotency keys (ADR-0003, docs/events.md).

CREATE TABLE payments
(
    id             uuid        NOT NULL,
    customer_id    text        NOT NULL,
    account_number text        NOT NULL,
    service_type   text        NOT NULL,
    amount_minor   bigint      NOT NULL,
    currency       text        NOT NULL,
    status         text        NOT NULL,
    created_at     timestamptz NOT NULL,
    updated_at     timestamptz NOT NULL,

    CONSTRAINT pk_payments PRIMARY KEY (id),
    CONSTRAINT chk_payments_customer_id CHECK (char_length(customer_id) BETWEEN 1 AND 64),
    CONSTRAINT chk_payments_account_number CHECK (char_length(account_number) BETWEEN 1 AND 64),
    CONSTRAINT chk_payments_service_type CHECK (service_type IN ('ELECTRICITY', 'WATER', 'GAS', 'HEATING', 'INTERNET')),
    CONSTRAINT chk_payments_amount_minor CHECK (amount_minor > 0),
    CONSTRAINT chk_payments_currency CHECK (currency ~ '^[A-Z]{3}$'),
    -- Status values follow docs/events.md, section 5. Adding a status means changing this constraint in a new migration.
    CONSTRAINT chk_payments_status CHECK (status IN (
        'ACCEPTED', 'FUNDS_RESERVED', 'PARTNER_CONFIRMED', 'COMPENSATING', 'UNKNOWN', 'COMPLETED', 'FAILED'))
);

-- A key belongs to a customer: the same key string of two customers never collides (ADR-0003).
CREATE TABLE idempotency_keys
(
    customer_id     text        NOT NULL,
    idempotency_key text        NOT NULL,
    -- Hex SHA-256 of the canonical request body (accountNumber, serviceType, amountMinor, currency).
    request_hash    text        NOT NULL,
    payment_id      uuid        NOT NULL,
    created_at      timestamptz NOT NULL,

    CONSTRAINT pk_idempotency_keys PRIMARY KEY (customer_id, idempotency_key),
    CONSTRAINT uq_idempotency_keys_payment_id UNIQUE (payment_id),
    CONSTRAINT fk_idempotency_keys_payment FOREIGN KEY (payment_id) REFERENCES payments (id),
    CONSTRAINT chk_idempotency_keys_customer_id CHECK (char_length(customer_id) BETWEEN 1 AND 64),
    CONSTRAINT chk_idempotency_keys_key CHECK (char_length(idempotency_key) BETWEEN 1 AND 255),
    CONSTRAINT chk_idempotency_keys_request_hash CHECK (request_hash ~ '^[0-9a-f]{64}$')
);
