package com.projek.sipatik.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

@ExtendWith(MockitoExtension.class)
class LaporanKasExportServiceTest {

    private static final int TAHUN = 2026;
    private static final int BULAN = 8;

    @Mock
    private AdminService adminService;

    @InjectMocks
    private LaporanKasExportService exportService;

    @Test
    void exportExcelMenghasilkanWorkbookRapiDenganNilaiRupiahNumerik() throws Exception {
        when(adminService.buildDataLaporanKas(TAHUN, BULAN)).thenReturn(reportData());

        byte[] bytes = exportService.exportExcel(TAHUN, BULAN);

        assertThat(bytes).hasSizeGreaterThan(1_000);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
            assertThat(workbook.getProperties().getCoreProperties().getCreator()).isEqualTo("SIPATIK");

            Sheet sheet = workbook.getSheet("Laporan Kas");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("LAPORAN KAS PUB");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("PERIODE AGUSTUS 2026");
            assertThat(sheet.getPaneInformation()).isNotNull();

            Row pemasukan = findRow(sheet, "TOTAL PEMASUKAN");
            assertThat(pemasukan.getCell(1).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(pemasukan.getCell(1).getNumericCellValue()).isEqualTo(3_000_000d);
            assertThat(pemasukan.getCell(1).getCellStyle().getDataFormatString()).contains("Rp");

            Row selisih = findRow(sheet, "Selisih kas");
            assertThat(selisih.getCell(1).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(selisih.getCell(1).getNumericCellValue()).isZero();
            assertThat(selisih.getCell(2).getStringCellValue()).isEqualTo("SEIMBANG");

            Row saldoAkhir = findRow(sheet, "TOTAL KAS AKHIR");
            assertThat(saldoAkhir.getCell(1).getNumericCellValue()).isEqualTo(5_800_000d);
        }
    }

    @Test
    void exportPdfMenghasilkanDokumenYangBisaDibukaDanMemuatRingkasanRekonsiliasi() throws Exception {
        when(adminService.buildDataLaporanKas(TAHUN, BULAN)).thenReturn(reportData());

        byte[] bytes = exportService.exportPdf(TAHUN, BULAN);

        assertThat(new String(bytes, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(bytes).hasSizeGreaterThan(1_000);

        try (PdfReader reader = new PdfReader(bytes)) {
            assertThat(reader.getNumberOfPages()).isGreaterThanOrEqualTo(1);
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            StringBuilder text = new StringBuilder();
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                text.append(extractor.getTextFromPage(page)).append('\n');
            }

            assertThat(text.toString())
                    .contains("LAPORAN KAS PUB")
                    .contains("AGUSTUS 2026")
                    .contains("TOTAL PEMASUKAN")
                    .contains("Rp 3.000.000")
                    .contains("Selisih kas")
                    .contains("SEIMBANG")
                    .contains("Halaman 1");
        }
    }

    @Test
    void exportMenolakPeriodeDiLuarBatasSebelumMembacaData() {
        assertThatThrownBy(() -> exportService.exportExcel(2026, 13))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Bulan");
        assertThatThrownBy(() -> exportService.exportPdf(1999, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tahun");

        verifyNoInteractions(adminService);
    }

    private static Row findRow(Sheet sheet, String label) {
        for (Row row : sheet) {
            if (row.getCell(0) != null && label.equals(row.getCell(0).getStringCellValue())) {
                return row;
            }
        }
        throw new AssertionError("Baris tidak ditemukan: " + label);
    }

    private static Map<String, Object> reportData() {
        Map<String, Object> pemasukan = new HashMap<>();
        pemasukan.put("bca", bd("1200000"));
        pemasukan.put("mandiri", bd("500000"));
        pemasukan.put("bni", bd("300000"));
        pemasukan.put("bankLain", List.of());
        pemasukan.put("totalInfak", bd("2000000"));
        pemasukan.put("lainLain", bd("400000"));
        pemasukan.put("pendapatanLain", bd("300000"));
        pemasukan.put("zis", bd("200000"));
        pemasukan.put("bungaBank", bd("100000"));
        pemasukan.put("tunai", bd("600000"));
        pemasukan.put("total", bd("3000000"));

        Map<String, Object> pengeluaran = new HashMap<>();
        pengeluaran.put("ketuaKeamanan", bd("400000"));
        pengeluaran.put("bendahara", BigDecimal.ZERO);
        pengeluaran.put("sekretaris", BigDecimal.ZERO);
        pengeluaran.put("ppmb", BigDecimal.ZERO);
        pengeluaran.put("pendidikan", BigDecimal.ZERO);
        pengeluaran.put("keasramaan", BigDecimal.ZERO);
        pengeluaran.put("kesejahteraan", BigDecimal.ZERO);
        pengeluaran.put("kesehatan", BigDecimal.ZERO);
        pengeluaran.put("kebersihan", BigDecimal.ZERO);
        pengeluaran.put("magang", BigDecimal.ZERO);
        pengeluaran.put("kerohanian", BigDecimal.ZERO);
        pengeluaran.put("pendidikanManajemen", BigDecimal.ZERO);
        pengeluaran.put("bank", bd("300000"));
        pengeluaran.put("kantor", BigDecimal.ZERO);
        pengeluaran.put("lainLain", bd("500000"));
        pengeluaran.put("total", bd("1200000"));
        pengeluaran.put("kanalBank", bd("700000"));
        pengeluaran.put("kanalTunai", bd("500000"));
        pengeluaran.put("kanalTanpaJenis", BigDecimal.ZERO);

        Map<String, Object> saldoAwal = Map.of(
                "bca", bd("1000000"),
                "mandiri", bd("2000000"),
                "tunai", bd("500000"),
                "bni", bd("500000"),
                "total", bd("4000000"));
        Map<String, Object> saldoAkhir = Map.of(
                "bca", bd("2500000"),
                "mandiri", bd("2000000"),
                "tunai", bd("600000"),
                "bni", bd("700000"),
                "total", bd("5800000"));

        Map<String, Object> data = new HashMap<>();
        data.put("namaBulan", "AGUSTUS");
        data.put("tahun", TAHUN);
        data.put("pemasukan", pemasukan);
        data.put("pengeluaran", pengeluaran);
        data.put("saldoAwal", saldoAwal);
        data.put("saldoAkhir", saldoAkhir);
        data.put("kenaikanKas", bd("1800000"));
        data.put("adaInputKas", true);
        data.put("kasAkhirSeharusnya", bd("5800000"));
        data.put("selisihKas", BigDecimal.ZERO);
        data.put("kasSeimbang", true);
        data.put("kanalBisaDicek", true);
        data.put("pemasukanBank", bd("2400000"));
        data.put("pemasukanTunai", bd("600000"));
        data.put("infakBankTidakTerpetakan", BigDecimal.ZERO);
        data.put("awalBank", bd("3500000"));
        data.put("akhirBank", bd("5200000"));
        data.put("bankSeharusnya", bd("5200000"));
        data.put("tunaiSeharusnya", bd("600000"));
        data.put("selisihBank", BigDecimal.ZERO);
        data.put("selisihTunai", BigDecimal.ZERO);
        data.put("bankSeimbang", true);
        data.put("tunaiSeimbang", true);
        data.put("risikoHitungGanda", false);
        data.put("infakManualPeriodeIni", BigDecimal.ZERO);
        data.put("kasAwalDisarankan", bd("4000000"));
        data.put("kasAwalNyambung", true);
        data.put("namaBulanLalu", "JULI 2026");
        return data;
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
