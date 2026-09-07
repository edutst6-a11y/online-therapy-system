package com.mindcare.backend.repository;

import com.mindcare.backend.model.Receipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReceiptRepository extends JpaRepository<Receipt, UUID> {

    Optional<Receipt> findByPaymentId(UUID paymentId);
}
