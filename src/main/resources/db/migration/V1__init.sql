-- Esquema inicial de facturación.
-- Compatible con H2 (modo PostgreSQL) y con PostgreSQL real.

CREATE TABLE customers (
    id         UUID PRIMARY KEY,
    name       VARCHAR(120) NOT NULL,
    email      VARCHAR(160) NOT NULL,
    tax_id     VARCHAR(20)  NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_customers_email UNIQUE (email),
    CONSTRAINT uk_customers_tax_id UNIQUE (tax_id)
);

CREATE TABLE invoices (
    id           UUID PRIMARY KEY,
    number       VARCHAR(24)    NOT NULL,
    customer_id  UUID           NOT NULL,
    status       VARCHAR(16)    NOT NULL,
    currency     VARCHAR(3)     NOT NULL,
    issue_date   DATE           NOT NULL,
    net_total    NUMERIC(12, 2) NOT NULL,
    vat_total    NUMERIC(12, 2) NOT NULL,
    gross_total  NUMERIC(12, 2) NOT NULL,
    version      BIGINT         NOT NULL DEFAULT 0,
    created_at   TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_invoices_number UNIQUE (number),
    CONSTRAINT fk_invoices_customer FOREIGN KEY (customer_id) REFERENCES customers (id)
);

CREATE TABLE invoice_lines (
    id          UUID PRIMARY KEY,
    invoice_id  UUID           NOT NULL,
    line_order  INT            NOT NULL,
    description VARCHAR(240)   NOT NULL,
    quantity    NUMERIC(12, 3) NOT NULL,
    unit_price  NUMERIC(12, 2) NOT NULL,
    vat_rate    NUMERIC(5, 4)  NOT NULL,
    net_amount  NUMERIC(12, 2) NOT NULL,
    vat_amount  NUMERIC(12, 2) NOT NULL,
    CONSTRAINT fk_lines_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id) ON DELETE CASCADE
);

CREATE TABLE payments (
    id              UUID PRIMARY KEY,
    invoice_id      UUID           NOT NULL,
    amount          NUMERIC(12, 2) NOT NULL,
    idempotency_key VARCHAR(64)    NOT NULL,
    created_at      TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_payments_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_payments_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id)
);

CREATE INDEX ix_invoices_status ON invoices (status);
CREATE INDEX ix_invoices_customer ON invoices (customer_id);
CREATE INDEX ix_payments_invoice ON payments (invoice_id);
