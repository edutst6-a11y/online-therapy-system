package com.mindcare.backend.billing.dto;

import java.math.BigDecimal;
import java.util.Map;

public record FinanceReport(
        BigDecimal totalInvoiced,
        BigDecimal totalCollected,
        BigDecimal totalOutstanding,
        Map<String, Long> invoiceCountByStatus,
        Map<String, BigDecimal> paymentsByMethod
) {
}
