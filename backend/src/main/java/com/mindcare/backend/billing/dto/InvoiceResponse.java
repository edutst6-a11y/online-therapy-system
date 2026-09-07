package com.mindcare.backend.billing.dto;

import com.mindcare.backend.model.Invoice;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InvoiceResponse(
        UUID id,
        String invoiceNumber,
        UUID clientId,
        String clientName,
        UUID appointmentId,
        String createdByName,
        String status,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal total,
        BigDecimal amountPaid,
        BigDecimal balanceDue,
        List<InvoiceItemResponse> items,
        Instant createdAt,
        Instant updatedAt
) {
    public static InvoiceResponse from(Invoice invoice) {
        return new InvoiceResponse(
                invoice.getId(), invoice.getInvoiceNumber(), invoice.getClient().getId(), invoice.getClient().getFullName(),
                invoice.getAppointment() != null ? invoice.getAppointment().getId() : null,
                invoice.getCreatedBy().getFullName(), invoice.getStatus().name(),
                invoice.getSubtotal(), invoice.getDiscount(), invoice.getTotal(), invoice.getAmountPaid(), invoice.getBalanceDue(),
                invoice.getItems().stream().map(InvoiceItemResponse::from).toList(),
                invoice.getCreatedAt(), invoice.getUpdatedAt()
        );
    }
}
