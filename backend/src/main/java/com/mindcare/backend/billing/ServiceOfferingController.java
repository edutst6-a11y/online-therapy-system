package com.mindcare.backend.billing;

import com.mindcare.backend.billing.dto.CreateServiceOfferingRequest;
import com.mindcare.backend.billing.dto.ServiceOfferingResponse;
import com.mindcare.backend.model.ServiceOffering;
import com.mindcare.backend.repository.ServiceOfferingRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The billing catalog Finance draws on when building an invoice. */
@RestController
@RequestMapping("/api/services")
@Transactional
@PreAuthorize("hasAnyRole('FINANCE', 'MAINTENANCE')")
public class ServiceOfferingController {

    private final ServiceOfferingRepository serviceOfferingRepository;

    public ServiceOfferingController(ServiceOfferingRepository serviceOfferingRepository) {
        this.serviceOfferingRepository = serviceOfferingRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceOfferingResponse create(@Valid @RequestBody CreateServiceOfferingRequest request) {
        ServiceOffering service = new ServiceOffering(request.name(), request.description(), request.price());
        serviceOfferingRepository.save(service);
        return ServiceOfferingResponse.from(service);
    }

    @GetMapping
    public List<ServiceOfferingResponse> list() {
        return serviceOfferingRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(ServiceOfferingResponse::from).toList();
    }
}
