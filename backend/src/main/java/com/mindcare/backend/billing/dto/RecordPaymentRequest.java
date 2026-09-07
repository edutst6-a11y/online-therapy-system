package com.mindcare.backend.billing.dto;

import com.mindcare.backend.model.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record RecordPaymentRequest(
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal amount,
        @NotNull PaymentMethod method,
        String reference
) {
}
