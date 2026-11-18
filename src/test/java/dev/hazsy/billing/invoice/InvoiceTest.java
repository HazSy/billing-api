package dev.hazsy.billing.invoice;

import dev.hazsy.billing.common.ApiException;
import dev.hazsy.billing.customer.Customer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvoiceTest {

    private Invoice invoice;

    @BeforeEach
    void setUp() {
        Customer customer = new Customer(UUID.randomUUID(), "Acme SL", "hola@acme.es", "B12345678");
        invoice = new Invoice(UUID.randomUUID(), "INV-2026-0001", customer, "EUR");
        invoice.addLine(new InvoiceLine("Consultoría", new BigDecimal("2"), new BigDecimal("50.00"), new BigDecimal("0.21")));
        invoice.applyTotals(InvoiceCalculator.totals(
                java.util.List.of(new InvoiceCalculator.Line(new BigDecimal("2"), new BigDecimal("50.00"), new BigDecimal("0.21")))));
    }

    @Test
    @DisplayName("nace en borrador y se puede emitir")
    void draftCanBeIssued() {
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.DRAFT);

        invoice.markIssued();

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.ISSUED);
    }

    @Test
    @DisplayName("no se emite dos veces")
    void cannotIssueTwice() {
        invoice.markIssued();

        assertThatThrownBy(invoice::markIssued)
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("borrador");
    }

    @Test
    @DisplayName("una factura sin líneas no se puede emitir")
    void cannotIssueEmptyInvoice() {
        Customer customer = new Customer(UUID.randomUUID(), "Vacía SL", "vacia@acme.es", "B99999999");
        Invoice empty = new Invoice(UUID.randomUUID(), "INV-2026-0099", customer, "EUR");

        assertThatThrownBy(empty::markIssued)
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("sin líneas");
    }

    @Test
    @DisplayName("un pago parcial deja la factura en PARTIALLY_PAID")
    void partialPayment() {
        invoice.markIssued();

        invoice.registerPayment(new BigDecimal("20.00"), BigDecimal.ZERO);

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PARTIALLY_PAID);
    }

    @Test
    @DisplayName("cubrir el total pasa la factura a PAID")
    void fullPayment() {
        invoice.markIssued();

        invoice.registerPayment(new BigDecimal("121.00"), BigDecimal.ZERO);

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
    }

    @Test
    @DisplayName("no se acepta un pago mayor que el pendiente")
    void rejectsOverpayment() {
        invoice.markIssued();

        assertThatThrownBy(() -> invoice.registerPayment(new BigDecimal("200.00"), BigDecimal.ZERO))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("supera");
    }

    @Test
    @DisplayName("un borrador no admite pagos")
    void draftRejectsPayments() {
        assertThatThrownBy(() -> invoice.registerPayment(new BigDecimal("10.00"), BigDecimal.ZERO))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("no admite pagos");
    }

    @Test
    @DisplayName("un pago de importe cero se rechaza")
    void rejectsZeroPayment() {
        invoice.markIssued();

        assertThatThrownBy(() -> invoice.registerPayment(BigDecimal.ZERO, BigDecimal.ZERO))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("mayor que cero");
    }
}
