package dev.hazsy.billing.invoice;

import dev.hazsy.billing.common.ApiException;
import dev.hazsy.billing.customer.Customer;
import dev.hazsy.billing.customer.CustomerRepository;
import dev.hazsy.billing.payment.Payment;
import dev.hazsy.billing.payment.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class InvoiceService {

    private final InvoiceRepository invoices;
    private final CustomerRepository customers;
    private final PaymentRepository payments;

    public InvoiceService(InvoiceRepository invoices, CustomerRepository customers, PaymentRepository payments) {
        this.invoices = invoices;
        this.customers = customers;
        this.payments = payments;
    }

    @Transactional
    public InvoiceDtos.InvoiceResponse create(InvoiceDtos.CreateInvoiceRequest request) {
        Customer customer = customers.findById(request.customerId())
                .orElseThrow(() -> ApiException.notFound("No existe el cliente " + request.customerId()));

        Invoice invoice = new Invoice(UUID.randomUUID(), nextNumber(), customer, request.currency());

        for (InvoiceDtos.LineRequest line : request.lines()) {
            invoice.addLine(new InvoiceLine(line.description().trim(), line.quantity(), line.unitPrice(), line.vatRate()));
        }

        invoice.applyTotals(InvoiceCalculator.totals(
                request.lines().stream()
                        .map(line -> new InvoiceCalculator.Line(line.quantity(), line.unitPrice(), line.vatRate()))
                        .toList()
        ));

        return toResponse(invoices.save(invoice));
    }

    @Transactional
    public InvoiceDtos.InvoiceResponse issue(UUID id) {
        Invoice invoice = find(id);
        invoice.markIssued();
        return toResponse(invoices.save(invoice));
    }

    @Transactional
    public InvoiceDtos.InvoiceResponse voidInvoice(UUID id) {
        Invoice invoice = find(id);
        invoice.markVoided();
        return toResponse(invoices.save(invoice));
    }

    /**
     * Registra un pago de forma idempotente: repetir la petición con la misma
     * clave devuelve el pago original sin volver a aplicar el importe.
     */
    @Transactional
    public InvoiceDtos.PaymentResponse pay(UUID id, BigDecimal amount, String idempotencyKey) {
        String key = idempotencyKey == null ? "" : idempotencyKey.trim();
        if (key.isEmpty()) {
            throw ApiException.badRequest("missing_idempotency_key",
                    "La cabecera Idempotency-Key es obligatoria para registrar un pago.");
        }
        if (key.length() > 64) {
            throw ApiException.badRequest("invalid_idempotency_key", "La clave no puede superar 64 caracteres.");
        }

        var existing = payments.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            Payment payment = existing.get();
            if (!payment.getInvoiceId().equals(id)) {
                throw ApiException.conflict("idempotency_key_reused",
                        "Esa clave ya se usó en otra factura.");
            }
            Invoice invoice = find(id);
            return new InvoiceDtos.PaymentResponse(
                    payment.getId(), id, payment.getAmount(), invoice.getStatus().name(), true);
        }

        Invoice invoice = find(id);
        BigDecimal paidSoFar = payments.sumAmountByInvoiceId(id);
        invoice.registerPayment(amount, paidSoFar);

        Payment payment = payments.save(new Payment(id, amount, key));
        invoices.save(invoice);

        return new InvoiceDtos.PaymentResponse(
                payment.getId(), id, payment.getAmount(), invoice.getStatus().name(), false);
    }

    @Transactional(readOnly = true)
    public InvoiceDtos.InvoiceResponse get(UUID id) {
        return toResponse(find(id));
    }

    @Transactional(readOnly = true)
    public List<InvoiceDtos.InvoiceResponse> list(InvoiceStatus status) {
        List<Invoice> found = status == null
                ? invoices.findAllByOrderByCreatedAtDesc()
                : invoices.findByStatusOrderByCreatedAtDesc(status);

        return found.stream().map(this::toResponse).toList();
    }

    private Invoice find(UUID id) {
        return invoices.findById(id)
                .orElseThrow(() -> ApiException.notFound("No existe la factura " + id));
    }

    private String nextNumber() {
        int year = LocalDate.now().getYear();
        String prefix = "INV-" + year + "-";
        long count = invoices.countByNumberPrefix(prefix);
        return prefix + String.format("%04d", count + 1);
    }

    private InvoiceDtos.InvoiceResponse toResponse(Invoice invoice) {
        BigDecimal paid = payments.sumAmountByInvoiceId(invoice.getId());

        return new InvoiceDtos.InvoiceResponse(
                invoice.getId(),
                invoice.getNumber(),
                new InvoiceDtos.CustomerSummary(
                        invoice.getCustomer().getId(),
                        invoice.getCustomer().getName(),
                        invoice.getCustomer().getEmail(),
                        invoice.getCustomer().getTaxId()),
                invoice.getStatus().name(),
                invoice.getCurrency(),
                invoice.getIssueDate(),
                invoice.getNetTotal(),
                invoice.getVatTotal(),
                invoice.getGrossTotal(),
                paid,
                invoice.getGrossTotal().subtract(paid),
                invoice.getLines().stream()
                        .map(line -> new InvoiceDtos.LineResponse(
                                line.getPosition(),
                                line.getDescription(),
                                line.getQuantity(),
                                line.getUnitPrice(),
                                line.getVatRate(),
                                line.getNetAmount(),
                                line.getVatAmount()))
                        .toList(),
                invoice.getVersion(),
                invoice.getCreatedAt()
        );
    }
}
