package com.projek.sipatik.models;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(uniqueConstraints = {
    @UniqueConstraint(columnNames = {"tahun","bulan"})
})
public class LaporanKas {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer tahun;
    private Integer bulan;

    // Kas Awal (manual)
    private BigDecimal kasAwalBca;
    private BigDecimal kasAwalMandiri;
    private BigDecimal kasAwalTunai;
    private BigDecimal kasAwalBni;
    private BigDecimal totalKasAwal;

    // Kas Akhir (manual)
    private BigDecimal kasAkhirBca;
    private BigDecimal kasAkhirMandiri;
    private BigDecimal kasAkhirTunai;
    private BigDecimal kasAkhirBni;
    private BigDecimal totalKasAkhir;

    // Pemasukan tambahan (manual)
    private BigDecimal infakLainLain;
    private BigDecimal pendapatanLainLain;

    private LocalDateTime updatedAt;
}