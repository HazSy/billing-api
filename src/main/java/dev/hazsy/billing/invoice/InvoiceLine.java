package dev.hazsy.billing.invoice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "invoice_lines")
public class InvoiceLine {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(name = "line_order", nullable = false)
    private int position;

    @Column(nullable = false, length = 240)
    private String description;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "vat_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal vatRate;

    @Column(name = "net_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal netAmount;

    @Column(name = "vat_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal vatAmount;

    protected InvoiceLine() {
        // Para JPA
    }

    public InvoiceLine(String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal vatRate) {
        this.id = UUID.randomUUID();
        this.description = description;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.vatRate = vatRate;
        this.netAmount = InvoiceCalculator.lineNet(quantity, unitPrice);
        this.vatAmount = InvoiceCalculator.lineVat(netAmount, vatRate);
    }

    void attachTo(Invoice invoice, int position) {
        this.invoice = invoice;
        this.position = position;
    }

    public UUID getId() {
        return id;
    }

    public int getPosition() {
        return position;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getVatRate() {
        return vatRate;
    }

    public BigDecimal getNetAmount() {
        return netAmount;
    }

    public BigDecimal getVatAmount() {
        return vatAmount;
    }
}
