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

    /**
     * Zakat, infak, sedekah dan pendapatan bunga bank.
     * Dulu keduanya ditampilkan di laporan tapi nilainya ditulis nol di kode, jadi
     * bendahara tidak punya cara mengisinya. Sekarang jadi input manual seperti
     * pemasukan tambahan lainnya.
     */
    private BigDecimal zis;
    private BigDecimal bungaBank;

    /**
     * Bagian dari pemasukan manual (infak lain-lain, pendapatan lain-lain, ZIS) yang
     * diterima sebagai uang tunai, bukan masuk rekening. Dipakai untuk memisahkan
     * rekonsiliasi kanal tunai dari kanal bank; tanpa angka ini, saldo tunai dan saldo
     * bank tidak bisa diuji sendiri-sendiri.
     */
    private BigDecimal pemasukanLainTunai;

    private LocalDateTime updatedAt;
}
