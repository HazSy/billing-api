package dev.hazsy.billing.customer;

import dev.hazsy.billing.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    public record CreateCustomerRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Email @Size(max = 160) String email,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9]{8,20}", message = "CIF/NIF de 8 a 20 caracteres alfanuméricos.") String taxId
    ) {
    }

    public record CustomerResponse(UUID id, String name, String email, String taxId) {
    }

    private final CustomerRepository customers;

    public CustomerController(CustomerRepository customers) {
        this.customers = customers;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public List<CustomerResponse> list() {
        return customers.findAll().stream()
                .sorted(Comparator.comparing(Customer::getName, String.CASE_INSENSITIVE_ORDER))
                .map(this::toResponse)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public CustomerResponse create(@Valid @RequestBody CreateCustomerRequest request) {
        if (customers.findByEmail(request.email().trim().toLowerCase()).isPresent()) {
            throw ApiException.conflict("email_taken", "Ya existe un cliente con ese correo.");
        }
        if (customers.existsByTaxId(request.taxId().toUpperCase())) {
            throw ApiException.conflict("tax_id_taken", "Ya existe un cliente con ese CIF/NIF.");
        }

        Customer saved = customers.save(new Customer(
                UUID.randomUUID(),
                request.name().trim(),
                request.email().trim().toLowerCase(),
                request.taxId().trim()));

        return toResponse(saved);
    }

    private CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(customer.getId(), customer.getName(), customer.getEmail(), customer.getTaxId());
    }
}
