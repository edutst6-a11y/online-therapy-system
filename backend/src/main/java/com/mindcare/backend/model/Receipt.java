package com.mindcare.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Issued automatically alongside every recorded Payment — a receipt is proof a specific payment happened. */
@Entity
@Table(name = "receipts")
public class Receipt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, unique = true)
    private Payment payment;

    @Column(nullable = false, unique = true)
    private String receiptNumber;

    @Column(nullable = false, updatable = false)
    private Instant issuedAt = Instant.now();

    protected Receipt() {
    }

    public Receipt(Payment payment, String receiptNumber) {
        this.payment = payment;
        this.receiptNumber = receiptNumber;
    }

    public UUID getId() {
        return id;
    }

    public Payment getPayment() {
        return payment;
    }

    public String getReceiptNumber() {
        return receiptNumber;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }
}
