package dev.hazsy.billing.invoice;

import dev.hazsy.billing.common.ApiException;
import dev.hazsy.billing.customer.Customer;
import dev.hazsy.billing.customer.CustomerRepository;
import dev.hazsy.billing.payment.Payment;
import dev.hazsy.billing.payment.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

    @Mock
    private InvoiceRepository invoices;

    @Mock
    private CustomerRepository customers;

    @Mock
    private PaymentRepository payments;

    @InjectMocks
    private InvoiceService service;

    private final UUID customerId = UUID.randomUUID();
    private final UUID invoiceId = UUID.randomUUID();
    private Customer customer;

    @BeforeEach
    void setUp() {
        customer = new Customer(customerId, "Acme SL", "hola@acme.es", "B12345678");
    }

    private InvoiceDtos.CreateInvoiceRequest request() {
        return new InvoiceDtos.CreateInvoiceRequest(customerId, "EUR", List.of(
                new InvoiceDtos.LineRequest("Consultoría", new BigDecimal("2"), new BigDecimal("50.00"), new BigDecimal("0.21")),
                new InvoiceDtos.LineRequest("Licencia", new BigDecimal("1"), new BigDecimal("30.00"), new BigDecimal("0.10"))
        ));
    }

    @Test
    @DisplayName("crea la factura con numeración y totales calculados")
    void createsInvoiceWithTotals() {
        when(customers.findById(customerId)).thenReturn(Optional.of(customer));
        when(invoices.countByNumberPrefix(anyString())).thenReturn(4L);
        when(invoices.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(payments.sumAmountByInvoiceId(any())).thenReturn(BigDecimal.ZERO);

        InvoiceDtos.InvoiceResponse response = service.create(request());

        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.number()).endsWith("-0005");
        assertThat(response.netTotal()).isEqualByComparingTo("130.00");
        assertThat(response.vatTotal()).isEqualByComparingTo("24.00");
        assertThat(response.grossTotal()).isEqualByComparingTo("154.00");
        assertThat(response.outstanding()).isEqualByComparingTo("154.00");
        assertThat(response.lines()).hasSize(2);
    }

    @Test
    @DisplayName("falla con 404 si el cliente no existe")
    void failsWhenCustomerIsMissing() {
        when(customers.findById(customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("No existe el cliente");
    }

    @Test
    @DisplayName("repetir la misma clave devuelve el pago original sin cobrar dos veces")
    void paymentIsIdempotent() {
        Payment existing = new Payment(invoiceId, new BigDecimal("100.00"), "pago-abc");
        when(payments.findByIdempotencyKey("pago-abc")).thenReturn(Optional.of(existing));
        when(invoices.findById(invoiceId)).thenReturn(Optional.of(draftToIssued()));

        InvoiceDtos.PaymentResponse response = service.pay(invoiceId, new BigDecimal("100.00"), "pago-abc");

        assertThat(response.replayed()).isTrue();
        verify(payments, never()).save(any());
    }

    @Test
    @DisplayName("reutilizar la clave en otra factura es un conflicto")
    void rejectsKeyReusedAcrossInvoices() {
        Payment existing = new Payment(UUID.randomUUID(), new BigDecimal("100.00"), "pago-abc");
        when(payments.findByIdempotencyKey("pago-abc")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.pay(invoiceId, new BigDecimal("1.00"), "pago-abc"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("otra factura");
    }

    @Test
    @DisplayName("la cabecera Idempotency-Key es obligatoria")
    void requiresIdempotencyKey() {
        assertThatThrownBy(() -> service.pay(invoiceId, new BigDecimal("10.00"), "  "))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Idempotency-Key");
    }

    private Invoice draftToIssued() {
        Invoice invoice = new Invoice(invoiceId, "INV-2026-0001", customer, "EUR");
        invoice.addLine(new InvoiceLine("Servicio", new BigDecimal("1"), new BigDecimal("100.00"), new BigDecimal("0.21")));
        invoice.applyTotals(InvoiceCalculator.totals(
                List.of(new InvoiceCalculator.Line(new BigDecimal("1"), new BigDecimal("100.00"), new BigDecimal("0.21")))));
        invoice.markIssued();
        return invoice;
    }
}
