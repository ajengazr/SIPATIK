package com.projek.sipatik.services;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;

import com.projek.sipatik.models.KategoriBeban;
import com.projek.sipatik.models.LaporanKas;
import com.projek.sipatik.repositories.LaporanKasRepository;
import com.projek.sipatik.repositories.PengeluaranRepository;
import com.projek.sipatik.repositories.SetorInfakRepository;


@Service
public class AdminService {

    private final SetorInfakRepository setorInfakRepo;
    private final PengeluaranRepository pengeluaranRepo;
    private final LaporanKasRepository laporanKasRepo;

     public AdminService(SetorInfakRepository setorInfakRepo,
                        PengeluaranRepository pengeluaranRepo,
                        LaporanKasRepository laporanKasRepo) {
        this.setorInfakRepo = setorInfakRepo;
        this.pengeluaranRepo = pengeluaranRepo;
        this.laporanKasRepo = laporanKasRepo;
    }

    public List<Integer> getTahunList() {
        int tahunSekarang = Year.now().getValue();
        int tahunMulai = tahunSekarang - 5; // 5 tahun terakhir
        return IntStream.rangeClosed(tahunMulai, tahunSekarang)
                .boxed()
                .collect(Collectors.toList());
    }

    public Map<String, Object> getDashboardData() {
        LocalDate now = LocalDate.now();

        // Hitung awal dan akhir bulan
        LocalDate start = now.withDayOfMonth(1);
        LocalDate end = now.withDayOfMonth(now.lengthOfMonth());

        // Ambil data infak dan pengeluaran (bulan berjalan)
        BigDecimal totalInfak = setorInfakRepo.totalInfakBulanan(start, end);
        BigDecimal totalPengeluaran = pengeluaranRepo.totalPengeluaranPerBulan(start, end);

        // Pastikan tidak null
        if (totalInfak == null) totalInfak = BigDecimal.ZERO;
        if (totalPengeluaran == null) totalPengeluaran = BigDecimal.ZERO;

        // Ambil kas awal/akhir dari LaporanKas untuk bulan & tahun berjalan
        LaporanKas kas = laporanKasRepo.findByTahunAndBulan(now.getYear(), now.getMonthValue()).orElse(null);

        BigDecimal kasAwal = BigDecimal.ZERO;
        BigDecimal kasAkhir = BigDecimal.ZERO;

        if (kas != null) {
            BigDecimal awalBca = kas.getKasAwalBca() == null ? BigDecimal.ZERO : kas.getKasAwalBca();
            BigDecimal awalMandiri = kas.getKasAwalMandiri() == null ? BigDecimal.ZERO : kas.getKasAwalMandiri();
            BigDecimal awalTunai = kas.getKasAwalTunai() == null ? BigDecimal.ZERO : kas.getKasAwalTunai();
            BigDecimal awalBni = kas.getKasAwalBni() == null ? BigDecimal.ZERO : kas.getKasAwalBni();
            kasAwal = kas.getTotalKasAwal() != null ? kas.getTotalKasAwal()
                    : awalBca.add(awalMandiri).add(awalTunai).add(awalBni);

            BigDecimal akhirBca = kas.getKasAkhirBca() == null ? BigDecimal.ZERO : kas.getKasAkhirBca();
            BigDecimal akhirMandiri = kas.getKasAkhirMandiri() == null ? BigDecimal.ZERO : kas.getKasAkhirMandiri();
            BigDecimal akhirTunai = kas.getKasAkhirTunai() == null ? BigDecimal.ZERO : kas.getKasAkhirTunai();
            BigDecimal akhirBni = kas.getKasAkhirBni() == null ? BigDecimal.ZERO : kas.getKasAkhirBni();
            kasAkhir = kas.getTotalKasAkhir() != null ? kas.getTotalKasAkhir()
                    : akhirBca.add(akhirMandiri).add(akhirTunai).add(akhirBni);
        }

        BigDecimal selisih = totalInfak.subtract(totalPengeluaran);
        String statusNaikTurun = selisih.compareTo(BigDecimal.ZERO) >= 0 ? "NAIK" : "TURUN";

        Map<String, Object> data = new HashMap<>();
        data.put("totalInfak", totalInfak);
        data.put("totalPengeluaran", totalPengeluaran);
        data.put("NaikTurun", selisih);
        data.put("statusNaikTurun", statusNaikTurun);
        data.put("KasAwal", kasAwal);
        data.put("KasAkhir", kasAkhir);

        return data;
    }

    public Map<String, Object> buildDataLaporanKas(Integer tahun, Integer bulan) {
        LocalDate start = LocalDate.of(tahun, bulan, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        // Pemasukan dari SetorInfak (confirmed)
        BigDecimal bca = sumInfakByBank("BCA", start, end);
        BigDecimal mandiri = sumInfakByBank("MANDIRI", start, end);

        // Ambil data manual dari LaporanKas
        LaporanKas kas = laporanKasRepo.findByTahunAndBulan(tahun, bulan).orElse(null);
        BigDecimal lainLain = kas != null && kas.getInfakLainLain() != null ? kas.getInfakLainLain() : BigDecimal.ZERO;
        BigDecimal pendapatanLain = kas != null && kas.getPendapatanLainLain() != null
                ? kas.getPendapatanLainLain()
                : BigDecimal.ZERO;
        BigDecimal zis = BigDecimal.ZERO; // placeholder
        BigDecimal bungaBank = BigDecimal.ZERO; // placeholder

        BigDecimal totalPemasukan = bca.add(mandiri).add(lainLain).add(pendapatanLain).add(zis).add(bungaBank);

        // Pengeluaran per kategori
        Map<String, BigDecimal> pengeluaranMap = new LinkedHashMap<>();
        pengeluaranMap.put("ketuaKeamanan", total(KategoriBeban.KETUA_KEAMANAN, start, end));
        pengeluaranMap.put("bendahara", total(KategoriBeban.BENDAHARA, start, end));
        pengeluaranMap.put("sekretaris", total(KategoriBeban.SEKRETARIS, start, end));
        pengeluaranMap.put("ppmb", total(KategoriBeban.PPMB_PUB, start, end));
        pengeluaranMap.put("pendidikan", total(KategoriBeban.DIVISI_PENDIDIKAN, start, end));
        pengeluaranMap.put("keasramaan", total(KategoriBeban.DIVISI_KEASRAMAAN, start, end));
        pengeluaranMap.put("kesejahteraan", total(KategoriBeban.DIVISI_KESEJAHTERAAN, start, end));
        pengeluaranMap.put("kesehatan", total(KategoriBeban.DIVISI_KESEHATAN, start, end));
        pengeluaranMap.put("kebersihan", total(KategoriBeban.DIVISI_KEBERSIHAN, start, end));
        pengeluaranMap.put("magang", total(KategoriBeban.DIVISI_MAGANG, start, end));
        pengeluaranMap.put("kerohanian", total(KategoriBeban.DIVISI_KEROHANIAN, start, end));
        pengeluaranMap.put("pendidikanManajemen", total(KategoriBeban.PENDIDIKAN_MANAJEMEN, start, end));
        pengeluaranMap.put("bank", total(KategoriBeban.BANK, start, end));
        pengeluaranMap.put("kantor", total(KategoriBeban.KANTOR_PUB, start, end));
        pengeluaranMap.put("lainLain", total(KategoriBeban.LAIN_LAIN, start, end));

        BigDecimal totalPengeluaran = pengeluaranMap.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        // Ambil/susun kas manual yang tersimpan (BigDecimal)
        BigDecimal awalBca = kas != null && kas.getKasAwalBca() != null ? kas.getKasAwalBca() : BigDecimal.ZERO;
        BigDecimal awalMandiri = kas != null && kas.getKasAwalMandiri() != null ? kas.getKasAwalMandiri() : BigDecimal.ZERO;
        BigDecimal awalTunai = kas != null && kas.getKasAwalTunai() != null ? kas.getKasAwalTunai() : BigDecimal.ZERO;
        BigDecimal awalBni = kas != null && kas.getKasAwalBni() != null ? kas.getKasAwalBni() : BigDecimal.ZERO;
        BigDecimal totalAwal = kas != null && kas.getTotalKasAwal() != null ? kas.getTotalKasAwal()
                : awalBca.add(awalMandiri).add(awalTunai).add(awalBni);

        BigDecimal akhirBca = kas != null && kas.getKasAkhirBca() != null ? kas.getKasAkhirBca() : BigDecimal.ZERO;
        BigDecimal akhirMandiri = kas != null && kas.getKasAkhirMandiri() != null ? kas.getKasAkhirMandiri() : BigDecimal.ZERO;
        BigDecimal akhirTunai = kas != null && kas.getKasAkhirTunai() != null ? kas.getKasAkhirTunai() : BigDecimal.ZERO;
        BigDecimal akhirBni = kas != null && kas.getKasAkhirBni() != null ? kas.getKasAkhirBni() : BigDecimal.ZERO;
        BigDecimal totalAkhir = kas != null && kas.getTotalKasAkhir() != null ? kas.getTotalKasAkhir()
                : akhirBca.add(akhirMandiri).add(akhirTunai).add(akhirBni);

        // Format nama bulan dalam bahasa Indonesia
        String[] namaBulan = {
            "", "JANUARI", "FEBRUARI", "MARET", "APRIL", "MEI", "JUNI",
            "JULI", "AGUSTUS", "SEPTEMBER", "OKTOBER", "NOVEMBER", "DESEMBER"
        };
        
        Map<String, Object> model = new HashMap<>();
        model.put("namaBulan", namaBulan[bulan]);
        model.put("tahun", tahun);

        Map<String, Object> pemasukan = new HashMap<>();
        pemasukan.put("bca", bca);
        pemasukan.put("mandiri", mandiri);
        pemasukan.put("lainLain", lainLain);
        pemasukan.put("pendapatanLain", pendapatanLain);
        pemasukan.put("zis", zis);
        pemasukan.put("bungaBank", bungaBank);
        pemasukan.put("total", totalPemasukan);
        model.put("pemasukan", pemasukan);

        Map<String, Object> pengeluaran = new HashMap<>(pengeluaranMap);
        pengeluaran.put("total", totalPengeluaran);
        model.put("pengeluaran", pengeluaran);

        model.put("kenaikanKas", totalPemasukan.subtract(totalPengeluaran));

        Map<String, Object> saldoAwal = Map.of(
                "bca", awalBca,
                "mandiri", awalMandiri,
                "tunai", awalTunai,
                "bni", awalBni,
                "total", totalAwal);
        Map<String, Object> saldoAkhir = Map.of(
                "bca", akhirBca,
                "mandiri", akhirMandiri,
                "tunai", akhirTunai,
                "bni", akhirBni,
                "total", totalAkhir);
        model.put("saldoAwal", saldoAwal);
        model.put("saldoAkhir", saldoAkhir);

        return model;
    }

    public void saveKasManual(Integer tahun, Integer bulan,
            java.math.BigDecimal awalBca, java.math.BigDecimal awalMandiri, java.math.BigDecimal awalTunai, java.math.BigDecimal awalBni,
            java.math.BigDecimal akhirBca, java.math.BigDecimal akhirMandiri, java.math.BigDecimal akhirTunai, java.math.BigDecimal akhirBni,
            java.math.BigDecimal infakLainLain, java.math.BigDecimal pendapatanLainLain) {
        LaporanKas kas = laporanKasRepo.findByTahunAndBulan(tahun, bulan)
                .orElse(LaporanKas.builder().tahun(tahun).bulan(bulan).build());

        java.math.BigDecimal awalBcaNZ = awalBca == null ? java.math.BigDecimal.ZERO : awalBca;
        java.math.BigDecimal awalMandiriNZ = awalMandiri == null ? java.math.BigDecimal.ZERO : awalMandiri;
        java.math.BigDecimal awalTunaiNZ = awalTunai == null ? java.math.BigDecimal.ZERO : awalTunai;
        java.math.BigDecimal awalBniNZ = awalBni == null ? java.math.BigDecimal.ZERO : awalBni;

        kas.setKasAwalBca(awalBcaNZ);
        kas.setKasAwalMandiri(awalMandiriNZ);
        kas.setKasAwalTunai(awalTunaiNZ);
        kas.setKasAwalBni(awalBniNZ);
        kas.setTotalKasAwal(awalBcaNZ.add(awalMandiriNZ).add(awalTunaiNZ).add(awalBniNZ));

        java.math.BigDecimal akhirBcaNZ = akhirBca == null ? java.math.BigDecimal.ZERO : akhirBca;
        java.math.BigDecimal akhirMandiriNZ = akhirMandiri == null ? java.math.BigDecimal.ZERO : akhirMandiri;
        java.math.BigDecimal akhirTunaiNZ = akhirTunai == null ? java.math.BigDecimal.ZERO : akhirTunai;
        java.math.BigDecimal akhirBniNZ = akhirBni == null ? java.math.BigDecimal.ZERO : akhirBni;

        kas.setKasAkhirBca(akhirBcaNZ);
        kas.setKasAkhirMandiri(akhirMandiriNZ);
        kas.setKasAkhirTunai(akhirTunaiNZ);
        kas.setKasAkhirBni(akhirBniNZ);
        kas.setTotalKasAkhir(akhirBcaNZ.add(akhirMandiriNZ).add(akhirTunaiNZ).add(akhirBniNZ));

        kas.setInfakLainLain(infakLainLain == null ? java.math.BigDecimal.ZERO : infakLainLain);
        kas.setPendapatanLainLain(pendapatanLainLain == null ? java.math.BigDecimal.ZERO : pendapatanLainLain);

        kas.setUpdatedAt(LocalDateTime.now());
        laporanKasRepo.save(kas);
    }

    public void updateKasManual(int tahun, int bulan,
        java.math.BigDecimal awalBca, java.math.BigDecimal awalMandiri, java.math.BigDecimal awalTunai, java.math.BigDecimal awalBni,
        java.math.BigDecimal akhirBca, java.math.BigDecimal akhirMandiri, java.math.BigDecimal akhirTunai, java.math.BigDecimal akhirBni,
        java.math.BigDecimal infakLainLain, java.math.BigDecimal pendapatanLainLain) {

    LaporanKas kas = laporanKasRepo.findByTahunAndBulan(tahun, bulan)
            .orElseThrow(() -> new IllegalArgumentException("Data kas " + bulan + "/" + tahun + " tidak ditemukan"));

    java.math.BigDecimal awalBcaNZ = awalBca == null ? java.math.BigDecimal.ZERO : awalBca;
    java.math.BigDecimal awalMandiriNZ = awalMandiri == null ? java.math.BigDecimal.ZERO : awalMandiri;
    java.math.BigDecimal awalTunaiNZ = awalTunai == null ? java.math.BigDecimal.ZERO : awalTunai;
    java.math.BigDecimal awalBniNZ = awalBni == null ? java.math.BigDecimal.ZERO : awalBni;

    kas.setKasAwalBca(awalBcaNZ);
    kas.setKasAwalMandiri(awalMandiriNZ);
    kas.setKasAwalTunai(awalTunaiNZ);
    kas.setKasAwalBni(awalBniNZ);
    kas.setTotalKasAwal(awalBcaNZ.add(awalMandiriNZ).add(awalTunaiNZ).add(awalBniNZ));

    java.math.BigDecimal akhirBcaNZ = akhirBca == null ? java.math.BigDecimal.ZERO : akhirBca;
    java.math.BigDecimal akhirMandiriNZ = akhirMandiri == null ? java.math.BigDecimal.ZERO : akhirMandiri;
    java.math.BigDecimal akhirTunaiNZ = akhirTunai == null ? java.math.BigDecimal.ZERO : akhirTunai;
    java.math.BigDecimal akhirBniNZ = akhirBni == null ? java.math.BigDecimal.ZERO : akhirBni;

    kas.setKasAkhirBca(akhirBcaNZ);
    kas.setKasAkhirMandiri(akhirMandiriNZ);
    kas.setKasAkhirTunai(akhirTunaiNZ);
    kas.setKasAkhirBni(akhirBniNZ);
    kas.setTotalKasAkhir(akhirBcaNZ.add(akhirMandiriNZ).add(akhirTunaiNZ).add(akhirBniNZ));

    kas.setInfakLainLain(infakLainLain == null ? java.math.BigDecimal.ZERO : infakLainLain);
    kas.setPendapatanLainLain(pendapatanLainLain == null ? java.math.BigDecimal.ZERO : pendapatanLainLain);

    kas.setUpdatedAt(java.time.LocalDateTime.now());
    laporanKasRepo.save(kas);
}

    private BigDecimal sumInfakByBank(String bank, LocalDate start, LocalDate end) {
        var items = setorInfakRepo.findConfirmedBetween(start, end).stream()
                .filter(s -> s.getBank() != null && s.getBank().equalsIgnoreCase(bank))
                .collect(Collectors.toList());
        return items.stream()
                .map(s -> BigDecimal.valueOf(s.getNominal() == null ? 0L : s.getNominal()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal total(KategoriBeban kategori, LocalDate start, LocalDate end) {
        BigDecimal v = pengeluaranRepo.totalByKategoriAndRange(kategori, start, end);
        return v == null ? BigDecimal.ZERO : v;
    }

    public LaporanKas getKasData(Integer tahun, Integer bulan) {
        return laporanKasRepo.findByTahunAndBulan(tahun, bulan).orElse(null);
    }

    private String formatId(long v) {
        return java.text.NumberFormat
                .getInstance(java.util.Locale.of("id", "ID"))
                .format(v);
    }
}
