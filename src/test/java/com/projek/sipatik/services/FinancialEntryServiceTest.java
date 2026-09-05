package com.projek.sipatik.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.projek.sipatik.exception.FieldValidationException;
import com.projek.sipatik.models.FinancialEntryOperation;
import com.projek.sipatik.models.JenisPengeluaran;
import com.projek.sipatik.models.KategoriBeban;
import com.projek.sipatik.models.Pengeluaran;
import com.projek.sipatik.models.Role;
import com.projek.sipatik.models.SetorInfak;
import com.projek.sipatik.models.Users;
import com.projek.sipatik.repositories.FinancialEntryReceiptRepository;
import com.projek.sipatik.repositories.PengeluaranRepository;
import com.projek.sipatik.repositories.SetorInfakRepository;
import com.projek.sipatik.repositories.UserRepository;
import com.projek.sipatik.services.FinancialEntryService.Result;
import com.projek.sipatik.services.FinancialEntryService.Status;

@SpringBootTest
class FinancialEntryServiceTest {

    @Autowired
    private FinancialEntryService service;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PengeluaranRepository pengeluaranRepository;
    @Autowired
    private SetorInfakRepository setorInfakRepository;
    @Autowired
    private FinancialEntryReceiptRepository receiptRepository;

    private final List<Long> testUserIds = new ArrayList<>();
    private final List<String> expenseMarkers = new ArrayList<>();

    @AfterEach
    void cleanup() {
        List<SetorInfak> testInfak = setorInfakRepository.findAll().stream()
                .filter(infak -> infak.getUser() != null && testUserIds.contains(infak.getUser().getId()))
                .toList();
        setorInfakRepository.deleteAll(testInfak);

        List<Pengeluaran> testExpenses = pengeluaranRepository.findAll().stream()
                .filter(expense -> expense.getKeterangan() != null
                        && expenseMarkers.stream().anyMatch(expense.getKeterangan()::startsWith))
                .toList();
        pengeluaranRepository.deleteAll(testExpenses);

        receiptRepository.deleteAll(receiptRepository.findAll().stream()
                .filter(receipt -> testUserIds.contains(receipt.getAdminId()))
                .toList());

        // Infak harus dihapus sebelum alumni/admin untuk menghormati foreign key.
        testUserIds.forEach(id -> {
            if (userRepository.existsById(id)) {
                userRepository.deleteById(id);
            }
        });
    }

    @Test
    void duaSubmitParalelDenganKeySamaHanyaMenyimpanSatuBatch() throws Exception {
        Users admin = saveUser(Role.ADMIN, 0L, "Admin paralel");
        UUID requestKey = UUID.randomUUID();
        String marker = marker("parallel");
        expenseMarkers.add(marker);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Result> first = executor.submit(() -> submitExpense(
                    admin.getId(), requestKey, marker, ready, start));
            Future<Result> second = executor.submit(() -> submitExpense(
                    admin.getId(), requestKey, marker, ready, start));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Result> results = List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS));

            assertThat(results).extracting(Result::status)
                    .containsExactlyInAnyOrder(Status.CREATED, Status.DUPLICATE);
            assertThat(results).extracting(Result::createdCount)
                    .containsExactlyInAnyOrder(2, 0);
            assertThat(countExpenses(marker)).isEqualTo(2);
            assertThat(countReceipts(FinancialEntryOperation.EXPENSE_BATCH, admin.getId(), requestKey))
                    .isEqualTo(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void keyBaruTetapMengizinkanEntryIdentikYangMemangDisengaja() {
        Users admin = saveUser(Role.ADMIN, 0L, "Admin key baru");
        String marker = marker("new-key");
        expenseMarkers.add(marker);
        UUID firstKey = UUID.randomUUID();
        UUID secondKey = UUID.randomUUID();

        Result first = service.saveExpenseBatch(admin.getId(), firstKey, List.of(expense(marker)));
        Result second = service.saveExpenseBatch(admin.getId(), secondKey, List.of(expense(marker)));

        assertThat(first.status()).isEqualTo(Status.CREATED);
        assertThat(second.status()).isEqualTo(Status.CREATED);
        assertThat(countExpenses(marker)).isEqualTo(2);
        assertThat(countReceipts(FinancialEntryOperation.EXPENSE_BATCH, admin.getId(), firstKey))
                .isEqualTo(1);
        assertThat(countReceipts(FinancialEntryOperation.EXPENSE_BATCH, admin.getId(), secondKey))
                .isEqualTo(1);
    }

    @Test
    void rollbackMenghapusReceiptSehinggaKeyYangSamaBisaDipakaiUlang() {
        Users admin = saveUser(Role.ADMIN, 0L, "Admin rollback");
        Users alumni = saveUser(Role.USER, 20L, "Alumni rollback");
        alumni.setJenjang("S1");
        userRepository.saveAndFlush(alumni);
        UUID requestKey = UUID.randomUUID();

        // Receipt di-flush sebelum InfakService dipanggil. Bank invalid membuat
        // transaksi gagal setelah flush dan harus me-rollback receipt tersebut.
        assertThatThrownBy(() -> service.saveManualInfak(
                admin.getId(), requestKey, alumni.getId(), 20L,
                "BRI", 800_000L, LocalDate.now()))
                .isInstanceOf(FieldValidationException.class);

        assertThat(countReceipts(FinancialEntryOperation.MANUAL_INFAK, admin.getId(), requestKey))
                .isZero();
        assertThat(setorInfakRepository.countByUser(alumni)).isZero();

        Result retry = service.saveManualInfak(
                admin.getId(), requestKey, alumni.getId(), 20L,
                "BCA", 800_000L, LocalDate.now());

        assertThat(retry.status()).isEqualTo(Status.CREATED);
        assertThat(setorInfakRepository.countByUser(alumni)).isEqualTo(1);
        assertThat(countReceipts(FinancialEntryOperation.MANUAL_INFAK, admin.getId(), requestKey))
                .isEqualTo(1);
    }

    private Result submitExpense(
            Long adminId,
            UUID requestKey,
            String marker,
            CountDownLatch ready,
            CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Test start latch timeout");
        }
        return service.saveExpenseBatch(adminId, requestKey, List.of(
                expense(marker + "-a"),
                expense(marker + "-b")));
    }

    private Users saveUser(Role role, Long angkatan, String nama) {
        String suffix = UUID.randomUUID().toString();
        Users user = Users.builder()
                .nama(nama)
                .email("idempotency-" + suffix + "@example.org")
                .password("test-only")
                .angkatan(angkatan)
                .role(role)
                .build();
        user = userRepository.saveAndFlush(user);
        testUserIds.add(user.getId());
        return user;
    }

    private Pengeluaran expense(String marker) {
        return Pengeluaran.builder()
                .jenis(JenisPengeluaran.BANK)
                .kategori(KategoriBeban.BANK)
                .nominal(123_456L)
                .keterangan(marker)
                .tanggalPengeluaran(LocalDate.of(2001, 1, 1))
                .build();
    }

    private String marker(String scenario) {
        return "idempotency-test-" + scenario + "-" + UUID.randomUUID();
    }

    private long countExpenses(String marker) {
        return pengeluaranRepository.findAll().stream()
                .filter(expense -> expense.getKeterangan() != null
                        && expense.getKeterangan().startsWith(marker))
                .count();
    }

    private long countReceipts(FinancialEntryOperation operation, Long adminId, UUID requestKey) {
        return receiptRepository.findAll().stream()
                .filter(receipt -> receipt.getOperation() == operation)
                .filter(receipt -> adminId.equals(receipt.getAdminId()))
                .filter(receipt -> requestKey.equals(receipt.getRequestKey()))
                .count();
    }
}
