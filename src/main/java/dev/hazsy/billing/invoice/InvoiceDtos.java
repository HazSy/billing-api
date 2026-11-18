package dev.hazsy.billing.invoice;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Contratos de entrada y salida de la API. */
public final class InvoiceDtos {

    private InvoiceDtos() {
    }

    public record LineRequest(
            @NotBlank @Size(max = 240) String description,
            @NotNull @DecimalMin(value = "0.001") BigDecimal quantity,
            @NotNull @DecimalMin(value = "0.00") BigDecimal unitPrice,
            @NotNull @DecimalMin(value = "0.00") @DecimalMax(value = "1.00") BigDecimal vatRate
    ) {
    }

    public record CreateInvoiceRequest(
            @NotNull UUID customerId,
            @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "Usa un código ISO de 3 letras, por ejemplo EUR.") String currency,
            @NotEmpty(message = "La factura necesita al menos una línea.") @Size(max = 50) List<@Valid LineRequest> lines
    ) {
    }

    public record PaymentRequest(
            @NotNull @DecimalMin(value = "0.01", message = "El importe debe ser mayor que cero.") BigDecimal amount
    ) {
    }

    public record LineResponse(
            int position,
            String description,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal vatRate,
            BigDecimal netAmount,
            BigDecimal vatAmount
    ) {
    }

    public record CustomerSummary(UUID id, String name, String email, String taxId) {
    }

    public record InvoiceResponse(
            UUID id,
            String number,
            CustomerSummary customer,
            String status,
            String currency,
            LocalDate issueDate,
            BigDecimal netTotal,
            BigDecimal vatTotal,
            BigDecimal grossTotal,
            BigDecimal paidTotal,
            BigDecimal outstanding,
            List<LineResponse> lines,
            long version,
            Instant createdAt
    ) {
    }

    public record PaymentResponse(
            UUID paymentId,
            UUID invoiceId,
            BigDecimal amount,
            String invoiceStatus,
            boolean replayed
    ) {
    }
}
