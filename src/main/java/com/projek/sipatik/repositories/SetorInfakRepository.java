package com.projek.sipatik.repositories;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.projek.sipatik.models.SetorInfak;
import com.projek.sipatik.models.StatusInfak;
import com.projek.sipatik.models.Users;

import jakarta.persistence.LockModeType;

/**
 * Filter daftar infak memakai Specification, bukan JPQL dengan pola
 * "(:param IS NULL OR ...)". Pola itu menyerahkan penebakan tipe parameter yang
 * bernilai null ke driver database dan gampang pecah saat parameternya berupa enum.
 */
public interface SetorInfakRepository
                extends JpaRepository<SetorInfak, Long>, JpaSpecificationExecutor<SetorInfak> {

        /** Kunci baris selama mutasi supaya request admin paralel tidak saling menimpa. */
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT s FROM SetorInfak s WHERE s.id = :id")
        java.util.Optional<SetorInfak> findByIdForUpdate(@Param("id") Long id);

        // Total uang infak terkonfirmasi by user
        @Query("SELECT COALESCE(SUM(s.nominal),0) FROM SetorInfak s " +
                        "WHERE s.user = :user AND s.status = com.projek.sipatik.models.StatusInfak.DIKONFIRMASI")
        BigDecimal totalInfakTerkonfirmasiByUser(@Param("user") Users user);

        // Jumlah infak terkonfirmasi by user
        @Query("SELECT COUNT(s) FROM SetorInfak s " +
                        "WHERE s.user = :user AND s.status = com.projek.sipatik.models.StatusInfak.DIKONFIRMASI")
        Long jumlahInfakByUser(@Param("user") Users user);

        // Ambil transaksi terakhir (sudah dikonfirmasi)
        SetorInfak findTopByUserAndStatusOrderByTanggalInfakDescIdDesc(Users user, StatusInfak status);

        // Total Infak / Bulan yg terkonfirmasi (fungsi database safe)
        @Query("SELECT COALESCE(SUM(s.nominal),0) FROM SetorInfak s " +
                        "WHERE s.status = com.projek.sipatik.models.StatusInfak.DIKONFIRMASI " +
                        "AND s.tanggalInfak >= :startDate AND s.tanggalInfak <= :endDate")
        BigDecimal totalInfakBulanan(@Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate);

        // Total keseluruhan
        @Query("SELECT COALESCE(SUM(s.nominal),0) FROM SetorInfak s " +
                        "WHERE s.status = com.projek.sipatik.models.StatusInfak.DIKONFIRMASI")
        BigDecimal totalInfakKeseluruhan();

        List<SetorInfak> findByUserAndStatusOrderByTanggalInfakDesc(Users user, StatusInfak status);

        List<SetorInfak> findByUserOrderByTanggalInfakDesc(Users user);

        List<SetorInfak> findByStatus(StatusInfak status);

        boolean existsByUserAndStatus(Users user, StatusInfak status);

        // Untuk laporan: ambil semua pemasukan (terkonfirmasi) dalam range
        @Query("SELECT s FROM SetorInfak s " +
                        "WHERE s.status = com.projek.sipatik.models.StatusInfak.DIKONFIRMASI " +
                        "AND s.tanggalInfak >= :start AND s.tanggalInfak <= :end " +
                        "ORDER BY s.tanggalInfak ASC")
        List<SetorInfak> findConfirmedBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);

        // Rekap pemasukan terkonfirmasi per bank untuk satu periode (dihitung di database)
        @Query("SELECT UPPER(TRIM(s.bank)), COALESCE(SUM(s.nominal),0) FROM SetorInfak s " +
                        "WHERE s.status = com.projek.sipatik.models.StatusInfak.DIKONFIRMASI " +
                        "AND s.tanggalInfak >= :start AND s.tanggalInfak <= :end " +
                        "GROUP BY UPPER(TRIM(s.bank))")
        List<Object[]> sumConfirmedPerBank(@Param("start") LocalDate start, @Param("end") LocalDate end);

        // Deteksi setoran ganda: alumni + tanggal + nominal yang sama dan belum ditolak
        @Query("SELECT COUNT(s) FROM SetorInfak s " +
                        "WHERE s.user = :user AND s.tanggalInfak = :tanggal AND s.nominal = :nominal " +
                        "AND s.status <> com.projek.sipatik.models.StatusInfak.DITOLAK")
        long countKemungkinanDuplikat(@Param("user") Users user,
                        @Param("tanggal") LocalDate tanggal,
                        @Param("nominal") Long nominal);

        long countByUser(Users user);

        /**
         * Total infak terkonfirmasi yang diinput manual oleh admin dalam satu periode.
         * Dipakai untuk memperingatkan kemungkinan hitung ganda dengan kolom manual
         * "infak lain-lain" di laporan kas.
         */
        @Query("SELECT COALESCE(SUM(s.nominal),0) FROM SetorInfak s " +
                        "WHERE s.status = com.projek.sipatik.models.StatusInfak.DIKONFIRMASI " +
                        "AND s.dibuatOleh IS NOT NULL " +
                        "AND s.tanggalInfak >= :start AND s.tanggalInfak <= :end")
        BigDecimal totalInfakManualBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);
}
