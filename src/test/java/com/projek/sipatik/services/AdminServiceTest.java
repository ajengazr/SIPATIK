package com.projek.sipatik.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.projek.sipatik.models.JenisPengeluaran;
import com.projek.sipatik.models.KategoriBeban;
import com.projek.sipatik.models.LaporanKas;
import com.projek.sipatik.repositories.LaporanKasRepository;
import com.projek.sipatik.repositories.PengeluaranRepository;
import com.projek.sipatik.repositories.SetorInfakRepository;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    private static final int TAHUN = 2026;
    private static final int BULAN = 8;
    private static final LocalDate AWAL_BULAN = LocalDate.of(TAHUN, BULAN, 1);
    private static final LocalDate AKHIR_BULAN = LocalDate.of(TAHUN, BULAN, 31);

    @Mock
    private SetorInfakRepository setorInfakRepo;

    @Mock
    private PengeluaranRepository pengeluaranRepo;

    @Mock
    private LaporanKasRepository laporanKasRepo;

    @InjectMocks
    private AdminService adminService;

    @Test
    void buildDataLaporanKasMenghitungRekonsiliasiTotalDanKanalYangSeimbang() {
        LaporanKas kas = LaporanKas.builder()
                .tahun(TAHUN)
                .bulan(BULAN)
                .kasAwalBca(bd("1000"))
                .kasAwalMandiri(bd("2000"))
                .kasAwalTunai(bd("500"))
                .kasAwalBni(bd("500"))
                .kasAkhirBca(bd("2500"))
                .kasAkhirMandiri(bd("2000"))
                .kasAkhirTunai(bd("600"))
                .kasAkhirBni(bd("700"))
                .infakLainLain(bd("400"))
                .pendapatanLainLain(bd("300"))
                .zis(bd("200"))
                .bungaBank(bd("100"))
                .pemasukanLainTunai(bd("600"))
                .build();
        LaporanKas bulanLalu = LaporanKas.builder()
                .kasAkhirBca(bd("1000"))
                .kasAkhirMandiri(bd("2000"))
                .kasAkhirTunai(bd("500"))
                .kasAkhirBni(bd("500"))
                .build();

        stubLaporan(
                kas,
                bulanLalu,
                List.<Object[]>of(
                        new Object[] { "BCA", 1200L },
                        new Object[] { "MANDIRI", bd("500") },
                        new Object[] { "BNI", 300L }),
                Map.of(
                        KategoriBeban.KETUA_KEAMANAN, bd("400"),
                        KategoriBeban.BANK, bd("300"),
                        KategoriBeban.LAIN_LAIN, bd("500")),
                bd("500"),
                bd("700"),
                bd("800"));

        Map<String, Object> hasil = adminService.buildDataLaporanKas(TAHUN, BULAN);

        Map<String, Object> pemasukan = objectMap(hasil, "pemasukan");
        Map<String, Object> pengeluaran = objectMap(hasil, "pengeluaran");

        assertDecimal(pemasukan.get("totalInfak"), "2000");
        assertDecimal(pemasukan.get("total"), "3000");
        assertDecimal(pengeluaran.get("total"), "1200");
        assertDecimal(pengeluaran.get("kanalBank"), "700");
        assertDecimal(pengeluaran.get("kanalTunai"), "500");
        assertDecimal(pengeluaran.get("kanalTanpaJenis"), "0");
        assertDecimal(hasil.get("kenaikanKas"), "1800");
        assertDecimal(hasil.get("kasAkhirSeharusnya"), "5800");
        assertDecimal(hasil.get("selisihKas"), "0");
        assertDecimal(hasil.get("pemasukanBank"), "2400");
        assertDecimal(hasil.get("pemasukanTunai"), "600");
        assertDecimal(hasil.get("bankSeharusnya"), "5200");
        assertDecimal(hasil.get("tunaiSeharusnya"), "600");
        assertDecimal(hasil.get("selisihBank"), "0");
        assertDecimal(hasil.get("selisihTunai"), "0");
        assertDecimal(hasil.get("infakBankTidakTerpetakan"), "0");
        assertThat(hasil.get("kasSeimbang")).isEqualTo(true);
        assertThat(hasil.get("kanalBisaDicek")).isEqualTo(true);
        assertThat(hasil.get("bankSeimbang")).isEqualTo(true);
        assertThat(hasil.get("tunaiSeimbang")).isEqualTo(true);
        assertThat(hasil.get("kasAwalNyambung")).isEqualTo(true);
    }

    @Test
    void buildDataLaporanKasTidakMengklaimKanalBisaDicekJikaAdaBankTidakTerpetakan() {
        LaporanKas kas = laporanKasNol();
        stubLaporan(
                kas,
                null,
                List.<Object[]>of(new Object[] { "BRI", 100L }),
                Map.of(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO);

        Map<String, Object> hasil = adminService.buildDataLaporanKas(TAHUN, BULAN);

        assertDecimal(hasil.get("infakBankTidakTerpetakan"), "100");
        assertDecimal(objectMap(hasil, "pemasukan").get("totalInfak"), "100");
        assertThat(decimalMap(hasil, "infakPerBank"))
                .containsEntry("BRI", bd("100"));
        assertThat(hasil.get("kanalBisaDicek")).isEqualTo(false);
    }

    @Test
    void buildDataLaporanKasMengelompokkanBankNullSebagaiTidakDiketahui() {
        stubLaporan(
                laporanKasNol(),
                null,
                List.<Object[]>of(new Object[] { null, 75L }),
                Map.of(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO);

        Map<String, Object> hasil = adminService.buildDataLaporanKas(TAHUN, BULAN);

        assertThat(decimalMap(hasil, "infakPerBank"))
                .containsExactly(Map.entry("TIDAK DIKETAHUI", bd("75")));
        assertDecimal(objectMap(hasil, "pemasukan").get("totalInfak"), "75");
        assertDecimal(hasil.get("infakBankTidakTerpetakan"), "75");
        assertThat(hasil.get("kanalBisaDicek")).isEqualTo(false);
    }

    @Test
    void getKasAwalDisarankanMenghitungKomponenDanMengabaikanTotalTersimpanYangUsang() {
        LaporanKas bulanLalu = LaporanKas.builder()
                .kasAkhirBca(bd("10"))
                .kasAkhirMandiri(bd("20"))
                .kasAkhirTunai(bd("30"))
                .kasAkhirBni(bd("40"))
                .totalKasAkhir(bd("999"))
                .build();
        when(laporanKasRepo.findByTahunAndBulan(2026, 7))
                .thenReturn(Optional.of(bulanLalu));

        BigDecimal hasil = adminService.getKasAwalDisarankan(TAHUN, BULAN);

        assertThat(hasil).isEqualByComparingTo("100");
    }

    @Test
    void simpanKasManualMenghitungTotalDariKomponenDanMengubahNullMenjadiNol() {
        when(laporanKasRepo.findByTahunAndBulan(TAHUN, BULAN))
                .thenReturn(Optional.empty());
        when(laporanKasRepo.save(any(LaporanKas.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LaporanKas hasil = adminService.simpanKasManual(
                TAHUN, BULAN,
                bd("100"), bd("200"), null, bd("400"),
                bd("500"), bd("600"), bd("700"), bd("800"),
                bd("100"), bd("200"), bd("300"), bd("400"), bd("600"),
                false);

        assertThat(hasil.getTahun()).isEqualTo(TAHUN);
        assertThat(hasil.getBulan()).isEqualTo(BULAN);
        assertThat(hasil.getKasAwalTunai()).isEqualByComparingTo("0");
        assertThat(hasil.getTotalKasAwal()).isEqualByComparingTo("700");
        assertThat(hasil.getTotalKasAkhir()).isEqualByComparingTo("2600");
        assertThat(hasil.getInfakLainLain()).isEqualByComparingTo("100");
        assertThat(hasil.getPendapatanLainLain()).isEqualByComparingTo("200");
        assertThat(hasil.getZis()).isEqualByComparingTo("300");
        assertThat(hasil.getBungaBank()).isEqualByComparingTo("400");
        assertThat(hasil.getPemasukanLainTunai()).isEqualByComparingTo("600");
        assertThat(hasil.getUpdatedAt()).isNotNull();
        verify(laporanKasRepo).save(hasil);
    }

    @Test
    void simpanKasManualMenolakPemasukanTunaiYangMelebihiPemasukanManual() {
        when(laporanKasRepo.findByTahunAndBulan(TAHUN, BULAN))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.simpanKasManual(
                TAHUN, BULAN,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                bd("100"), bd("200"), bd("300"), bd("999"), bd("601"),
                false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Bagian tunai")
                .hasMessageContaining("600");

        verify(laporanKasRepo, never()).save(any(LaporanKas.class));
    }

    private void stubLaporan(
            LaporanKas kas,
            LaporanKas bulanLalu,
            List<Object[]> infakPerBank,
            Map<KategoriBeban, BigDecimal> pengeluaranPerKategori,
            BigDecimal pengeluaranTunai,
            BigDecimal pengeluaranBank,
            BigDecimal infakManual) {
        when(laporanKasRepo.findByTahunAndBulan(TAHUN, BULAN))
                .thenReturn(Optional.ofNullable(kas));
        when(laporanKasRepo.findByTahunAndBulan(2026, 7))
                .thenReturn(Optional.ofNullable(bulanLalu));
        when(setorInfakRepo.sumConfirmedPerBank(AWAL_BULAN, AKHIR_BULAN))
                .thenReturn(infakPerBank);
        when(setorInfakRepo.totalInfakManualBetween(AWAL_BULAN, AKHIR_BULAN))
                .thenReturn(infakManual);
        when(pengeluaranRepo.totalByKategoriAndRange(
                any(KategoriBeban.class), eq(AWAL_BULAN), eq(AKHIR_BULAN)))
                .thenAnswer(invocation -> pengeluaranPerKategori.getOrDefault(
                        invocation.getArgument(0, KategoriBeban.class), BigDecimal.ZERO));
        when(pengeluaranRepo.totalByJenisAndRange(
                JenisPengeluaran.KAS_TUNAI, AWAL_BULAN, AKHIR_BULAN))
                .thenReturn(pengeluaranTunai);
        when(pengeluaranRepo.totalByJenisAndRange(
                JenisPengeluaran.BANK, AWAL_BULAN, AKHIR_BULAN))
                .thenReturn(pengeluaranBank);
    }

    private static LaporanKas laporanKasNol() {
        return LaporanKas.builder()
                .tahun(TAHUN)
                .bulan(BULAN)
                .kasAwalBca(BigDecimal.ZERO)
                .kasAwalMandiri(BigDecimal.ZERO)
                .kasAwalTunai(BigDecimal.ZERO)
                .kasAwalBni(BigDecimal.ZERO)
                .kasAkhirBca(BigDecimal.ZERO)
                .kasAkhirMandiri(BigDecimal.ZERO)
                .kasAkhirTunai(BigDecimal.ZERO)
                .kasAkhirBni(BigDecimal.ZERO)
                .build();
    }

    private static BigDecimal bd(String nilai) {
        return new BigDecimal(nilai);
    }

    private static void assertDecimal(Object aktual, String ekspektasi) {
        assertThat((BigDecimal) aktual).isEqualByComparingTo(ekspektasi);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> objectMap(Map<String, Object> sumber, String key) {
        return (Map<String, Object>) sumber.get(key);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, BigDecimal> decimalMap(Map<String, Object> sumber, String key) {
        return (Map<String, BigDecimal>) sumber.get(key);
    }
}
