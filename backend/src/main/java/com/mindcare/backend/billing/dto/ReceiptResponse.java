package com.mindcare.backend.billing.dto;

import com.mindcare.backend.model.Receipt;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReceiptResponse(
        UUID id,
        String receiptNumber,
        UUID paymentId,
        String invoiceNumber,
        String clientName,
        BigDecimal amount,
        String method,
        Instant paidAt,
        Instant issuedAt
) {
    public static ReceiptResponse from(Receipt receipt) {
        var payment = receipt.getPayment();
        var invoice = payment.getInvoice();
        return new ReceiptResponse(
                receipt.getId(), receipt.getReceiptNumber(), payment.getId(), invoice.getInvoiceNumber(),
                invoice.getClient().getFullName(), payment.getAmount(), payment.getMethod().name(),
                payment.getPaidAt(), receipt.getIssuedAt()
        );
    }
}
