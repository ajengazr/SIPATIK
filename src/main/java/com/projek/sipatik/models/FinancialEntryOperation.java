package com.projek.sipatik.models;

/** Jenis mutasi finansial yang dilindungi oleh idempotency key. */
public enum FinancialEntryOperation {
    EXPENSE_BATCH,
    MANUAL_INFAK
}
