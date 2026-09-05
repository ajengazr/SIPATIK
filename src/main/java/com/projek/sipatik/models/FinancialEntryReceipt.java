package com.projek.sipatik.models;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Bukti bahwa satu submit finansial sudah berhasil disimpan.
 *
 * Constraint mencakup operasi dan admin agar UUID yang sama boleh dipakai oleh
 * admin lain atau form lain, tetapi retry form yang sama tidak menggandakan data.
 */
@Entity
@Getter
@NoArgsConstructor
@Table(name = "financial_entry_receipt", uniqueConstraints = @UniqueConstraint(
        name = "uk_financial_entry_receipt_request",
        columnNames = { "operation", "admin_id", "request_key" }))
public class FinancialEntryReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private FinancialEntryOperation operation;

    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    @Column(name = "request_key", nullable = false)
    private UUID requestKey;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public FinancialEntryReceipt(FinancialEntryOperation operation, Long adminId, UUID requestKey) {
        this.operation = operation;
        this.adminId = adminId;
        this.requestKey = requestKey;
    }

    @PrePersist
    void setCreatedAt() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
