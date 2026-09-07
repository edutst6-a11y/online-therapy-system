package com.mindcare.backend.billing;

import com.mindcare.backend.auth.dto.UserResponse;
import com.mindcare.backend.billing.dto.CreateInvoiceRequest;
import com.mindcare.backend.billing.dto.InvoiceResponse;
import com.mindcare.backend.billing.dto.PaymentResponse;
import com.mindcare.backend.billing.dto.ReceiptResponse;
import com.mindcare.backend.billing.dto.RecordPaymentRequest;
import com.mindcare.backend.model.Appointment;
import com.mindcare.backend.model.Invoice;
import com.mindcare.backend.model.InvoiceItem;
import com.mindcare.backend.model.InvoiceStatus;
import com.mindcare.backend.model.NotificationType;
import com.mindcare.backend.model.Payment;
import com.mindcare.backend.model.Receipt;
import com.mindcare.backend.model.Role;
import com.mindcare.backend.model.User;
import com.mindcare.backend.notification.NotificationService;
import com.mindcare.backend.repository.AppointmentRepository;
import com.mindcare.backend.repository.InvoiceRepository;
import com.mindcare.backend.repository.PaymentRepository;
import com.mindcare.backend.repository.ReceiptRepository;
import com.mindcare.backend.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/invoices")
@Transactional
public class InvoiceController {

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final ReceiptRepository receiptRepository;
    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public InvoiceController(
            InvoiceRepository invoiceRepository,
            PaymentRepository paymentRepository,
            ReceiptRepository receiptRepository,
            AppointmentRepository appointmentRepository,
            UserRepository userRepository,
            NotificationService notificationService
    ) {
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.receiptRepository = receiptRepository;
        this.appointmentRepository = appointmentRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('FINANCE', 'MAINTENANCE')")
    public InvoiceResponse create(@Valid @RequestBody CreateInvoiceRequest request, @AuthenticationPrincipal User createdBy) {
        User client = userRepository.findById(request.clientId())
                .orElseThrow(() -> new NoSuchElementException("Client not found"));
        if (client.getRole() != Role.CLIENT) {
            throw new IllegalArgumentException("Invoices can only be issued to a client");
        }

        Appointment appointment = null;
        if (request.appointmentId() != null) {
            appointment = appointmentRepository.findById(request.appointmentId())
                    .orElseThrow(() -> new NoSuchElementException("Appointment not found"));
        }

        Invoice invoice = new Invoice(nextInvoiceNumber(), client, appointment, createdBy, request.discount());
        invoiceRepository.save(invoice);

        for (var itemRequest : request.items()) {
            int quantity = itemRequest.quantity() == null ? 1 : itemRequest.quantity();
            invoice.getItems().add(new InvoiceItem(invoice, itemRequest.description(), itemRequest.amount(), quantity));
        }
        invoice.recalculateTotals();
        invoiceRepository.save(invoice);

        notificationService.notify(client, NotificationType.INVOICE_ISSUED,
                "New invoice " + invoice.getInvoiceNumber(), "Total due: " + invoice.getTotal(), invoice.getId());

        return InvoiceResponse.from(invoice);
    }

    /** So Finance can pick a client when building an invoice, without the wider Maintenance-only /api/staff listing. */
    @GetMapping("/clients")
    @PreAuthorize("hasAnyRole('FINANCE', 'MAINTENANCE')")
    public List<UserResponse> clients() {
        return userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.CLIENT)
                .map(UserResponse::from)
                .toList();
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('FINANCE', 'MAINTENANCE')")
    public List<InvoiceResponse> all() {
        return invoiceRepository.findAllByOrderByCreatedAtDesc().stream().map(InvoiceResponse::from).toList();
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('CLIENT')")
    public List<InvoiceResponse> mine(@AuthenticationPrincipal User client) {
        return invoiceRepository.findByClientIdOrderByCreatedAtDesc(client.getId()).stream().map(InvoiceResponse::from).toList();
    }

    @GetMapping("/client/{clientId}")
    @PreAuthorize("hasAnyRole('FINANCE', 'MAINTENANCE')")
    public List<InvoiceResponse> forClient(@PathVariable UUID clientId) {
        return invoiceRepository.findByClientIdOrderByCreatedAtDesc(clientId).stream().map(InvoiceResponse::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CLIENT', 'FINANCE', 'MAINTENANCE')")
    public InvoiceResponse get(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        Invoice invoice = findOrThrow(id);
        ownInvoiceOrThrow(invoice, user);
        return InvoiceResponse.from(invoice);
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('FINANCE', 'MAINTENANCE')")
    public InvoiceResponse cancel(@PathVariable UUID id) {
        Invoice invoice = findOrThrow(id);
        if (invoice.getAmountPaid().compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalArgumentException("Cannot cancel an invoice that already has payments recorded");
        }
        invoice.cancel();
        invoiceRepository.save(invoice);
        return InvoiceResponse.from(invoice);
    }

    @PostMapping("/{id}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('FINANCE', 'MAINTENANCE')")
    public PaymentResponse recordPayment(@PathVariable UUID id, @Valid @RequestBody RecordPaymentRequest request, @AuthenticationPrincipal User recordedBy) {
        Invoice invoice = findOrThrow(id);
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new IllegalArgumentException("Cannot record a payment against a cancelled invoice");
        }
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new IllegalArgumentException("This invoice is already fully paid");
        }
        if (request.amount().compareTo(invoice.getBalanceDue()) > 0) {
            throw new IllegalArgumentException("Payment exceeds the balance due (" + invoice.getBalanceDue() + ")");
        }

        Payment payment = new Payment(invoice, request.amount(), request.method(), request.reference(), recordedBy);
        paymentRepository.save(payment);

        Receipt receipt = new Receipt(payment, nextReceiptNumber());
        receiptRepository.save(receipt);

        invoice.applyPayment(request.amount());
        invoiceRepository.save(invoice);

        notificationService.notify(invoice.getClient(), NotificationType.PAYMENT_RECEIVED,
                "Payment received", request.amount() + " received against invoice " + invoice.getInvoiceNumber() + ".", invoice.getId());

        return PaymentResponse.from(payment);
    }

    @GetMapping("/{id}/payments")
    @PreAuthorize("hasAnyRole('CLIENT', 'FINANCE', 'MAINTENANCE')")
    public List<PaymentResponse> payments(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        Invoice invoice = findOrThrow(id);
        ownInvoiceOrThrow(invoice, user);
        return paymentRepository.findByInvoiceIdOrderByPaidAtDesc(invoice.getId()).stream().map(PaymentResponse::from).toList();
    }

    @GetMapping("/{invoiceId}/payments/{paymentId}/receipt")
    @PreAuthorize("hasAnyRole('CLIENT', 'FINANCE', 'MAINTENANCE')")
    public ReceiptResponse receipt(@PathVariable UUID invoiceId, @PathVariable UUID paymentId, @AuthenticationPrincipal User user) {
        Invoice invoice = findOrThrow(invoiceId);
        ownInvoiceOrThrow(invoice, user);
        Receipt receipt = receiptRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new NoSuchElementException("Receipt not found"));
        if (!receipt.getPayment().getInvoice().getId().equals(invoiceId)) {
            throw new NoSuchElementException("Receipt not found");
        }
        return ReceiptResponse.from(receipt);
    }

    private void ownInvoiceOrThrow(Invoice invoice, User user) {
        if (user.getRole() == Role.CLIENT && !invoice.getClient().getId().equals(user.getId())) {
            throw new AccessDeniedException("Not your invoice");
        }
    }

    private Invoice findOrThrow(UUID id) {
        return invoiceRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Invoice not found"));
    }

    private String nextInvoiceNumber() {
        return String.format("MC-%06d", invoiceRepository.count() + 1);
    }

    private String nextReceiptNumber() {
        return String.format("RC-%06d", receiptRepository.count() + 1);
    }
}
