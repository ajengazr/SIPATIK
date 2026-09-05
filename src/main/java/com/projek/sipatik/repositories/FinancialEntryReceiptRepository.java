package com.projek.sipatik.repositories;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projek.sipatik.models.FinancialEntryOperation;
import com.projek.sipatik.models.FinancialEntryReceipt;

public interface FinancialEntryReceiptRepository extends JpaRepository<FinancialEntryReceipt, Long> {

    boolean existsByOperationAndAdminIdAndRequestKey(
            FinancialEntryOperation operation,
            Long adminId,
            UUID requestKey);
}
