package com.projek.sipatik.services;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.projek.sipatik.dto.SetorInfakRequest;
import com.projek.sipatik.exception.FieldValidationException;
import com.projek.sipatik.exception.ResourceNotFoundException;
import com.projek.sipatik.models.SetorInfak;
import com.projek.sipatik.models.StatusInfak;
import com.projek.sipatik.models.Users;
import com.projek.sipatik.repositories.SetorInfakRepository;
import com.projek.sipatik.repositories.UserRepository;

import jakarta.persistence.criteria.Predicate;

/**
 * Satu-satunya pintu masuk untuk mencatat, mengubah, dan memproses setoran infak.
 *
 * Sebelumnya aturan minimal nominal hanya diterapkan di jalur form web, sementara
 * jalur REST menyimpan langsung ke repository dengan pengecekan "nominal lebih dari 0"
 * saja. Semua jalur sekarang wajib lewat kelas ini agar aturannya tunggal.
 */
@Service
public class InfakService {

    private static final Logger log = LoggerFactory.getLogger(InfakService.class);

    /** Angkatan terkecil dan terbesar yang punya tarif minimal infak. */
    public static final long ANGKATAN_MIN = 1L;
    public static final long ANGKATAN_MAX = 24L;
    private static final List<String> BANK_DIDUKUNG = List.of("BCA", "MANDIRI", "BNI");

    private static final Set<String> EKSTENSI_BUKTI_DIIZINKAN = Set.of("jpg", "jpeg", "png", "gif", "pdf");

    private final SetorInfakRepository setorInfakRepository;
    private final UserRepository userRepository;

    public InfakService(SetorInfakRepository setorInfakRepository, UserRepository userRepository) {
        this.setorInfakRepository = setorInfakRepository;
        this.userRepository = userRepository;
    }

    // =====================================================================
    // Validasi nominal
    // =====================================================================

    public void validateInfakNominal(Users user, Long nominal) {
        if (nominal == null || nominal <= 0) {
            throw new FieldValidationException("nominal", "Nominal harus lebih dari 0.");
        }

        long minimalInfak = getMinimalInfak(user);

        if (nominal < minimalInfak) {
            throw new FieldValidationException("nominal",
                    "Minimal infak untuk angkatan " + user.getAngkatan() + " jenjang " + user.getJenjang()
                            + " adalah Rp " + formatRupiah(minimalInfak));
        }
    }

    /**
     * Tarif minimal infak per angkatan (dan jenjang, untuk angkatan yang membedakan).
     * Angkatan di luar rentang ANGKATAN_MIN..ANGKATAN_MAX belum punya tarif, jadi
     * ditolak dengan pesan yang menjelaskan langkah perbaikannya.
     */
    public long getMinimalInfak(Users user) {
        if (user.getAngkatan() == null) {
            throw new FieldValidationException("angkatan",
                    "Data angkatan alumni belum diisi. Lengkapi dulu lewat menu Alumni.");
        }

        long angkatan = user.getAngkatan();
        if (!isAngkatanDidukung(angkatan)) {
            throw new FieldValidationException("angkatan",
                    "Belum ada tarif minimal infak untuk angkatan " + angkatan
                            + ". Tarif tersedia untuk angkatan " + ANGKATAN_MIN + " sampai " + ANGKATAN_MAX + ".");
        }

        boolean s1 = "S1".equalsIgnoreCase(user.getJenjang());

        return switch ((int) angkatan) {
            case 1 -> 200_000L;
            case 2, 3, 4, 5, 6, 7, 8 -> 150_000L;
            case 9, 10, 11, 12 -> 250_000L;
            case 13 -> 400_000L;
            case 14 -> 500_000L;
            case 15 -> 550_000L;
            case 16 -> s1 ? 400_000L : 550_000L;
            case 17, 18 -> s1 ? 600_000L : 750_000L;
            case 19 -> s1 ? 900_000L : 750_000L;
            case 20 -> 800_000L;
            case 21, 22 -> s1 ? 950_000L : 850_000L;
            case 23 -> s1 ? 1_000_000L : 900_000L;
            case 24 -> s1 ? 1_050_000L : 950_000L;
            default -> throw new FieldValidationException("angkatan", "Angkatan tidak dikenali.");
        };
    }

    public static boolean isAngkatanDidukung(Long angkatan) {
        return angkatan != null && angkatan >= ANGKATAN_MIN && angkatan <= ANGKATAN_MAX;
    }

    // =====================================================================
    // Pencatatan
    // =====================================================================

    /** Setoran yang diunggah sendiri oleh alumni. Selalu masuk sebagai MENUNGGU. */
    @Transactional
    public SetorInfak simpanInfakAlumni(Users user, SetorInfakRequest request) throws IOException {
        Users userTerkunci = kunciUser(user);
        validateInfakNominal(userTerkunci, request.getNominal());
        String bank = normalisasiBank(request.getBank());

        LocalDate tanggal = request.getTanggalInfak() != null ? request.getTanggalInfak() : LocalDate.now();
        validateTanggalInfak(tanggal);
        cegahDuplikat(userTerkunci, tanggal, request.getNominal());

        String namaFile = simpanBukti(request.getBuktiTransfer());
        hapusBuktiBilaTransaksiBatal(namaFile);

        SetorInfak infak = new SetorInfak();
        infak.setUser(userTerkunci);
        infak.setBank(bank);
        infak.setNominal(request.getNominal());
        infak.setBuktiTransfer(namaFile);
        infak.setTanggalInfak(tanggal);
        infak.setStatus(StatusInfak.MENUNGGU);
        infak.setDibuatPada(LocalDateTime.now());

        SetorInfak tersimpan = setorInfakRepository.save(infak);
        log.info("Setoran infak baru dari {} sebesar {} tanggal {}", userTerkunci.getEmail(), request.getNominal(), tanggal);
        return tersimpan;
    }

    /**
     * Setoran yang diinput manual oleh admin (alumni transfer offline).
     * Langsung DIKONFIRMASI karena admin sudah memverifikasi mutasi rekening.
     */
    @Transactional
    public SetorInfak tambahInfakManual(Users user, String bank, Long nominal, LocalDate tanggalInfak,
            String emailAdmin) {
        validateInfakNominal(user, nominal);
        String bankTernormalisasi = normalisasiBank(bank);
        validateTanggalInfak(tanggalInfak);
        // Input manual tidak dicegah oleh pemeriksaan duplikat: admin melihat daftarnya
        // dan memang kadang perlu mencatat dua setoran identik di hari yang sama.

        SetorInfak infak = new SetorInfak();
        infak.setUser(user);
        infak.setBank(bankTernormalisasi);
        infak.setNominal(nominal);
        infak.setTanggalInfak(tanggalInfak);
        infak.setBuktiTransfer(SetorInfak.BUKTI_MANUAL);
        infak.setStatus(StatusInfak.DIKONFIRMASI);
        infak.setDibuatPada(LocalDateTime.now());
        infak.setDibuatOleh(emailAdmin);
        infak.setDiprosesOleh(emailAdmin);
        infak.setDiprosesPada(LocalDateTime.now());

        log.info("Infak manual untuk {} sebesar {} tanggal {} oleh admin {}", user.getEmail(), nominal, tanggalInfak,
                emailAdmin);
        return setorInfakRepository.save(infak);
    }

    // =====================================================================
    // Proses admin
    // =====================================================================

    @Transactional
    public SetorInfak konfirmasi(Long id, String emailAdmin) {
        SetorInfak infak = ambilUntukMutasi(id);
        if (infak.getStatus() == StatusInfak.DIKONFIRMASI) {
            throw new FieldValidationException("status", "Setoran ini sudah dikonfirmasi sebelumnya.");
        }

        if (infak.getNominal() == null || infak.getNominal() <= 0) {
            throw new FieldValidationException("nominal", "Nominal setoran tidak valid, koreksi dulu sebelum dikonfirmasi.");
        }

        // Tarif minimal sengaja tidak diuji ulang di sini. Konfirmasi adalah pernyataan
        // bahwa uangnya benar-benar masuk rekening; kalau nominalnya perlu dikoreksi,
        // admin memakai menu Edit. Memblokir di titik ini akan mengunci setoran lama
        // ketika tarif angkatannya berubah.
        infak.setStatus(StatusInfak.DIKONFIRMASI);
        infak.setAlasanTolak(null);
        infak.setDiprosesOleh(emailAdmin);
        infak.setDiprosesPada(LocalDateTime.now());
        log.info("Infak #{} dikonfirmasi oleh {}", id, emailAdmin);
        return setorInfakRepository.save(infak);
    }

    @Transactional
    public SetorInfak tolak(Long id, String alasan, String emailAdmin) {
        if (alasan == null || alasan.trim().length() < 5) {
            throw new FieldValidationException("alasanTolak", "Alasan penolakan wajib diisi minimal 5 karakter.");
        }

        SetorInfak infak = ambilUntukMutasi(id);
        infak.setStatus(StatusInfak.DITOLAK);
        infak.setAlasanTolak(alasan.trim());
        infak.setDiprosesOleh(emailAdmin);
        infak.setDiprosesPada(LocalDateTime.now());
        log.info("Infak #{} ditolak oleh {} dengan alasan: {}", id, emailAdmin, alasan.trim());
        return setorInfakRepository.save(infak);
    }

    /** Kembalikan setoran yang sudah diproses ke antrean MENUNGGU. */
    @Transactional
    public SetorInfak batalkanProses(Long id, String emailAdmin) {
        SetorInfak infak = ambilUntukMutasi(id);
        if (infak.getStatus() == StatusInfak.MENUNGGU) {
            throw new FieldValidationException("status",
                    "Setoran ini masih menunggu, tidak ada proses yang perlu dibatalkan.");
        }
        infak.setStatus(StatusInfak.MENUNGGU);
        infak.setAlasanTolak(null);
        infak.setDiprosesOleh(emailAdmin);
        infak.setDiprosesPada(LocalDateTime.now());
        log.info("Proses infak #{} dibatalkan oleh {}", id, emailAdmin);
        return setorInfakRepository.save(infak);
    }

    /** Koreksi nominal, bank, dan tanggal setoran oleh admin. */
    @Transactional
    public SetorInfak koreksiInfak(Long id, Long nominal, String bank, LocalDate tanggalInfak, String emailAdmin) {
        SetorInfak infak = ambilUntukMutasi(id);

        if (infak.getStatus() == StatusInfak.DIKONFIRMASI) {
            throw new FieldValidationException("status",
                    "Batalkan konfirmasi setoran sebelum mengubah data keuangan ini.");
        }

        validateInfakNominal(infak.getUser(), nominal);
        String bankTernormalisasi = normalisasiBank(bank);
        validateTanggalInfak(tanggalInfak);

        infak.setNominal(nominal);
        infak.setBank(bankTernormalisasi);
        infak.setTanggalInfak(tanggalInfak);
        infak.setDiprosesOleh(emailAdmin);
        infak.setDiprosesPada(LocalDateTime.now());

        log.info("Infak #{} dikoreksi oleh {} menjadi {} / {} / {}", id, emailAdmin, nominal, bank, tanggalInfak);
        return setorInfakRepository.save(infak);
    }

    @Transactional
    public void hapusInfak(Long id) {
        SetorInfak infak = ambilUntukMutasi(id);
        if (infak.getStatus() == StatusInfak.DIKONFIRMASI) {
            throw new FieldValidationException("status",
                    "Batalkan konfirmasi setoran sebelum menghapus data keuangan ini.");
        }
        String bukti = infak.getBuktiTransfer();
        setorInfakRepository.delete(infak);
        hapusBuktiSetelahCommit(bukti);
        log.info("Infak #{} dihapus", id);
    }

    // =====================================================================
    // Pembacaan
    // =====================================================================

    public SetorInfak ambil(Long id) {
        return setorInfakRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Setoran infak #" + id + " tidak ditemukan"));
    }

    public List<SetorInfak> riwayatTerkonfirmasi(Users user) {
        return setorInfakRepository.findByUserAndStatusOrderByTanggalInfakDesc(user, StatusInfak.DIKONFIRMASI);
    }

    public List<SetorInfak> riwayatLengkap(Users user) {
        return setorInfakRepository.findByUserOrderByTanggalInfakDesc(user);
    }

    // =====================================================================
    // Helper internal
    // =====================================================================

    private String normalisasiBank(String bank) {
        if (bank == null || bank.isBlank()) {
            throw new FieldValidationException("bank", "Bank tujuan wajib dipilih.");
        }
        String hasil = bank.trim().toUpperCase(Locale.ROOT);
        if (!BANK_DIDUKUNG.contains(hasil)) {
            throw new FieldValidationException("bank", "Bank tujuan harus BCA, MANDIRI, atau BNI.");
        }
        return hasil;
    }

    private void validateTanggalInfak(LocalDate tanggal) {
        if (tanggal == null) {
            throw new FieldValidationException("tanggalInfak", "Tanggal infak wajib diisi.");
        }
        if (tanggal.isAfter(LocalDate.now())) {
            throw new FieldValidationException("tanggalInfak", "Tanggal infak tidak boleh melewati hari ini.");
        }
    }

    /**
     * Menahan submit ganda dari alumni (tombol ditekan dua kali, atau form dikirim ulang).
     * Hanya dipakai di jalur self-service; admin punya konteks daftar dan boleh mencatat
     * dua setoran identik bila memang begitu kenyataannya.
     */
    private void cegahDuplikat(Users user, LocalDate tanggal, Long nominal) {
        if (setorInfakRepository.countKemungkinanDuplikat(user, tanggal, nominal) > 0) {
            throw new FieldValidationException("nominal",
                    "Kamu sudah punya setoran dengan nominal dan tanggal yang sama. "
                            + "Cek halaman Rekap Infak dulu sebelum menyetor lagi.");
        }
    }

    private Users kunciUser(Users user) {
        if (user == null || user.getId() == null) {
            throw new ResourceNotFoundException("Data alumni untuk setoran tidak ditemukan");
        }
        return userRepository.findByIdForUpdate(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Data alumni untuk setoran tidak ditemukan"));
    }

    private SetorInfak ambilUntukMutasi(Long id) {
        return setorInfakRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Setoran infak #" + id + " tidak ditemukan"));
    }

    /** Kriteria pencarian daftar infak untuk halaman admin. Semua argumen boleh null. */
    public static Specification<SetorInfak> filter(LocalDate start, LocalDate end, StatusInfak status, String nama) {
        return (root, query, cb) -> {
            List<Predicate> syarat = new ArrayList<>();
            if (start != null) {
                syarat.add(cb.greaterThanOrEqualTo(root.get("tanggalInfak"), start));
            }
            if (end != null) {
                syarat.add(cb.lessThanOrEqualTo(root.get("tanggalInfak"), end));
            }
            if (status != null) {
                syarat.add(cb.equal(root.get("status"), status));
            }
            if (nama != null && !nama.isBlank()) {
                syarat.add(cb.like(cb.lower(root.get("user").get("nama")),
                        "%" + nama.toLowerCase(Locale.ROOT) + "%"));
            }
            return cb.and(syarat.toArray(new Predicate[0]));
        };
    }

    private String simpanBukti(MultipartFile bukti) throws IOException {
        if (bukti == null || bukti.isEmpty()) {
            throw new FieldValidationException("buktiTransfer", "File bukti transfer wajib diunggah.");
        }

        String originalFilename = bukti.getOriginalFilename();
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            originalFilename = "file-bukti";
        }
        originalFilename = StringUtils.cleanPath(originalFilename);
        if (originalFilename.contains("..") || originalFilename.contains("/") || originalFilename.contains("\\")) {
            throw new FieldValidationException("buktiTransfer", "Nama file tidak valid.");
        }

        String ekstensi = StringUtils.getFilenameExtension(originalFilename);
        if (ekstensi == null || !EKSTENSI_BUKTI_DIIZINKAN.contains(ekstensi.toLowerCase(Locale.ROOT))) {
            throw new FieldValidationException("buktiTransfer",
                    "Format file harus JPG, JPEG, PNG, GIF, atau PDF.");
        }

        Path uploadDir = uploadDir();
        Files.createDirectories(uploadDir);

        String namaFile = UUID.randomUUID() + "_" + originalFilename;
        Path target = uploadDir.resolve(namaFile).normalize();
        if (!target.startsWith(uploadDir)) {
            throw new FieldValidationException("buktiTransfer", "Nama file tidak valid.");
        }

        try (InputStream in = bukti.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return namaFile;
    }

    private void hapusBukti(String namaFile) {
        if (namaFile == null || namaFile.isBlank() || SetorInfak.BUKTI_MANUAL.equals(namaFile)) {
            return;
        }
        try {
            Path uploadDir = uploadDir();
            Path target = uploadDir.resolve(namaFile).normalize();
            if (target.startsWith(uploadDir)) {
                Files.deleteIfExists(target);
            }
        } catch (IOException e) {
            // File yatim tidak boleh menggagalkan penghapusan data.
            log.warn("Gagal menghapus file bukti {}: {}", namaFile, e.getMessage());
        }
    }

    /** Hapus file baru bila transaksi database kemudian rollback. */
    private void hapusBuktiBilaTransaksiBatal(String namaFile) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    hapusBukti(namaFile);
                }
            }
        });
    }

    /** Jangan hapus bukti sebelum penghapusan baris benar-benar berhasil di-commit. */
    private void hapusBuktiSetelahCommit(String namaFile) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            hapusBukti(namaFile);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                hapusBukti(namaFile);
            }
        });
    }

    private Path uploadDir() {
        return Paths.get("uploads").toAbsolutePath().normalize();
    }

    private static String formatRupiah(long nominal) {
        return String.format("%,d", nominal).replace(',', '.');
    }
}
