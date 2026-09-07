package com.mindcare.backend.billing.dto;

import com.mindcare.backend.model.InvoiceItem;

import java.math.BigDecimal;
import java.util.UUID;

public record InvoiceItemResponse(UUID id, String description, BigDecimal amount, int quantity, BigDecimal lineTotal) {
    public static InvoiceItemResponse from(InvoiceItem item) {
        return new InvoiceItemResponse(item.getId(), item.getDescription(), item.getAmount(), item.getQuantity(), item.lineTotal());
    }
}
