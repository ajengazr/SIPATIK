package com.projek.sipatik.services;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

/**
 * Membuat unduhan laporan kas dari data yang sama dengan halaman laporan web.
 *
 * <p>Perhitungan tetap menjadi tanggung jawab {@link AdminService}. Service ini
 * hanya mengubah hasil perhitungan tersebut ke format Excel atau PDF, sehingga
 * angka di halaman dan di berkas unduhan tidak mempunyai rumus yang berbeda.</p>
 */
@Service
public class LaporanKasExportService {

    private static final Locale LOCALE_ID = Locale.forLanguageTag("id-ID");
    private static final Color PDF_BIRU = new Color(30, 64, 175);
    private static final Color PDF_ABU = new Color(243, 244, 246);
    private static final Color PDF_GARIS = new Color(209, 213, 219);
    private static final Color PDF_HIJAU = new Color(22, 101, 52);
    private static final Color PDF_MERAH = new Color(185, 28, 28);
    private static final Color PDF_KUNING = new Color(146, 64, 14);

    private final AdminService adminService;

    public LaporanKasExportService(AdminService adminService) {
        this.adminService = adminService;
    }

    public byte[] exportExcel(int tahun, int bulan) {
        Report report = buildReport(tahun, bulan);

        try (XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Laporan Kas");
            ExcelStyles styles = createExcelStyles(workbook);

            workbook.getProperties().getCoreProperties().setTitle(report.judul());
            workbook.getProperties().getCoreProperties().setCreator("SIPATIK");

            int rowIndex = 0;
            Row titleRow = sheet.createRow(rowIndex++);
            titleRow.setHeightInPoints(27);
            createTextCell(titleRow, 0, "LAPORAN KAS PUB", styles.title());
            mergeAndStyle(sheet, titleRow, 0, 2, styles.title());

            Row periodRow = sheet.createRow(rowIndex++);
            periodRow.setHeightInPoints(22);
            createTextCell(periodRow, 0, "PERIODE " + report.periode(), styles.subtitle());
            mergeAndStyle(sheet, periodRow, 0, 2, styles.subtitle());

            Row scopeRow = sheet.createRow(rowIndex++);
            scopeRow.setHeightInPoints(19);
            createTextCell(scopeRow, 0,
                    "Sumber: transaksi terkonfirmasi, pengeluaran, dan saldo kas yang tersimpan di SIPATIK.",
                    styles.scope());
            mergeAndStyle(sheet, scopeRow, 0, 2, styles.scope());

            Row headerRow = sheet.createRow(rowIndex++);
            headerRow.setHeightInPoints(22);
            createTextCell(headerRow, 0, "Uraian", styles.columnHeader());
            createTextCell(headerRow, 1, "Nilai (Rp)", styles.columnHeader());
            createTextCell(headerRow, 2, "Keterangan", styles.columnHeader());

            for (ReportSection section : report.sections()) {
                Row sectionRow = sheet.createRow(rowIndex++);
                sectionRow.setHeightInPoints(22);
                createTextCell(sectionRow, 0, section.title(), styles.section());
                mergeAndStyle(sheet, sectionRow, 0, 2, styles.section());

                for (ReportRow reportRow : section.rows()) {
                    Row row = sheet.createRow(rowIndex++);
                    row.setHeightInPoints(reportRow.note() == null ? 20 : 29);

                    createTextCell(row, 0, reportRow.label(),
                            reportRow.emphasized() ? styles.labelTotal() : styles.label());

                    Cell valueCell = row.createCell(1);
                    valueCell.setCellStyle(reportRow.emphasized() ? styles.moneyTotal() : styles.money());
                    if (reportRow.amount() != null) {
                        // Nilai sengaja ditulis sebagai numeric cell, bukan string berformat rupiah.
                        valueCell.setCellValue(reportRow.amount().doubleValue());
                    }

                    createTextCell(row, 2, reportRow.note() == null ? "" : reportRow.note(),
                            switch (reportRow.tone()) {
                                case GOOD -> styles.statusGood();
                                case WARNING -> styles.statusWarning();
                                case DANGER -> styles.statusDanger();
                                case NEUTRAL -> styles.note();
                            });
                }
            }

            sheet.setColumnWidth(0, 51 * 256);
            sheet.setColumnWidth(1, 20 * 256);
            sheet.setColumnWidth(2, 38 * 256);
            sheet.createFreezePane(0, 4);
            sheet.setRepeatingRows(new CellRangeAddress(0, 3, -1, -1));
            sheet.setAutobreaks(true);
            sheet.setFitToPage(true);
            sheet.getPrintSetup().setFitWidth((short) 1);
            sheet.getPrintSetup().setFitHeight((short) 0);
            sheet.getPrintSetup().setPaperSize(PrintSetup.A4_PAPERSIZE);
            sheet.getFooter().setLeft("SIPATIK");
            sheet.getFooter().setCenter("Laporan Kas " + report.periode());
            sheet.getFooter().setRight("Halaman &P dari &N");

            workbook.write(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Gagal membuat laporan kas Excel", e);
        }
    }

    public byte[] exportPdf(int tahun, int bulan) {
        Report report = buildReport(tahun, bulan);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 42, 42);

        try {
            PdfWriter writer = PdfWriter.getInstance(document, output);
            writer.setPageEvent(new PageNumberEvent(report.periode()));
            document.addTitle(report.judul());
            document.addAuthor("SIPATIK");
            document.addSubject("Laporan kas bulanan PUB");
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, PDF_BIRU);
            Font periodFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.DARK_GRAY);
            Font scopeFont = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);

            Paragraph title = new Paragraph("LAPORAN KAS PUB", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(4);
            document.add(title);

            Paragraph period = new Paragraph("PERIODE " + report.periode(), periodFont);
            period.setAlignment(Element.ALIGN_CENTER);
            period.setSpacingAfter(5);
            document.add(period);

            Paragraph scope = new Paragraph(
                    "Sumber: transaksi terkonfirmasi, pengeluaran, dan saldo kas yang tersimpan di SIPATIK.",
                    scopeFont);
            scope.setAlignment(Element.ALIGN_CENTER);
            scope.setSpacingAfter(14);
            document.add(scope);

            PdfPTable table = new PdfPTable(new float[] { 5.1f, 1.65f, 2.75f });
            table.setWidthPercentage(100);
            table.setHeaderRows(1);
            table.setSplitLate(false);
            table.setSpacingAfter(8);

            addPdfHeaderCell(table, "Uraian", Element.ALIGN_LEFT);
            addPdfHeaderCell(table, "Nilai (Rp)", Element.ALIGN_RIGHT);
            addPdfHeaderCell(table, "Keterangan", Element.ALIGN_LEFT);

            for (ReportSection section : report.sections()) {
                addPdfSectionCell(table, section.title());
                for (ReportRow row : section.rows()) {
                    addPdfTextCell(table, row.label(), row.emphasized());
                    addPdfAmountCell(table, row.amount(), row.emphasized());
                    addPdfNoteCell(table, row.note(), row.tone(), row.emphasized());
                }
            }

            document.add(table);
            document.close();
            return output.toByteArray();
        } catch (DocumentException e) {
            if (document.isOpen()) {
                document.close();
            }
            throw new IllegalStateException("Gagal membuat laporan kas PDF", e);
        }
    }

    public String excelFilename(int tahun, int bulan) {
        validatePeriod(tahun, bulan);
        return String.format(Locale.ROOT, "laporan-kas-%04d-%02d.xlsx", tahun, bulan);
    }

    public String pdfFilename(int tahun, int bulan) {
        validatePeriod(tahun, bulan);
        return String.format(Locale.ROOT, "laporan-kas-%04d-%02d.pdf", tahun, bulan);
    }

    private Report buildReport(int tahun, int bulan) {
        validatePeriod(tahun, bulan);
        Map<String, Object> data = adminService.buildDataLaporanKas(tahun, bulan);
        Map<String, Object> pemasukan = objectMap(data, "pemasukan");
        Map<String, Object> pengeluaran = objectMap(data, "pengeluaran");
        Map<String, Object> saldoAwal = objectMap(data, "saldoAwal");
        Map<String, Object> saldoAkhir = objectMap(data, "saldoAkhir");

        List<ReportSection> sections = new ArrayList<>();

        List<ReportRow> pemasukanRows = new ArrayList<>();
        pemasukanRows.add(amountRow("Infak alumni melalui BCA", pemasukan, "bca"));
        pemasukanRows.add(amountRow("Infak alumni melalui Mandiri", pemasukan, "mandiri"));
        pemasukanRows.add(amountRow("Infak alumni melalui BNI", pemasukan, "bni"));
        for (Map<String, Object> bank : objectList(pemasukan.get("bankLain"))) {
            pemasukanRows.add(amountRow("Infak alumni melalui " + text(bank.get("bank")), bank, "jumlah"));
        }
        pemasukanRows.add(totalRow("Total infak alumni", decimal(pemasukan.get("totalInfak")), null,
                Tone.NEUTRAL));
        pemasukanRows.add(amountRow("Infak lain-lain", pemasukan, "lainLain"));
        pemasukanRows.add(amountRow("Pendapatan lain-lain", pemasukan, "pendapatanLain"));
        pemasukanRows.add(amountRow("Zakat, infak, sedekah", pemasukan, "zis"));
        pemasukanRows.add(amountRow("Pendapatan bunga bank", pemasukan, "bungaBank"));
        pemasukanRows.add(totalRow("TOTAL PEMASUKAN", decimal(pemasukan.get("total")), null, Tone.NEUTRAL));
        sections.add(new ReportSection("PEMASUKAN", pemasukanRows));

        List<ReportRow> pengeluaranRows = new ArrayList<>();
        pengeluaranRows.add(amountRow("Beban Ketua/Keamanan", pengeluaran, "ketuaKeamanan"));
        pengeluaranRows.add(amountRow("Beban Bendahara", pengeluaran, "bendahara"));
        pengeluaranRows.add(amountRow("Beban Sekretaris", pengeluaran, "sekretaris"));
        pengeluaranRows.add(amountRow("Beban PPMB PUB", pengeluaran, "ppmb"));
        pengeluaranRows.add(amountRow("Beban Divisi Pendidikan", pengeluaran, "pendidikan"));
        pengeluaranRows.add(amountRow("Beban Divisi Keasramaan", pengeluaran, "keasramaan"));
        pengeluaranRows.add(amountRow("Beban Divisi Kesejahteraan", pengeluaran, "kesejahteraan"));
        pengeluaranRows.add(amountRow("Beban Divisi Kesehatan", pengeluaran, "kesehatan"));
        pengeluaranRows.add(amountRow("Beban Divisi Kebersihan", pengeluaran, "kebersihan"));
        pengeluaranRows.add(amountRow("Beban Divisi Magang", pengeluaran, "magang"));
        pengeluaranRows.add(amountRow("Beban Divisi Kerohanian", pengeluaran, "kerohanian"));
        pengeluaranRows.add(amountRow("Beban Pendidikan Manajemen", pengeluaran, "pendidikanManajemen"));
        pengeluaranRows.add(amountRow("Beban Bank", pengeluaran, "bank"));
        pengeluaranRows.add(amountRow("Beban Kantor PUB", pengeluaran, "kantor"));
        pengeluaranRows.add(amountRow("Beban lain-lain", pengeluaran, "lainLain"));
        pengeluaranRows.add(totalRow("TOTAL PENGELUARAN", decimal(pengeluaran.get("total")), null,
                Tone.NEUTRAL));
        sections.add(new ReportSection("PENGELUARAN", pengeluaranRows));

        boolean adaInputKas = bool(data.get("adaInputKas"));
        boolean kasSeimbang = bool(data.get("kasSeimbang"));
        String statusKas = !adaInputKas ? "BELUM DICEK - saldo kas belum diinput"
                : kasSeimbang ? "SEIMBANG" : "PERLU DICEK";
        Tone toneKas = !adaInputKas ? Tone.WARNING : kasSeimbang ? Tone.GOOD : Tone.DANGER;

        sections.add(new ReportSection("RINGKASAN DAN REKONSILIASI TOTAL", List.of(
                totalRow("Total pemasukan", decimal(pemasukan.get("total")), null, Tone.NEUTRAL),
                totalRow("Total pengeluaran", decimal(pengeluaran.get("total")), null, Tone.NEUTRAL),
                totalRow("Kenaikan/penurunan kas", decimal(data.get("kenaikanKas")), null, Tone.NEUTRAL),
                amountRow("Total kas awal", saldoAwal, "total"),
                amountRow("Kas akhir seharusnya (kas awal + kenaikan)", data, "kasAkhirSeharusnya"),
                amountRow("Kas akhir menurut input", saldoAkhir, "total"),
                totalRow("Selisih kas", decimal(data.get("selisihKas")), statusKas, toneKas))));

        boolean kanalBisaDicek = bool(data.get("kanalBisaDicek"));
        boolean bankSeimbang = bool(data.get("bankSeimbang"));
        boolean tunaiSeimbang = bool(data.get("tunaiSeimbang"));
        String statusBank = statusKanal(kanalBisaDicek, bankSeimbang);
        String statusTunai = statusKanal(kanalBisaDicek, tunaiSeimbang);
        Tone toneBank = toneKanal(kanalBisaDicek, bankSeimbang);
        Tone toneTunai = toneKanal(kanalBisaDicek, tunaiSeimbang);

        sections.add(new ReportSection("REKONSILIASI PER KANAL", List.of(
                amountRow("Pemasukan melalui bank", data, "pemasukanBank"),
                amountRow("Pemasukan tunai", data, "pemasukanTunai"),
                amountRow("Pengeluaran melalui bank", pengeluaran, "kanalBank"),
                amountRow("Pengeluaran tunai", pengeluaran, "kanalTunai"),
                warningAmountRow("Pengeluaran tanpa jenis kanal", decimal(pengeluaran.get("kanalTanpaJenis"))),
                warningAmountRow("Infak bank tanpa kolom saldo", decimal(data.get("infakBankTidakTerpetakan"))),
                amountRow("Kas bank awal", data, "awalBank"),
                amountRow("Kas bank akhir seharusnya", data, "bankSeharusnya"),
                amountRow("Kas bank akhir menurut input", data, "akhirBank"),
                totalRow("Selisih kas bank", decimal(data.get("selisihBank")), statusBank, toneBank),
                amountRow("Kas tunai awal", saldoAwal, "tunai"),
                amountRow("Kas tunai akhir seharusnya", data, "tunaiSeharusnya"),
                amountRow("Kas tunai akhir menurut input", saldoAkhir, "tunai"),
                totalRow("Selisih kas tunai", decimal(data.get("selisihTunai")), statusTunai, toneTunai))));

        List<ReportRow> saldoRows = new ArrayList<>();
        saldoRows.add(amountRow("Saldo awal BCA", saldoAwal, "bca"));
        saldoRows.add(amountRow("Saldo awal Mandiri", saldoAwal, "mandiri"));
        saldoRows.add(amountRow("Saldo awal Tunai", saldoAwal, "tunai"));
        saldoRows.add(amountRow("Saldo awal BNI", saldoAwal, "bni"));
        saldoRows.add(totalRow("TOTAL KAS AWAL", decimal(saldoAwal.get("total")), null, Tone.NEUTRAL));
        saldoRows.add(amountRow("Saldo akhir BCA", saldoAkhir, "bca"));
        saldoRows.add(amountRow("Saldo akhir Mandiri", saldoAkhir, "mandiri"));
        saldoRows.add(amountRow("Saldo akhir Tunai", saldoAkhir, "tunai"));
        saldoRows.add(amountRow("Saldo akhir BNI", saldoAkhir, "bni"));
        saldoRows.add(totalRow("TOTAL KAS AKHIR", decimal(saldoAkhir.get("total")), null, Tone.NEUTRAL));
        sections.add(new ReportSection("SALDO PER REKENING DAN KANAL", saldoRows));

        List<ReportRow> kontrolRows = new ArrayList<>();
        Object kasAwalDisarankan = data.get("kasAwalDisarankan");
        if (kasAwalDisarankan == null) {
            kontrolRows.add(textRow("Sambungan saldo antarbulan",
                    "Belum ada saldo akhir bulan sebelumnya", Tone.NEUTRAL));
        } else {
            boolean kasAwalNyambung = bool(data.get("kasAwalNyambung"));
            kontrolRows.add(totalRow(
                    "Kas awal yang disarankan dari " + text(data.get("namaBulanLalu")),
                    decimal(kasAwalDisarankan),
                    !adaInputKas ? "BELUM DICEK" : kasAwalNyambung ? "NYAMBUNG" : "PERLU DICEK",
                    !adaInputKas ? Tone.WARNING : kasAwalNyambung ? Tone.GOOD : Tone.DANGER));
        }

        boolean risikoHitungGanda = bool(data.get("risikoHitungGanda"));
        kontrolRows.add(new ReportRow(
                "Infak manual yang sudah masuk total infak alumni",
                decimal(data.get("infakManualPeriodeIni")),
                risikoHitungGanda ? "RISIKO HITUNG GANDA - periksa infak lain-lain" : "Tidak terdeteksi hitung ganda",
                false,
                risikoHitungGanda ? Tone.DANGER : Tone.GOOD));
        sections.add(new ReportSection("CATATAN KONTROL", kontrolRows));

        String namaBulan = text(data.get("namaBulan"));
        String periode = (namaBulan.isBlank() ? String.format(Locale.ROOT, "%02d", bulan) : namaBulan)
                + " " + tahun;
        return new Report("Laporan Kas PUB - " + periode, periode, sections);
    }

    private static ExcelStyles createExcelStyles(XSSFWorkbook workbook) {
        org.apache.poi.ss.usermodel.Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 16);
        titleFont.setColor(IndexedColors.DARK_BLUE.getIndex());

        CellStyle title = workbook.createCellStyle();
        title.setFont(titleFont);
        title.setAlignment(HorizontalAlignment.LEFT);
        title.setVerticalAlignment(VerticalAlignment.CENTER);

        org.apache.poi.ss.usermodel.Font subtitleFont = workbook.createFont();
        subtitleFont.setBold(true);
        subtitleFont.setFontHeightInPoints((short) 11);
        subtitleFont.setColor(IndexedColors.GREY_80_PERCENT.getIndex());

        CellStyle subtitle = workbook.createCellStyle();
        subtitle.setFont(subtitleFont);
        subtitle.setVerticalAlignment(VerticalAlignment.CENTER);

        org.apache.poi.ss.usermodel.Font scopeFont = workbook.createFont();
        scopeFont.setItalic(true);
        scopeFont.setFontHeightInPoints((short) 9);
        scopeFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());

        CellStyle scope = workbook.createCellStyle();
        scope.setFont(scopeFont);
        scope.setVerticalAlignment(VerticalAlignment.CENTER);

        org.apache.poi.ss.usermodel.Font whiteBold = workbook.createFont();
        whiteBold.setBold(true);
        whiteBold.setColor(IndexedColors.WHITE.getIndex());

        CellStyle columnHeader = workbook.createCellStyle();
        columnHeader.setFont(whiteBold);
        columnHeader.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        columnHeader.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        columnHeader.setAlignment(HorizontalAlignment.CENTER);
        columnHeader.setVerticalAlignment(VerticalAlignment.CENTER);
        setThinBorder(columnHeader);

        CellStyle section = workbook.createCellStyle();
        section.setFont(whiteBold);
        section.setFillForegroundColor(IndexedColors.BLUE_GREY.getIndex());
        section.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        section.setVerticalAlignment(VerticalAlignment.CENTER);
        setThinBorder(section);

        org.apache.poi.ss.usermodel.Font normalFont = workbook.createFont();
        normalFont.setFontHeightInPoints((short) 10);

        org.apache.poi.ss.usermodel.Font boldFont = workbook.createFont();
        boldFont.setFontHeightInPoints((short) 10);
        boldFont.setBold(true);

        CellStyle label = workbook.createCellStyle();
        label.setFont(normalFont);
        label.setVerticalAlignment(VerticalAlignment.CENTER);
        setThinBorder(label);

        CellStyle labelTotal = workbook.createCellStyle();
        labelTotal.cloneStyleFrom(label);
        labelTotal.setFont(boldFont);
        labelTotal.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        labelTotal.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        short moneyFormat = workbook.createDataFormat()
                .getFormat("\"Rp\" #,##0.00;[Red]-\"Rp\" #,##0.00");

        CellStyle money = workbook.createCellStyle();
        money.setFont(normalFont);
        money.setDataFormat(moneyFormat);
        money.setAlignment(HorizontalAlignment.RIGHT);
        money.setVerticalAlignment(VerticalAlignment.CENTER);
        setThinBorder(money);

        CellStyle moneyTotal = workbook.createCellStyle();
        moneyTotal.cloneStyleFrom(money);
        moneyTotal.setFont(boldFont);
        moneyTotal.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        moneyTotal.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        CellStyle note = workbook.createCellStyle();
        note.setFont(normalFont);
        note.setWrapText(true);
        note.setVerticalAlignment(VerticalAlignment.CENTER);
        setThinBorder(note);

        CellStyle statusGood = workbook.createCellStyle();
        statusGood.cloneStyleFrom(note);
        org.apache.poi.ss.usermodel.Font greenFont = workbook.createFont();
        greenFont.setBold(true);
        greenFont.setColor(IndexedColors.GREEN.getIndex());
        statusGood.setFont(greenFont);

        CellStyle statusWarning = workbook.createCellStyle();
        statusWarning.cloneStyleFrom(note);
        org.apache.poi.ss.usermodel.Font orangeFont = workbook.createFont();
        orangeFont.setBold(true);
        orangeFont.setColor(IndexedColors.DARK_YELLOW.getIndex());
        statusWarning.setFont(orangeFont);

        CellStyle statusDanger = workbook.createCellStyle();
        statusDanger.cloneStyleFrom(note);
        org.apache.poi.ss.usermodel.Font redFont = workbook.createFont();
        redFont.setBold(true);
        redFont.setColor(IndexedColors.DARK_RED.getIndex());
        statusDanger.setFont(redFont);

        return new ExcelStyles(title, subtitle, scope, columnHeader, section, label, labelTotal,
                money, moneyTotal, note, statusGood, statusWarning, statusDanger);
    }

    private static void createTextCell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        // Jangan menulis shared-string kosong. Selain memperbesar workbook, beberapa
        // pembaca XLSX menampilkan indeks shared string alih-alih sel kosongnya.
        if (value != null && !value.isEmpty()) {
            cell.setCellValue(value);
        }
        cell.setCellStyle(style);
    }

    private static void mergeAndStyle(org.apache.poi.ss.usermodel.Sheet sheet, Row row,
            int firstColumn, int lastColumn, CellStyle style) {
        for (int column = firstColumn; column <= lastColumn; column++) {
            Cell cell = row.getCell(column);
            if (cell == null) {
                cell = row.createCell(column);
            }
            cell.setCellStyle(style);
        }
        sheet.addMergedRegion(new CellRangeAddress(row.getRowNum(), row.getRowNum(), firstColumn, lastColumn));
    }

    private static void setThinBorder(CellStyle style) {
        style.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setBorderBottom(BorderStyle.THIN);
    }

    private static void addPdfHeaderCell(PdfPTable table, String value, int alignment) {
        Font font = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
        PdfPCell cell = new PdfPCell(new Phrase(value, font));
        cell.setBackgroundColor(PDF_BIRU);
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(7);
        cell.setBorderColor(PDF_BIRU);
        table.addCell(cell);
    }

    private static void addPdfSectionCell(PdfPTable table, String title) {
        Font font = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
        PdfPCell cell = new PdfPCell(new Phrase(title, font));
        cell.setColspan(3);
        cell.setBackgroundColor(PDF_BIRU);
        cell.setPadding(6);
        cell.setBorderColor(PDF_BIRU);
        table.addCell(cell);
    }

    private static void addPdfTextCell(PdfPTable table, String value, boolean emphasized) {
        Font font = FontFactory.getFont(
                emphasized ? FontFactory.HELVETICA_BOLD : FontFactory.HELVETICA,
                8,
                Color.DARK_GRAY);
        PdfPCell cell = new PdfPCell(new Phrase(value, font));
        stylePdfBodyCell(cell, emphasized);
        table.addCell(cell);
    }

    private static void addPdfAmountCell(PdfPTable table, BigDecimal value, boolean emphasized) {
        Font font = FontFactory.getFont(
                emphasized ? FontFactory.HELVETICA_BOLD : FontFactory.HELVETICA,
                8,
                value != null && value.signum() < 0 ? PDF_MERAH : Color.DARK_GRAY);
        PdfPCell cell = new PdfPCell(new Phrase(value == null ? "" : "Rp " + formatAmount(value), font));
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        stylePdfBodyCell(cell, emphasized);
        table.addCell(cell);
    }

    private static void addPdfNoteCell(PdfPTable table, String value, Tone tone, boolean emphasized) {
        Color color = switch (tone) {
            case GOOD -> PDF_HIJAU;
            case WARNING -> PDF_KUNING;
            case DANGER -> PDF_MERAH;
            case NEUTRAL -> Color.DARK_GRAY;
        };
        Font font = FontFactory.getFont(
                tone == Tone.NEUTRAL && !emphasized ? FontFactory.HELVETICA : FontFactory.HELVETICA_BOLD,
                7.5f,
                color);
        PdfPCell cell = new PdfPCell(new Phrase(value == null ? "" : value, font));
        stylePdfBodyCell(cell, emphasized);
        table.addCell(cell);
    }

    private static void stylePdfBodyCell(PdfPCell cell, boolean emphasized) {
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(5);
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderWidth(0.35f);
        cell.setBorderColor(PDF_GARIS);
        if (emphasized) {
            cell.setBackgroundColor(PDF_ABU);
        }
    }

    private static String formatAmount(BigDecimal value) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(LOCALE_ID);
        DecimalFormat format = new DecimalFormat("#,##0.##", symbols);
        format.setParseBigDecimal(true);
        return format.format(value);
    }

    private static ReportRow amountRow(String label, Map<String, Object> values, String key) {
        return new ReportRow(label, decimal(values.get(key)), null, false, Tone.NEUTRAL);
    }

    private static ReportRow totalRow(String label, BigDecimal amount, String note, Tone tone) {
        return new ReportRow(label, amount, note, true, tone);
    }

    private static ReportRow warningAmountRow(String label, BigDecimal amount) {
        boolean warning = amount.signum() != 0;
        return new ReportRow(label, amount, warning ? "PERLU DICEK" : "Tidak ada", false,
                warning ? Tone.WARNING : Tone.GOOD);
    }

    private static ReportRow textRow(String label, String note, Tone tone) {
        return new ReportRow(label, null, note, false, tone);
    }

    private static String statusKanal(boolean bisaDicek, boolean seimbang) {
        if (!bisaDicek) {
            return "BELUM BISA DICEK";
        }
        return seimbang ? "SEIMBANG" : "PERLU DICEK";
    }

    private static Tone toneKanal(boolean bisaDicek, boolean seimbang) {
        if (!bisaDicek) {
            return Tone.WARNING;
        }
        return seimbang ? Tone.GOOD : Tone.DANGER;
    }

    private static void validatePeriod(int tahun, int bulan) {
        if (bulan < 1 || bulan > 12) {
            throw new IllegalArgumentException("Bulan harus antara 1 sampai 12.");
        }
        if (tahun < 2000 || tahun > 2100) {
            throw new IllegalArgumentException("Tahun tidak valid.");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> objectMap(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (!(value instanceof Map<?, ?>)) {
            throw new IllegalStateException("Data laporan tidak lengkap: " + key);
        }
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> objectList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?>)) {
            throw new IllegalStateException("Data bank lain pada laporan tidak valid");
        }
        return (List<Map<String, Object>>) value;
    }

    private static BigDecimal decimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return new BigDecimal(value.toString());
    }

    private static boolean bool(Object value) {
        return Boolean.TRUE.equals(value);
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private enum Tone {
        NEUTRAL,
        GOOD,
        WARNING,
        DANGER
    }

    private record Report(String judul, String periode, List<ReportSection> sections) {
    }

    private record ReportSection(String title, List<ReportRow> rows) {
    }

    private record ReportRow(String label, BigDecimal amount, String note, boolean emphasized, Tone tone) {
    }

    private record ExcelStyles(
            CellStyle title,
            CellStyle subtitle,
            CellStyle scope,
            CellStyle columnHeader,
            CellStyle section,
            CellStyle label,
            CellStyle labelTotal,
            CellStyle money,
            CellStyle moneyTotal,
            CellStyle note,
            CellStyle statusGood,
            CellStyle statusWarning,
            CellStyle statusDanger) {
    }

    private static final class PageNumberEvent extends PdfPageEventHelper {
        private final String periode;
        private final Font footerFont = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);

        private PageNumberEvent(String periode) {
            this.periode = periode;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            String footer = "SIPATIK - Laporan Kas " + periode + " - Halaman " + writer.getPageNumber();
            ColumnText.showTextAligned(
                    writer.getDirectContent(),
                    Element.ALIGN_CENTER,
                    new Phrase(footer, footerFont),
                    document.getPageSize().getWidth() / 2,
                    22,
                    0);
        }
    }
}
