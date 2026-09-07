package com.mindcare.backend.billing.dto;

import com.mindcare.backend.model.Payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID invoiceId,
        BigDecimal amount,
        String method,
        String reference,
        String recordedByName,
        Instant paidAt
) {
    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(
                p.getId(), p.getInvoice().getId(), p.getAmount(), p.getMethod().name(),
                p.getReference(), p.getRecordedBy().getFullName(), p.getPaidAt()
        );
    }
}
