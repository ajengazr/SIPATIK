package com.projek.sipatik.services;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projek.sipatik.models.FinancialEntryOperation;
import com.projek.sipatik.models.FinancialEntryReceipt;
import com.projek.sipatik.models.Pengeluaran;
import com.projek.sipatik.models.Role;
import com.projek.sipatik.models.Users;
import com.projek.sipatik.repositories.FinancialEntryReceiptRepository;
import com.projek.sipatik.repositories.PengeluaranRepository;
import com.projek.sipatik.repositories.UserRepository;

/** Menyatukan entry finansial dan receipt idempotensinya dalam satu transaksi. */
@Service
public class FinancialEntryService {

    private final UserRepository userRepository;
    private final PengeluaranRepository pengeluaranRepository;
    private final FinancialEntryReceiptRepository receiptRepository;
    private final InfakService infakService;

    public FinancialEntryService(
            UserRepository userRepository,
            PengeluaranRepository pengeluaranRepository,
            FinancialEntryReceiptRepository receiptRepository,
            InfakService infakService) {
        this.userRepository = userRepository;
        this.pengeluaranRepository = pengeluaranRepository;
        this.receiptRepository = receiptRepository;
        this.infakService = infakService;
    }

    @Transactional
    public Result saveExpenseBatch(Long adminId, UUID requestKey, List<Pengeluaran> expenses) {
        Users admin = lockAdmin(adminId);
        requireRequestKey(requestKey);
        if (isDuplicate(FinancialEntryOperation.EXPENSE_BATCH, admin.getId(), requestKey)) {
            return Result.duplicate();
        }
        if (expenses == null || expenses.isEmpty()) {
            throw new IllegalArgumentException("Batch pengeluaran tidak boleh kosong.");
        }

        reserveReceipt(FinancialEntryOperation.EXPENSE_BATCH, admin.getId(), requestKey);
        pengeluaranRepository.saveAll(expenses);
        return Result.created(expenses.size());
    }

    @Transactional
    public Result saveManualInfak(
            Long adminId,
            UUID requestKey,
            Long alumniId,
            Long expectedAngkatan,
            String bank,
            Long nominal,
            LocalDate tanggalInfak) {
        Users admin = lockAdmin(adminId);
        requireRequestKey(requestKey);
        if (isDuplicate(FinancialEntryOperation.MANUAL_INFAK, admin.getId(), requestKey)) {
            return Result.duplicate();
        }

        Users alumni = userRepository.findById(alumniId)
                .filter(user -> user.getRole() == Role.USER)
                .orElseThrow(() -> new IllegalArgumentException("Alumni tidak ditemukan atau tidak valid."));
        if (!Objects.equals(alumni.getAngkatan(), expectedAngkatan)) {
            throw new IllegalArgumentException("Angkatan yang dipilih tidak sesuai dengan data alumni.");
        }

        reserveReceipt(FinancialEntryOperation.MANUAL_INFAK, admin.getId(), requestKey);
        // Propagasi default REQUIRED pada InfakService mempertahankan pemrosesan infak
        // dan receipt di transaksi yang sama. Jika validasi/save infak gagal, receipt
        // ikut rollback sehingga request key aman dipakai untuk retry.
        infakService.tambahInfakManual(
                alumni, bank, nominal, tanggalInfak, admin.getEmail());
        return Result.created(1);
    }

    private Users lockAdmin(Long adminId) {
        if (adminId == null) {
            throw new IllegalArgumentException("Sesi admin tidak valid.");
        }
        return userRepository.findByIdForUpdate(adminId)
                .filter(user -> user.getRole() == Role.ADMIN)
                .orElseThrow(() -> new IllegalArgumentException("Sesi admin tidak valid."));
    }

    private void requireRequestKey(UUID requestKey) {
        if (requestKey == null) {
            throw new IllegalArgumentException("Request key wajib diisi.");
        }
    }

    private boolean isDuplicate(FinancialEntryOperation operation, Long adminId, UUID requestKey) {
        return receiptRepository.existsByOperationAndAdminIdAndRequestKey(operation, adminId, requestKey);
    }

    private void reserveReceipt(FinancialEntryOperation operation, Long adminId, UUID requestKey) {
        // Flush membuat receipt benar-benar masuk ke database sebelum entry finansial.
        // RuntimeException setelah titik ini menguji rollback transaksi secara nyata;
        // receipt tidak tertinggal dan key tetap dapat digunakan untuk retry.
        receiptRepository.saveAndFlush(new FinancialEntryReceipt(operation, adminId, requestKey));
    }

    public enum Status {
        CREATED,
        DUPLICATE
    }

    public record Result(Status status, int createdCount) {
        public static Result created(int createdCount) {
            return new Result(Status.CREATED, createdCount);
        }

        public static Result duplicate() {
            return new Result(Status.DUPLICATE, 0);
        }

        public boolean isDuplicate() {
            return status == Status.DUPLICATE;
        }
    }
}
