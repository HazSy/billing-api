package dev.hazsy.billing.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.invoiceId = :invoiceId")
    BigDecimal sumAmountByInvoiceId(@Param("invoiceId") UUID invoiceId);
}
