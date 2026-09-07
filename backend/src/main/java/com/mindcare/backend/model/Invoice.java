package com.mindcare.backend.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * SERVICE -> APPOINTMENT -> BILLABLE EVENT -> INVOICE -> PAYMENT -> RECEIPT.
 * An invoice is never just a "paid" boolean: it tracks a running amountPaid
 * against its total and derives status from that, so partial payments are a
 * first-class state rather than something bolted on later.
 */
@Entity
@Table(name = "invoices")
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String invoiceNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private User client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id")
    private Appointment appointment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    private User createdBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvoiceStatus status = InvoiceStatus.UNPAID;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InvoiceItem> items = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    protected Invoice() {
    }

    public Invoice(String invoiceNumber, User client, Appointment appointment, User createdBy, BigDecimal discount) {
        this.invoiceNumber = invoiceNumber;
        this.client = client;
        this.appointment = appointment;
        this.createdBy = createdBy;
        this.discount = discount == null ? BigDecimal.ZERO : discount;
    }

    public UUID getId() {
        return id;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public User getClient() {
        return client;
    }

    public Appointment getAppointment() {
        return appointment;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getDiscount() {
        return discount;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    public List<InvoiceItem> getItems() {
        return items;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** Recomputes subtotal/total from the current line items. Call after adding items. */
    public void recalculateTotals() {
        this.subtotal = items.stream().map(InvoiceItem::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        this.total = subtotal.subtract(discount).max(BigDecimal.ZERO);
        touch();
    }

    /** Applies a recorded payment: bumps amountPaid and derives status from it. */
    public void applyPayment(BigDecimal amount) {
        this.amountPaid = this.amountPaid.add(amount);
        if (this.amountPaid.compareTo(this.total) >= 0) {
            this.status = InvoiceStatus.PAID;
        } else if (this.amountPaid.compareTo(BigDecimal.ZERO) > 0) {
            this.status = InvoiceStatus.PARTIALLY_PAID;
        }
        touch();
    }

    public BigDecimal getBalanceDue() {
        return total.subtract(amountPaid).max(BigDecimal.ZERO);
    }

    public void cancel() {
        this.status = InvoiceStatus.CANCELLED;
        touch();
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }
}
