package com.mindcare.backend.billing;

import com.mindcare.backend.billing.dto.FinanceReport;
import com.mindcare.backend.billing.dto.OperationalReport;
import com.mindcare.backend.model.AppointmentStatus;
import com.mindcare.backend.model.Invoice;
import com.mindcare.backend.model.InvoiceStatus;
import com.mindcare.backend.model.Payment;
import com.mindcare.backend.model.Role;
import com.mindcare.backend.repository.AppointmentRepository;
import com.mindcare.backend.repository.InvoiceRepository;
import com.mindcare.backend.repository.PaymentRepository;
import com.mindcare.backend.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Phase 4 items 21-22: Finance reports and Operational reports, both Finance/Maintenance-only. */
@RestController
@RequestMapping("/api/reports")
@Transactional
@PreAuthorize("hasAnyRole('FINANCE', 'MAINTENANCE')")
public class ReportsController {

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;

    public ReportsController(
            InvoiceRepository invoiceRepository,
            PaymentRepository paymentRepository,
            AppointmentRepository appointmentRepository,
            UserRepository userRepository
    ) {
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.appointmentRepository = appointmentRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/finance")
    public FinanceReport finance() {
        List<Invoice> invoices = invoiceRepository.findAll().stream()
                .filter(i -> i.getStatus() != InvoiceStatus.CANCELLED)
                .toList();

        BigDecimal totalInvoiced = invoices.stream().map(Invoice::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCollected = invoices.stream().map(Invoice::getAmountPaid).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalOutstanding = totalInvoiced.subtract(totalCollected);

        Map<String, Long> invoiceCountByStatus = new LinkedHashMap<>();
        for (InvoiceStatus status : InvoiceStatus.values()) {
            invoiceCountByStatus.put(status.name(), invoiceRepository.countByStatus(status));
        }

        Map<String, BigDecimal> paymentsByMethod = paymentRepository.findAll().stream()
                .collect(Collectors.groupingBy(
                        p -> p.getMethod().name(),
                        LinkedHashMap::new,
                        Collectors.reducing(BigDecimal.ZERO, Payment::getAmount, BigDecimal::add)
                ));

        return new FinanceReport(totalInvoiced, totalCollected, totalOutstanding, invoiceCountByStatus, paymentsByMethod);
    }

    @GetMapping("/operational")
    public OperationalReport operational() {
        long totalClients = userRepository.countByRole(Role.CLIENT);
        long totalTherapists = userRepository.countByRole(Role.THERAPIST);

        Map<String, Long> appointmentCountByStatus = Arrays.stream(AppointmentStatus.values())
                .collect(Collectors.toMap(Enum::name, appointmentRepository::countByStatus, (a, b) -> a, LinkedHashMap::new));

        return new OperationalReport(totalClients, totalTherapists, appointmentCountByStatus);
    }
}
