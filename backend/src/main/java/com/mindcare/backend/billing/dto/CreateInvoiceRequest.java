package com.mindcare.backend.billing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateInvoiceRequest(
        @NotNull UUID clientId,
        UUID appointmentId,
        @NotEmpty @Valid List<InvoiceItemRequest> items,
        @DecimalMin(value = "0.0", inclusive = true) BigDecimal discount
) {
}
