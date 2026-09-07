package com.mindcare.backend.billing.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record InvoiceItemRequest(
        @NotBlank String description,
        @NotNull @DecimalMin(value = "0.0", inclusive = true) BigDecimal amount,
        @Min(1) Integer quantity
) {
}
