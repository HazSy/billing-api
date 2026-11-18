package dev.hazsy.billing.invoice;

public enum InvoiceStatus {
    DRAFT,
    ISSUED,
    PARTIALLY_PAID,
    PAID,
    VOID;

    public boolean canBeIssued() {
        return this == DRAFT;
    }

    public boolean acceptsPayments() {
        return this == ISSUED || this == PARTIALLY_PAID;
    }

    public boolean canBeVoided() {
        return this == DRAFT || this == ISSUED;
    }
}
