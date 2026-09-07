package com.mindcare.backend.billing.dto;

import java.util.Map;

public record OperationalReport(
        long totalClients,
        long totalTherapists,
        Map<String, Long> appointmentCountByStatus
) {
}
