package dev.hazsy.billing.customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customers")
public class Customer {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 160, unique = true)
    private String email;

    @Column(name = "tax_id", nullable = false, length = 20, unique = true)
    private String taxId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Customer() {
        // Para JPA
    }

    public Customer(UUID id, String name, String email, String taxId) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.taxId = taxId.toUpperCase();
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getTaxId() {
        return taxId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
