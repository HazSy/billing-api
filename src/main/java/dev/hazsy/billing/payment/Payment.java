package dev.hazsy.billing.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    private UUID id;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /**
     * La clave de idempotencia es única en base de datos: si el cliente
     * reintenta la misma petición (timeout, red), no se cobra dos veces.
     */
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 64)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Payment() {
        // Para JPA
    }

    public Payment(UUID invoiceId, BigDecimal amount, String idempotencyKey) {
        this.id = UUID.randomUUID();
        this.invoiceId = invoiceId;
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
