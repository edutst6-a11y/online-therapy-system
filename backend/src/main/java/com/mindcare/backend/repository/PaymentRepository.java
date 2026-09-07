package com.mindcare.backend.repository;

import com.mindcare.backend.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findByInvoiceIdOrderByPaidAtDesc(UUID invoiceId);

    List<Payment> findByPaidAtBetween(Instant from, Instant to);
}
