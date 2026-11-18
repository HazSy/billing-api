package dev.hazsy.billing.invoice;

import dev.hazsy.billing.common.ApiException;
import dev.hazsy.billing.customer.Customer;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "invoices")
public class Invoice {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 24)
    private String number;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InvoiceStatus status;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "net_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal netTotal = BigDecimal.ZERO;

    @Column(name = "vat_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal vatTotal = BigDecimal.ZERO;

    @Column(name = "gross_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal grossTotal = BigDecimal.ZERO;

    /**
     * Bloqueo optimista: si dos peticiones modifican la factura a la vez,
     * la segunda recibe un 409 en lugar de pisar los datos de la primera.
     */
    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<InvoiceLine> lines = new ArrayList<>();

    protected Invoice() {
        // Para JPA
    }

    public Invoice(UUID id, String number, Customer customer, String currency) {
        this.id = id;
        this.number = number;
        this.customer = customer;
        this.currency = currency;
        this.status = InvoiceStatus.DRAFT;
        this.issueDate = LocalDate.now();
        this.createdAt = Instant.now();
    }

    public void addLine(InvoiceLine line) {
        line.attachTo(this, lines.size() + 1);
        lines.add(line);
    }

    public void applyTotals(InvoiceCalculator.Totals totals) {
        this.netTotal = totals.net();
        this.vatTotal = totals.vat();
        this.grossTotal = totals.gross();
    }

    public void markIssued() {
        if (!status.canBeIssued()) {
            throw ApiException.conflict("invalid_status", "Solo se puede emitir una factura en borrador.");
        }
        if (lines.isEmpty()) {
            throw ApiException.conflict("empty_invoice", "Una factura sin líneas no se puede emitir.");
        }
        this.status = InvoiceStatus.ISSUED;
        this.issueDate = LocalDate.now();
    }

    public void markVoided() {
        if (!status.canBeVoided()) {
            throw ApiException.conflict("invalid_status", "Una factura pagada no se puede anular.");
        }
        this.status = InvoiceStatus.VOID;
    }

    /** 'paid' es el importe acumulado de los pagos, ya validado por el servicio. */
    public void registerPayment(BigDecimal amount, BigDecimal paidSoFar) {
        if (!status.acceptsPayments()) {
            throw ApiException.conflict("invalid_status", "Esta factura no admite pagos en su estado actual.");
        }
        if (amount.signum() <= 0) {
            throw ApiException.badRequest("invalid_amount", "El importe debe ser mayor que cero.");
        }

        BigDecimal total = paidSoFar.add(amount);
        if (total.compareTo(grossTotal) > 0) {
            throw ApiException.badRequest("overpayment", "El pago supera el total pendiente de la factura.");
        }

        this.status = total.compareTo(grossTotal) == 0 ? InvoiceStatus.PAID : InvoiceStatus.PARTIALLY_PAID;
    }

    public UUID getId() {
        return id;
    }

    public String getNumber() {
        return number;
    }

    public Customer getCustomer() {
        return customer;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public String getCurrency() {
        return currency;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public BigDecimal getNetTotal() {
        return netTotal;
    }

    public BigDecimal getVatTotal() {
        return vatTotal;
    }

    public BigDecimal getGrossTotal() {
        return grossTotal;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<InvoiceLine> getLines() {
        return List.copyOf(lines);
    }
}
