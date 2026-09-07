package com.mindcare.backend.billing.dto;

import com.mindcare.backend.model.ServiceOffering;

import java.math.BigDecimal;
import java.util.UUID;

public record ServiceOfferingResponse(UUID id, String name, String description, BigDecimal price, boolean active) {
    public static ServiceOfferingResponse from(ServiceOffering s) {
        return new ServiceOfferingResponse(s.getId(), s.getName(), s.getDescription(), s.getPrice(), s.isActive());
    }
}
