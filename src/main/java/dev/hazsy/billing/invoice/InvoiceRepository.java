package dev.hazsy.billing.invoice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    @Query("select count(i) from Invoice i where i.number like :prefix%")
    long countByNumberPrefix(@Param("prefix") String prefix);

    List<Invoice> findByStatusOrderByCreatedAtDesc(InvoiceStatus status);

    List<Invoice> findAllByOrderByCreatedAtDesc();

    List<Invoice> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);
}
