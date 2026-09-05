package com.projek.sipatik.services;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.projek.sipatik.models.JenisPengeluaran;
import com.projek.sipatik.models.KategoriBeban;
import com.projek.sipatik.models.LaporanKas;
import com.projek.sipatik.repositories.LaporanKasRepository;
import com.projek.sipatik.repositories.PengeluaranRepository;
import com.projek.sipatik.repositories.SetorInfakRepository;
import com.projek.sipatik.repositories.AlumniInvitationRepository;
import com.projek.sipatik.repositories.UserRepository;
import com.projek.sipatik.models.Users;
import com.projek.sipatik.models.Role;

@Service
public class AdminService {

    private static final String[] NAMA_BULAN = {
            "", "JANUARI", "FEBRUARI", "MARET", "APRIL", "MEI", "JUNI",
            "JULI", "AGUSTUS", "SEPTEMBER", "OKTOBER", "NOVEMBER", "DESEMBER"
    };

    /** Bank yang punya kolom saldo sendiri di laporan kas. */
    private static final List<String> BANK_UTAMA = List.of("BCA", "MANDIRI", "BNI");

    private final SetorInfakRepository setorInfakRepo;
    private final PengeluaranRepository pengeluaranRepo;
    private final LaporanKasRepository laporanKasRepo;
    private final AlumniInvitationRepository alumniInvitationRepo;
    private final UserRepository userRepo;

    public AdminService(SetorInfakRepository setorInfakRepo,
            PengeluaranRepository pengeluaranRepo,
            LaporanKasRepository laporanKasRepo,
            AlumniInvitationRepository alumniInvitationRepo,
            UserRepository userRepo) {
        this.setorInfakRepo = setorInfakRepo;
        this.pengeluaranRepo = pengeluaranRepo;
        this.laporanKasRepo = laporanKasRepo;
        this.alumniInvitationRepo = alumniInvitationRepo;
        this.userRepo = userRepo;
    }

    /**
     * Menghapus alumni dan undangannya dalam satu transaksi. Riwayat infak tetap
     * menjadi penghalang karena menghapusnya akan mengubah laporan keuangan lama.
     */
    @Transactional
    public AlumniDeletionResult deleteAlumni(Long id) {
        Users alumni = userRepo.findByIdForUpdate(id).orElse(null);
        if (alumni == null || alumni.getRole() != Role.USER) {
            return new AlumniDeletionResult(AlumniDeletionStatus.NOT_FOUND, null, 0);
        }

        long jumlahInfak = setorInfakRepo.countByUser(alumni);
        if (jumlahInfak > 0) {
            return new AlumniDeletionResult(AlumniDeletionStatus.HAS_INFAK, alumni.getNama(), jumlahInfak);
        }

        alumniInvitationRepo.deleteByUserId(id);
        alumniInvitationRepo.flush();
        userRepo.delete(alumni);
        userRepo.flush();
        return new AlumniDeletionResult(AlumniDeletionStatus.DELETED, alumni.getNama(), 0);
    }

    public enum AlumniDeletionStatus {
        DELETED,
        NOT_FOUND,
        HAS_INFAK
    }

    public record AlumniDeletionResult(AlumniDeletionStatus status, String nama, long jumlahInfak) {
    }

    public List<Integer> getTahunList() {
        int tahunSekarang = Year.now().getValue();
        int tahunMulai = tahunSekarang - 5;
        return IntStream.rangeClosed(tahunMulai, tahunSekarang)
                .boxed()
                .sorted((a, b) -> Integer.compare(b, a))
                .collect(Collectors.toList());
    }

    // =====================================================================
    // Dashboard
    // =====================================================================

    /**
     * Ringkasan dashboard admin.
     *
     * Angka di sini diturunkan dari builder laporan kas yang sama supaya
     * "kenaikan kas" di dashboard dan di halaman Laporan Kas tidak lagi memakai
     * dua rumus berbeda (dulu dashboard mengabaikan infak & pendapatan lain-lain).
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> getDashboardData() {
        LocalDate now = LocalDate.now();
        Map<String, Object> laporan = buildDataLaporanKas(now.getYear(), now.getMonthValue());

        @SuppressWarnings("unchecked")
        Map<String, Object> pemasukan = (Map<String, Object>) laporan.get("pemasukan");
        @SuppressWarnings("unchecked")
        Map<String, Object> pengeluaran = (Map<String, Object>) laporan.get("pengeluaran");
        @SuppressWarnings("unchecked")
        Map<String, Object> saldoAwal = (Map<String, Object>) laporan.get("saldoAwal");
        @SuppressWarnings("unchecked")
        Map<String, Object> saldoAkhir = (Map<String, Object>) laporan.get("saldoAkhir");

        BigDecimal totalInfak = (BigDecimal) pemasukan.get("totalInfak");
        BigDecimal totalPemasukan = (BigDecimal) pemasukan.get("total");
        BigDecimal totalPengeluaran = (BigDecimal) pengeluaran.get("total");
        BigDecimal kenaikanKas = (BigDecimal) laporan.get("kenaikanKas");

        Map<String, Object> data = new HashMap<>();
        data.put("totalInfak", totalInfak);
        data.put("totalPemasukan", totalPemasukan);
        data.put("totalPengeluaran", totalPengeluaran);
        data.put("NaikTurun", kenaikanKas);
        data.put("statusNaikTurun", kenaikanKas.compareTo(BigDecimal.ZERO) > 0
                ? "SURPLUS"
                : kenaikanKas.compareTo(BigDecimal.ZERO) < 0 ? "DEFISIT" : "NETRAL");
        data.put("KasAwal", saldoAwal.get("total"));
        data.put("KasAkhir", saldoAkhir.get("total"));
        data.put("namaBulan", laporan.get("namaBulan"));
        data.put("tahun", laporan.get("tahun"));
        data.put("adaInputKas", laporan.get("adaInputKas"));
        data.put("kasAkhirSeharusnya", laporan.get("kasAkhirSeharusnya"));
        data.put("kasSeimbang", laporan.get("kasSeimbang"));
        data.put("selisihKas", laporan.get("selisihKas"));
        return data;
    }

    // =====================================================================
    // Laporan kas
    // =====================================================================

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> buildDataLaporanKas(Integer tahun, Integer bulan) {
        LocalDate start = LocalDate.of(tahun, bulan, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        LaporanKas kas = laporanKasRepo.findByTahunAndBulan(tahun, bulan).orElse(null);

        // ---- Pemasukan infak, dikelompokkan per bank langsung dari database ----
        Map<String, BigDecimal> infakPerBank = sumInfakPerBank(start, end);
        BigDecimal totalInfak = infakPerBank.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // Nilai dari instalasi lama bisa memakai nama bank di luar rekening yang punya
        // kolom saldo (atau bahkan null). Uangnya tetap masuk total pemasukan, tetapi
        // rekonsiliasi per kanal tidak boleh mengklaim seimbang karena saldo akhirnya
        // tidak memiliki tempat yang setara untuk dibandingkan.
        BigDecimal infakBankTidakTerpetakan = infakPerBank.entrySet().stream()
                .filter(entry -> !BANK_UTAMA.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal lainLain = nilai(kas == null ? null : kas.getInfakLainLain());
        BigDecimal pendapatanLain = nilai(kas == null ? null : kas.getPendapatanLainLain());
        // ZIS dan bunga bank sekarang benar-benar diambil dari input bendahara,
        // bukan lagi konstanta nol yang menyamar sebagai data di laporan.
        BigDecimal zis = nilai(kas == null ? null : kas.getZis());
        BigDecimal bungaBank = nilai(kas == null ? null : kas.getBungaBank());

        BigDecimal totalPemasukan = totalInfak
                .add(lainLain).add(pendapatanLain).add(zis).add(bungaBank);

        // ---- Pengeluaran per kategori ----
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

        BigDecimal totalPengeluaran = pengeluaranMap.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Pemecahan per kanal. Kolom "jenis" sudah lama dicatat pada tiap pengeluaran
        // tapi tidak pernah dipakai laporan, sehingga saldo tunai dan saldo bank
        // tidak bisa diuji sendiri-sendiri.
        BigDecimal pengeluaranTunai = nilai(
                pengeluaranRepo.totalByJenisAndRange(JenisPengeluaran.KAS_TUNAI, start, end));
        BigDecimal pengeluaranBank = nilai(
                pengeluaranRepo.totalByJenisAndRange(JenisPengeluaran.BANK, start, end));
        BigDecimal pengeluaranTanpaKanal = totalPengeluaran
                .subtract(pengeluaranTunai).subtract(pengeluaranBank);

        BigDecimal kenaikanKas = totalPemasukan.subtract(totalPengeluaran);

        // ---- Saldo kas (input manual bendahara) ----
        BigDecimal awalBca = nilai(kas == null ? null : kas.getKasAwalBca());
        BigDecimal awalMandiri = nilai(kas == null ? null : kas.getKasAwalMandiri());
        BigDecimal awalTunai = nilai(kas == null ? null : kas.getKasAwalTunai());
        BigDecimal awalBni = nilai(kas == null ? null : kas.getKasAwalBni());
        BigDecimal totalAwal = awalBca.add(awalMandiri).add(awalTunai).add(awalBni);

        BigDecimal akhirBca = nilai(kas == null ? null : kas.getKasAkhirBca());
        BigDecimal akhirMandiri = nilai(kas == null ? null : kas.getKasAkhirMandiri());
        BigDecimal akhirTunai = nilai(kas == null ? null : kas.getKasAkhirTunai());
        BigDecimal akhirBni = nilai(kas == null ? null : kas.getKasAkhirBni());
        BigDecimal totalAkhir = akhirBca.add(akhirMandiri).add(akhirTunai).add(akhirBni);

        // ---- Rekonsiliasi: mutasi harus menjelaskan perubahan saldo ----
        BigDecimal kasAkhirSeharusnya = totalAwal.add(kenaikanKas);
        BigDecimal selisihKas = totalAkhir.subtract(kasAkhirSeharusnya);
        boolean adaInputKas = kas != null;
        boolean kasSeimbang = !adaInputKas || selisihKas.compareTo(BigDecimal.ZERO) == 0;

        // ---- Rekonsiliasi per kanal ----
        // Semua infak alumni masuk lewat rekening, jadi kanal tunai hanya menerima
        // bagian pemasukan manual yang dinyatakan diterima tunai oleh bendahara.
        BigDecimal pemasukanTunai = nilai(kas == null ? null : kas.getPemasukanLainTunai());
        BigDecimal pemasukanBank = totalPemasukan.subtract(pemasukanTunai);

        BigDecimal awalBank = awalBca.add(awalMandiri).add(awalBni);
        BigDecimal akhirBank = akhirBca.add(akhirMandiri).add(akhirBni);

        BigDecimal bankSeharusnya = awalBank.add(pemasukanBank).subtract(pengeluaranBank);
        BigDecimal tunaiSeharusnya = awalTunai.add(pemasukanTunai).subtract(pengeluaranTunai);
        BigDecimal selisihBank = akhirBank.subtract(bankSeharusnya);
        BigDecimal selisihTunai = akhirTunai.subtract(tunaiSeharusnya);

        // Pemecahan per kanal hanya bermakna bila setiap pengeluaran punya jenis dan
        // setiap setoran mengarah ke rekening yang memiliki kolom saldo pada laporan.
        boolean kanalBisaDicek = adaInputKas
                && pengeluaranTanpaKanal.compareTo(BigDecimal.ZERO) == 0
                && infakBankTidakTerpetakan.compareTo(BigDecimal.ZERO) == 0;
        boolean bankSeimbang = !kanalBisaDicek || selisihBank.compareTo(BigDecimal.ZERO) == 0;
        boolean tunaiSeimbang = !kanalBisaDicek || selisihTunai.compareTo(BigDecimal.ZERO) == 0;

        // ---- Peringatan hitung ganda ----
        // Setoran offline yang sudah diinput lewat "Tambah Infak Manual" ikut terhitung
        // di total infak alumni. Bila nominalnya juga dimasukkan ke "infak lain-lain",
        // uang yang sama tercatat dua kali.
        BigDecimal infakManualPeriodeIni = nilai(setorInfakRepo.totalInfakManualBetween(start, end));
        boolean risikoHitungGanda = lainLain.compareTo(BigDecimal.ZERO) > 0
                && infakManualPeriodeIni.compareTo(BigDecimal.ZERO) > 0;

        // ---- Sambungan saldo antar bulan ----
        LocalDate bulanLalu = start.minusMonths(1);
        LaporanKas kasBulanLalu = laporanKasRepo
                .findByTahunAndBulan(bulanLalu.getYear(), bulanLalu.getMonthValue())
                .orElse(null);
        BigDecimal kasAwalDisarankan = kasBulanLalu == null ? null : totalKasAkhir(kasBulanLalu);
        boolean kasAwalNyambung = kasAwalDisarankan == null || !adaInputKas
                || kasAwalDisarankan.compareTo(totalAwal) == 0;

        Map<String, Object> model = new HashMap<>();
        model.put("namaBulan", NAMA_BULAN[bulan]);
        model.put("tahun", tahun);

        Map<String, Object> pemasukan = new HashMap<>();
        // Kunci per bank utama tetap ada agar tampilan lama tidak berubah bentuk.
        pemasukan.put("bca", infakPerBank.getOrDefault("BCA", BigDecimal.ZERO));
        pemasukan.put("mandiri", infakPerBank.getOrDefault("MANDIRI", BigDecimal.ZERO));
        pemasukan.put("bni", infakPerBank.getOrDefault("BNI", BigDecimal.ZERO));
        pemasukan.put("lainLain", lainLain);
        pemasukan.put("pendapatanLain", pendapatanLain);
        pemasukan.put("zis", zis);
        pemasukan.put("bungaBank", bungaBank);
        pemasukan.put("tunai", nilai(kas == null ? null : kas.getPemasukanLainTunai()));
        pemasukan.put("totalInfak", totalInfak);
        pemasukan.put("total", totalPemasukan);
        // Baris infak dari bank di luar daftar utama, supaya tidak ada nominal yang hilang.
        pemasukan.put("bankLain", bankDiLuarDaftarUtama(infakPerBank));
        model.put("pemasukan", pemasukan);
        model.put("infakPerBank", infakPerBank);

        Map<String, Object> pengeluaran = new HashMap<>(pengeluaranMap);
        pengeluaran.put("total", totalPengeluaran);
        // Sengaja memakai awalan kanal*: kunci "bank" sudah dipakai untuk kategori
        // beban Bank, bukan untuk kanal pembayaran.
        pengeluaran.put("kanalTunai", pengeluaranTunai);
        pengeluaran.put("kanalBank", pengeluaranBank);
        pengeluaran.put("kanalTanpaJenis", pengeluaranTanpaKanal);
        model.put("pengeluaran", pengeluaran);

        model.put("kenaikanKas", kenaikanKas);

        model.put("saldoAwal", Map.of(
                "bca", awalBca,
                "mandiri", awalMandiri,
                "tunai", awalTunai,
                "bni", awalBni,
                "total", totalAwal));
        model.put("saldoAkhir", Map.of(
                "bca", akhirBca,
                "mandiri", akhirMandiri,
                "tunai", akhirTunai,
                "bni", akhirBni,
                "total", totalAkhir));

        // Rekonsiliasi per kanal.
        model.put("kanalBisaDicek", kanalBisaDicek);
        model.put("pemasukanTunai", pemasukanTunai);
        model.put("pemasukanBank", pemasukanBank);
        model.put("infakBankTidakTerpetakan", infakBankTidakTerpetakan);
        model.put("awalBank", awalBank);
        model.put("akhirBank", akhirBank);
        model.put("bankSeharusnya", bankSeharusnya);
        model.put("tunaiSeharusnya", tunaiSeharusnya);
        model.put("selisihBank", selisihBank);
        model.put("selisihTunai", selisihTunai);
        model.put("bankSeimbang", bankSeimbang);
        model.put("tunaiSeimbang", tunaiSeimbang);

        // Peringatan potensi hitung ganda infak lain-lain.
        model.put("risikoHitungGanda", risikoHitungGanda);
        model.put("infakManualPeriodeIni", infakManualPeriodeIni);

        // Blok rekonsiliasi yang ditampilkan di halaman laporan.
        model.put("adaInputKas", adaInputKas);
        model.put("kasAkhirSeharusnya", kasAkhirSeharusnya);
        model.put("selisihKas", selisihKas);
        model.put("kasSeimbang", kasSeimbang);
        model.put("kasAwalDisarankan", kasAwalDisarankan);
        model.put("kasAwalNyambung", kasAwalNyambung);
        model.put("namaBulanLalu", NAMA_BULAN[bulanLalu.getMonthValue()] + " " + bulanLalu.getYear());

        return model;
    }

    // =====================================================================
    // Simpan kas manual
    // =====================================================================

    /**
     * Simpan atau perbarui saldo kas manual satu periode.
     * Total kas awal/akhir selalu dihitung ulang dari komponennya, tidak pernah diinput.
     */
    @Transactional
    public LaporanKas simpanKasManual(Integer tahun, Integer bulan,
            BigDecimal awalBca, BigDecimal awalMandiri, BigDecimal awalTunai, BigDecimal awalBni,
            BigDecimal akhirBca, BigDecimal akhirMandiri, BigDecimal akhirTunai, BigDecimal akhirBni,
            BigDecimal infakLainLain, BigDecimal pendapatanLainLain,
            BigDecimal zis, BigDecimal bungaBank, BigDecimal pemasukanLainTunai,
            boolean harusSudahAda) {

        validasiPeriode(tahun, bulan);

        LaporanKas kas = laporanKasRepo.findByTahunAndBulan(tahun, bulan).orElse(null);
        if (kas == null) {
            if (harusSudahAda) {
                throw new IllegalArgumentException("Data kas " + bulan + "/" + tahun + " tidak ditemukan");
            }
            kas = LaporanKas.builder().tahun(tahun).bulan(bulan).build();
        }

        BigDecimal aBca = wajibTidakNegatif(awalBca, "Kas awal BCA");
        BigDecimal aMandiri = wajibTidakNegatif(awalMandiri, "Kas awal Mandiri");
        BigDecimal aTunai = wajibTidakNegatif(awalTunai, "Kas awal tunai");
        BigDecimal aBni = wajibTidakNegatif(awalBni, "Kas awal BNI");

        kas.setKasAwalBca(aBca);
        kas.setKasAwalMandiri(aMandiri);
        kas.setKasAwalTunai(aTunai);
        kas.setKasAwalBni(aBni);
        kas.setTotalKasAwal(aBca.add(aMandiri).add(aTunai).add(aBni));

        BigDecimal kBca = wajibTidakNegatif(akhirBca, "Kas akhir BCA");
        BigDecimal kMandiri = wajibTidakNegatif(akhirMandiri, "Kas akhir Mandiri");
        BigDecimal kTunai = wajibTidakNegatif(akhirTunai, "Kas akhir tunai");
        BigDecimal kBni = wajibTidakNegatif(akhirBni, "Kas akhir BNI");

        kas.setKasAkhirBca(kBca);
        kas.setKasAkhirMandiri(kMandiri);
        kas.setKasAkhirTunai(kTunai);
        kas.setKasAkhirBni(kBni);
        kas.setTotalKasAkhir(kBca.add(kMandiri).add(kTunai).add(kBni));

        BigDecimal infakLain = wajibTidakNegatif(infakLainLain, "Infak lain-lain");
        BigDecimal pendapatanLain = wajibTidakNegatif(pendapatanLainLain, "Pendapatan lain-lain");
        BigDecimal zisNZ = wajibTidakNegatif(zis, "ZIS");
        BigDecimal bungaNZ = wajibTidakNegatif(bungaBank, "Pendapatan bunga bank");
        BigDecimal tunaiNZ = wajibTidakNegatif(pemasukanLainTunai, "Pemasukan lain-lain tunai");

        // Bagian tunai tidak boleh melebihi pemasukan manual yang tersedia, kalau tidak
        // rekonsiliasi kanal bank akan ikut salah tanpa sebab yang jelas.
        BigDecimal totalPemasukanManual = infakLain.add(pendapatanLain).add(zisNZ);
        if (tunaiNZ.compareTo(totalPemasukanManual) > 0) {
            throw new IllegalArgumentException(
                    "Bagian tunai (" + tunaiNZ + ") tidak boleh melebihi total infak lain-lain, "
                            + "pendapatan lain-lain, dan ZIS (" + totalPemasukanManual + ").");
        }

        kas.setInfakLainLain(infakLain);
        kas.setPendapatanLainLain(pendapatanLain);
        kas.setZis(zisNZ);
        kas.setBungaBank(bungaNZ);
        kas.setPemasukanLainTunai(tunaiNZ);

        kas.setUpdatedAt(LocalDateTime.now());
        return laporanKasRepo.save(kas);
    }

    public LaporanKas getKasData(Integer tahun, Integer bulan) {
        return laporanKasRepo.findByTahunAndBulan(tahun, bulan).orElse(null);
    }

    /** Total kas akhir bulan sebelumnya, untuk prefill kas awal bulan berjalan. */
    public BigDecimal getKasAwalDisarankan(int tahun, int bulan) {
        LocalDate bulanLalu = LocalDate.of(tahun, bulan, 1).minusMonths(1);
        return laporanKasRepo.findByTahunAndBulan(bulanLalu.getYear(), bulanLalu.getMonthValue())
                .map(this::totalKasAkhir)
                .orElse(null);
    }

    /** Rincian kas akhir bulan sebelumnya per kanal, untuk prefill form kas awal. */
    public Map<String, BigDecimal> getRincianKasAkhirBulanLalu(int tahun, int bulan) {
        LocalDate bulanLalu = LocalDate.of(tahun, bulan, 1).minusMonths(1);
        return laporanKasRepo.findByTahunAndBulan(bulanLalu.getYear(), bulanLalu.getMonthValue())
                .map(k -> {
                    Map<String, BigDecimal> rincian = new LinkedHashMap<>();
                    rincian.put("bca", nilai(k.getKasAkhirBca()));
                    rincian.put("mandiri", nilai(k.getKasAkhirMandiri()));
                    rincian.put("tunai", nilai(k.getKasAkhirTunai()));
                    rincian.put("bni", nilai(k.getKasAkhirBni()));
                    return rincian;
                })
                .orElse(null);
    }

    // =====================================================================
    // Helper
    // =====================================================================

    /**
     * Total infak terkonfirmasi per bank untuk satu periode.
     *
     * Sebelumnya hanya BCA dan Mandiri yang dijumlah dengan nama bank ditulis
     * langsung di kode, sehingga setoran lewat bank lain hilang dari total
     * pemasukan tanpa error. Sekarang pengelompokan dilakukan di database
     * berdasarkan nilai bank yang benar-benar tersimpan.
     */
    private Map<String, BigDecimal> sumInfakPerBank(LocalDate start, LocalDate end) {
        Map<String, BigDecimal> hasil = new LinkedHashMap<>();
        for (Object[] baris : setorInfakRepo.sumConfirmedPerBank(start, end)) {
            String bank = baris[0] == null ? "TIDAK DIKETAHUI" : baris[0].toString().toUpperCase(Locale.ROOT);
            BigDecimal jumlah = toBigDecimal(baris[1]);
            hasil.merge(bank, jumlah, BigDecimal::add);
        }
        return hasil;
    }

    private List<Map<String, Object>> bankDiLuarDaftarUtama(Map<String, BigDecimal> infakPerBank) {
        List<Map<String, Object>> lainnya = new ArrayList<>();
        infakPerBank.forEach((bank, jumlah) -> {
            if (!BANK_UTAMA.contains(bank) && jumlah.compareTo(BigDecimal.ZERO) != 0) {
                Map<String, Object> baris = new LinkedHashMap<>();
                baris.put("bank", bank);
                baris.put("jumlah", jumlah);
                lainnya.add(baris);
            }
        });
        return lainnya;
    }

    private BigDecimal total(KategoriBeban kategori, LocalDate start, LocalDate end) {
        return nilai(pengeluaranRepo.totalByKategoriAndRange(kategori, start, end));
    }

    private BigDecimal totalKasAkhir(LaporanKas kas) {
        // Kolom total adalah cache turunan. Selalu hitung dari rincian agar baris legacy
        // dengan total lama/tidak sinkron tidak membuat saran kas awal berbeda dari
        // angka per rekening yang ditampilkan dan diprefill.
        return nilai(kas.getKasAkhirBca())
                .add(nilai(kas.getKasAkhirMandiri()))
                .add(nilai(kas.getKasAkhirTunai()))
                .add(nilai(kas.getKasAkhirBni()));
    }

    private static BigDecimal nilai(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal toBigDecimal(Object v) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        if (v instanceof BigDecimal bd) {
            return bd;
        }
        if (v instanceof java.math.BigInteger bi) {
            return new BigDecimal(bi);
        }
        return BigDecimal.valueOf(((Number) v).longValue());
    }

    private static BigDecimal wajibTidakNegatif(BigDecimal v, String label) {
        BigDecimal hasil = nilai(v);
        if (hasil.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(label + " tidak boleh negatif.");
        }
        return hasil;
    }

    private static void validasiPeriode(Integer tahun, Integer bulan) {
        if (bulan == null || bulan < 1 || bulan > 12) {
            throw new IllegalArgumentException("Bulan harus antara 1 sampai 12.");
        }
        if (tahun == null || tahun < 2000 || tahun > 2100) {
            throw new IllegalArgumentException("Tahun tidak valid.");
        }
    }
}
