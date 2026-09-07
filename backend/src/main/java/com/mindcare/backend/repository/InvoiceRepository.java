package com.mindcare.backend.repository;

import com.mindcare.backend.model.Invoice;
import com.mindcare.backend.model.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    List<Invoice> findByClientIdOrderByCreatedAtDesc(UUID clientId);

    List<Invoice> findAllByOrderByCreatedAtDesc();

    long countByStatus(InvoiceStatus status);
}
