package com.projek.sipatik.repositories;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.projek.sipatik.models.KategoriBeban;
import com.projek.sipatik.models.Pengeluaran;

public interface PengeluaranRepository extends JpaRepository<Pengeluaran, Long> {

    @Query("SELECT COALESCE(SUM(p.nominal),0) FROM Pengeluaran p " +
            "WHERE p.tanggalPengeluaran >= :startDate AND p.tanggalPengeluaran <= :endDate")
    BigDecimal totalPengeluaranPerBulan(@Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("SELECT COALESCE(SUM(p.nominal),0) FROM Pengeluaran p")
    BigDecimal totalPengeluaranKeseluruhan();

    @Query("SELECT COALESCE(SUM(p.nominal),0) FROM Pengeluaran p " +
           "WHERE p.kategori = :kategori " +
           "AND p.tanggalPengeluaran >= :startDate AND p.tanggalPengeluaran <= :endDate")
    BigDecimal totalByKategoriAndRange(@Param("kategori") KategoriBeban kategori,
                                       @Param("startDate") LocalDate startDate,
                                       @Param("endDate") LocalDate endDate);
}