package dev.hazsy.billing.invoice;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/invoices")
@Tag(name = "Facturas")
@SecurityRequirement(name = "bearer-jwt")
public class InvoiceController {

    private final InvoiceService service;

    public InvoiceController(InvoiceService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    @Operation(summary = "Lista facturas, opcionalmente filtradas por estado")
    public List<InvoiceDtos.InvoiceResponse> list(@RequestParam(required = false) InvoiceStatus status) {
        return service.list(status);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public InvoiceDtos.InvoiceResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    @Operation(summary = "Crea una factura en borrador y calcula sus totales")
    public InvoiceDtos.InvoiceResponse create(@Valid @RequestBody InvoiceDtos.CreateInvoiceRequest request) {
        return service.create(request);
    }

    @PostMapping("/{id}/issue")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    @Operation(summary = "Emite un borrador (solo el administrador puede anular después)")
    public InvoiceDtos.InvoiceResponse issue(@PathVariable UUID id) {
        return service.issue(id);
    }

    @PostMapping("/{id}/void")
    @PreAuthorize("hasRole('ADMIN')")
    public InvoiceDtos.InvoiceResponse voidInvoice(@PathVariable UUID id) {
        return service.voidInvoice(id);
    }

    @PostMapping("/{id}/payments")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    @Operation(summary = "Registra un pago idempotente mediante la cabecera Idempotency-Key")
    public InvoiceDtos.PaymentResponse pay(
            @PathVariable UUID id,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody InvoiceDtos.PaymentRequest request) {
        return service.pay(id, request.amount(), idempotencyKey);
    }
}
