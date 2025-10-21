package com.projek.sipatik.repositories;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.projek.sipatik.models.SetorInfak;
import com.projek.sipatik.models.Users;

public interface SetorInfakRepository extends JpaRepository<SetorInfak, Long> {

        // Total uang infak terkonfirmasi by user
        @Query("SELECT COALESCE(SUM(s.nominal),0) FROM SetorInfak s WHERE s.user = :user AND s.dikonfirmasi = true")
        BigDecimal totalInfakTerkonfirmasiByUser(@Param("user") Users user);

        // Jumlah infak terkonfirmasi by user
        @Query("SELECT COUNT(s) FROM SetorInfak s WHERE s.user = :user AND s.dikonfirmasi = true")
        Long jumlahInfakByUser(@Param("user") Users user);

        // Ambil transaksi terakhir (sudah dikonfirmasi)
        SetorInfak findTopByUserAndDikonfirmasiTrueOrderByTanggalInfakDesc(Users user);

        // Total Infak / Bulan yg terkonfirmasi (fungsi database safe)
        @Query("SELECT COALESCE(SUM(s.nominal),0) FROM SetorInfak s " +
                        "WHERE s.dikonfirmasi = true " +
                        "AND s.tanggalInfak >= :startDate AND s.tanggalInfak <= :endDate")
        BigDecimal totalInfakBulanan(@Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate);

        // Total keseluruhan
        @Query("SELECT COALESCE(SUM(s.nominal),0) FROM SetorInfak s WHERE s.dikonfirmasi = true")
        BigDecimal totalInfakKeseluruhan();

        List<SetorInfak> findByUserAndDikonfirmasiTrue(Users user);

        List<SetorInfak> findByDikonfirmasiFalse();

        // FLEX: filter by tanggal range & status (nullable)
        @Query("SELECT s FROM SetorInfak s " +
                        "WHERE (:start IS NULL OR s.tanggalInfak >= :start) " +
                        "AND (:end IS NULL OR s.tanggalInfak <= :end) " +
                        "AND (:status IS NULL OR s.dikonfirmasi = :status) " +
                        "ORDER BY s.tanggalInfak DESC")
        List<SetorInfak> filterByDateAndStatus(@Param("start") LocalDate start,
                        @Param("end") LocalDate end,
                        @Param("status") Boolean status);

        // Untuk laporan: ambil semua pemasukan (terkonfirmasi) dalam range
        @Query("SELECT s FROM SetorInfak s WHERE s.dikonfirmasi = true AND s.tanggalInfak >= :start AND s.tanggalInfak <= :end ORDER BY s.tanggalInfak ASC")
        List<SetorInfak> findConfirmedBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);
}