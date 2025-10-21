package com.projek.sipatik.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projek.sipatik.models.LaporanKas;

public interface LaporanKasRepository extends JpaRepository<LaporanKas, Long> {
    Optional<LaporanKas> findByTahunAndBulan(Integer tahun, Integer bulan);
}