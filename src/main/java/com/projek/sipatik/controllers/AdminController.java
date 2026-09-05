package com.projek.sipatik.controllers;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.validation.BindingResult;

import com.projek.sipatik.dto.AdminSetorInfakRequest;
import com.projek.sipatik.dto.AlumniResponse;
import com.projek.sipatik.dto.EditKasRequest;
import com.projek.sipatik.dto.PengeluaranRequest;
import com.projek.sipatik.dto.SetorInfakResponse;
import com.projek.sipatik.models.JenisPengeluaran;
import com.projek.sipatik.models.KategoriBeban;
import com.projek.sipatik.models.Pengeluaran;
import com.projek.sipatik.models.Role;
import com.projek.sipatik.models.Users;
import com.projek.sipatik.models.SetorInfak;
import com.projek.sipatik.models.StatusInfak;
import com.projek.sipatik.repositories.PengeluaranRepository;
import com.projek.sipatik.repositories.SetorInfakRepository;
import com.projek.sipatik.repositories.UserRepository;
import com.projek.sipatik.services.AdminService;
import com.projek.sipatik.services.AlumniInvitationService;
import com.projek.sipatik.services.FinancialEntryService;
import com.projek.sipatik.services.InfakService;
import com.projek.sipatik.services.LaporanKasExportService;
import com.projek.sipatik.exception.FieldValidationException;
import com.projek.sipatik.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

import jakarta.validation.Valid;

import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin")
public class AdminController {
    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    @Autowired
    private AdminService adminService;

    @Autowired
    private PengeluaranRepository pengeluaranRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SetorInfakRepository setorInfakRepository;

    @Autowired
    private InfakService infakService;

    @Autowired
    private FinancialEntryService financialEntryService;

    @Autowired
    private AlumniInvitationService alumniInvitationService;

    @Autowired
    private LaporanKasExportService laporanKasExportService;

    @ModelAttribute("adminSetorInfakRequest")
    public AdminSetorInfakRequest adminSetorInfakRequest() {
        return new AdminSetorInfakRequest();
    }

    @GetMapping("/dash-admin")
    public String dashAdmin(@AuthenticationPrincipal Users user, Model model) {
        log.debug("Dashboard accessed by user: {}", user != null ? user.getEmail() : "null");
        if (user == null) {
            return "redirect:/auth-adm/login";
        }

        model.addAttribute("nama", user.getNama());
        model.addAttribute("angkatan", user.getAngkatan());
        model.addAttribute("email", user.getEmail());
        model.addAttribute("activePage", "dashboard");
        model.addAttribute("pageTitle", "Dashboard Admin | SIPATIK");

        try {
            Map<String, Object> data = adminService.getDashboardData();
            log.debug("Dashboard data loaded successfully");
            model.addAllAttributes(data);
        } catch (Exception e) {
            log.error("Error getting dashboard data for user: {}", user.getEmail(), e);
            throw e; // Re-throw to trigger 500 error
        }

        return "html/admin/dash-admin";
    }

    @GetMapping("/show-pengeluaran")
    public String showPengeluaran(Model model) {
        model.addAttribute("pengeluaranRequest", new PengeluaranRequest());
        model.addAttribute("pengeluaranRequestKey", UUID.randomUUID());

        List<Integer> tahunList = pengeluaranRepository.findAll().stream()
                .map(p -> p.getTanggalPengeluaran())
                .filter(Objects::nonNull)
                .map(d -> d.getYear())
                .distinct()
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());

        if (tahunList.isEmpty()) {
            int now = java.time.Year.now().getValue();
            tahunList = java.util.stream.IntStream.rangeClosed(now - 5, now)
                    .map(i -> now - (i - (now - 5)))
                    .boxed()
                    .sorted(Comparator.reverseOrder())
                    .collect(Collectors.toList());
        }

        model.addAttribute("tahunList", tahunList);
        model.addAttribute("hariIni", LocalDate.now());
        model.addAttribute("activePage", "pengeluaran");
        return "html/admin/add-pengeluaran";
    }

    /**
     * Simpan pengeluaran satu periode.
     *
     * Kategori tiap baris sekarang dikirim eksplisit lewat kategoriList, tidak lagi
     * disimpulkan dari urutan baris form. Tanggal juga diambil dari input admin supaya
     * pengeluaran bulan lalu bisa dicatat, bukan selalu dipaksa ke tanggal hari ini.
     */
    @PostMapping("/add")
    public String tambahPengeluaran(
            @RequestParam(value = "kategoriList", required = false) List<KategoriBeban> kategoriList,
            @RequestParam(value = "jenis", required = false) List<JenisPengeluaran> jenisList,
            @RequestParam(value = "nominalList", required = false) List<Long> nominalList,
            @RequestParam(value = "keteranganList", required = false) List<String> keteranganList,
            @RequestParam(value = "tanggalPengeluaran", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tanggalPengeluaran,
            @RequestParam("requestKey") UUID requestKey,
            @AuthenticationPrincipal Users admin,
            RedirectAttributes redirectAttributes) {

        if (kategoriList == null)
            kategoriList = List.of();
        if (jenisList == null)
            jenisList = List.of();
        if (nominalList == null)
            nominalList = List.of();
        if (keteranganList == null)
            keteranganList = List.of();

        LocalDate tanggal = tanggalPengeluaran != null ? tanggalPengeluaran : LocalDate.now();
        if (tanggal.isAfter(LocalDate.now())) {
            redirectAttributes.addFlashAttribute("error", "Tanggal pengeluaran tidak boleh melewati hari ini.");
            return "redirect:/admin/show-pengeluaran";
        }

        if (kategoriList.size() != jenisList.size() || kategoriList.size() != nominalList.size()) {
            log.warn("Jumlah kolom form pengeluaran tidak sama: kategori={} jenis={} nominal={}",
                    kategoriList.size(), jenisList.size(), nominalList.size());
            redirectAttributes.addFlashAttribute("error",
                    "Data form pengeluaran tidak lengkap. Muat ulang halaman lalu coba lagi.");
            return "redirect:/admin/show-pengeluaran";
        }

        List<Pengeluaran> pengeluaranBaru = new ArrayList<>();
        for (int i = 0; i < kategoriList.size(); i++) {
            Long nominal = nominalList.get(i);
            if (nominal == null || nominal <= 0) {
                continue;
            }
            String keterangan = i < keteranganList.size() ? keteranganList.get(i) : null;
            Pengeluaran pengeluaran = Pengeluaran.builder()
                    .jenis(jenisList.get(i))
                    .kategori(kategoriList.get(i))
                    .nominal(nominal)
                    .keterangan(keterangan != null && !keterangan.isBlank() ? keterangan.trim() : null)
                    .tanggalPengeluaran(tanggal)
                    .build();
            pengeluaranBaru.add(pengeluaran);
        }

        int tersimpan = pengeluaranBaru.size();
        if (tersimpan == 0) {
            redirectAttributes.addFlashAttribute("error",
                    "Tidak ada nominal yang diisi, jadi tidak ada data yang disimpan.");
        } else {
            try {
                FinancialEntryService.Result result = financialEntryService.saveExpenseBatch(
                        admin != null ? admin.getId() : null,
                        requestKey,
                        pengeluaranBaru);
                if (result.isDuplicate()) {
                    redirectAttributes.addFlashAttribute("success",
                            "Permintaan ini sudah diproses; tidak ada pengeluaran ganda yang dibuat.");
                } else {
                    redirectAttributes.addFlashAttribute("success",
                            result.createdCount() + " pengeluaran tersimpan untuk tanggal " + tanggal + ".");
                }
            } catch (RuntimeException e) {
                log.error("Gagal menyimpan batch pengeluaran tanggal {}: {}", tanggal, e.getMessage(), e);
                redirectAttributes.addFlashAttribute("error",
                        "Pengeluaran gagal disimpan. Tidak ada baris dalam batch ini yang dicatat.");
            }
        }
        return "redirect:/admin/show-pengeluaran";
    }

    /**
     * Koreksi satu pengeluaran.
     *
     * Dipetakan ke POST (bukan PUT) karena form HTML tidak bisa mengirim PUT dan filter
     * _method tidak aktif. Tanggal juga tidak lagi direset ke hari ini: mengoreksi nominal
     * pengeluaran bulan lalu dulu memindahkan datanya ke bulan berjalan dan diam-diam
     * mengubah dua laporan sekaligus.
     */
    @PostMapping("/edit-pengeluaran/{id}")
    public String updatePengeluaran(
            @PathVariable Long id,
            @RequestParam("jenis") JenisPengeluaran jenis,
            @RequestParam("kategori") KategoriBeban kategori,
            @RequestParam("nominal") Long nominal,
            @RequestParam(value = "keterangan", required = false) String keterangan,
            @RequestParam(value = "tanggalPengeluaran", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tanggalPengeluaran,
            RedirectAttributes redirectAttributes) {

        Pengeluaran pengeluaran = pengeluaranRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pengeluaran #" + id + " tidak ditemukan"));

        if (nominal == null || nominal <= 0) {
            redirectAttributes.addFlashAttribute("error", "Nominal pengeluaran harus lebih dari 0.");
            return "redirect:/admin/show-pengeluaran";
        }
        if (tanggalPengeluaran != null && tanggalPengeluaran.isAfter(LocalDate.now())) {
            redirectAttributes.addFlashAttribute("error", "Tanggal pengeluaran tidak boleh melewati hari ini.");
            return "redirect:/admin/show-pengeluaran";
        }

        pengeluaran.setJenis(jenis);
        pengeluaran.setKategori(kategori);
        pengeluaran.setNominal(nominal);
        pengeluaran.setKeterangan(keterangan != null && !keterangan.isBlank() ? keterangan.trim() : null);
        if (tanggalPengeluaran != null) {
            pengeluaran.setTanggalPengeluaran(tanggalPengeluaran);
        }

        pengeluaranRepository.save(pengeluaran);
        log.info("Pengeluaran #{} diperbarui menjadi {} / {} / {}", id, kategori, nominal,
                pengeluaran.getTanggalPengeluaran());

        redirectAttributes.addFlashAttribute("success", "Pengeluaran berhasil diperbarui.");
        return "redirect:/admin/show-pengeluaran";
    }

    @PostMapping("/hapus-pengeluaran/{id}")
    public String hapusPengeluaran(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        if (!pengeluaranRepository.existsById(id)) {
            redirectAttributes.addFlashAttribute("error", "Pengeluaran #" + id + " tidak ditemukan.");
            return "redirect:/admin/show-pengeluaran";
        }
        pengeluaranRepository.deleteById(id);
        log.info("Pengeluaran #{} dihapus", id);
        redirectAttributes.addFlashAttribute("success", "Pengeluaran berhasil dihapus.");
        return "redirect:/admin/show-pengeluaran";
    }

    @GetMapping("/alumni")
    public String listAlumni(
            @RequestParam(value = "angkatan", required = false) Long angkatan,
            @RequestParam(value = "sort", defaultValue = "asc") String sort,
            Model model) {

        // Handle flash attributes for validation errors and modal states
        if (model.containsAttribute("alumniErrors")) {
            model.addAttribute("alumniErrors", model.getAttribute("alumniErrors"));
        }
        if (model.containsAttribute("editErrors")) {
            model.addAttribute("editErrors", model.getAttribute("editErrors"));
        }
        if (model.containsAttribute("alumniFormData")) {
            model.addAttribute("alumniFormData", model.getAttribute("alumniFormData"));
        }
        if (model.containsAttribute("editFormData")) {
            model.addAttribute("editFormData", model.getAttribute("editFormData"));
        }
        if (model.containsAttribute("openTambahModal")) {
            model.addAttribute("openTambahModal", model.getAttribute("openTambahModal"));
        }
        if (model.containsAttribute("openEditModalId")) {
            model.addAttribute("openEditModalId", model.getAttribute("openEditModalId"));
        }

        // Ambil semua angkatan yang tersedia HANYA untuk USER (bukan ADMIN)
        List<Long> availableAngkatan = userRepository.findDistinctAngkatanByRole(Role.USER);

        if (availableAngkatan == null || availableAngkatan.isEmpty()) {
            model.addAttribute("alumniList", List.of());
            model.addAttribute("alumni", new Users());
            model.addAttribute("angkatan", null);
            model.addAttribute("availableAngkatan", List.of());
            model.addAttribute("prevAngkatan", null);
            model.addAttribute("nextAngkatan", null);
            model.addAttribute("sort", sort);
            model.addAttribute("activePage", "alumni");
            return "html/admin/alumni-list";
        }

        // Urutkan availableAngkatan
        availableAngkatan.sort(Comparator.naturalOrder());

        List<Users> alumniList;
        Long prevAngkatan = null;
        Long nextAngkatan = null;

        // FIXED: Jika tidak ada angkatan yang dipilih (tombol reset), tampilkan SEMUA
        // alumni
        if (angkatan == null) {
            // Tampilkan semua alumni dengan role USER
            alumniList = userRepository.findByRole(Role.USER);

            // Terapkan sorting nama
            if ("desc".equalsIgnoreCase(sort)) {
                alumniList.sort(Comparator.comparing(Users::getNama,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)).reversed());
            } else {
                alumniList.sort(Comparator.comparing(Users::getNama,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
            }
        } else {
            // Tampilkan alumni per angkatan tertentu
            alumniList = userRepository.findByRoleAndAngkatanOrderByNamaAsc(Role.USER, angkatan);

            // Terapkan sorting nama
            if ("desc".equalsIgnoreCase(sort)) {
                alumniList.sort(Comparator.comparing(Users::getNama,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)).reversed());
            } else {
                alumniList.sort(Comparator.comparing(Users::getNama,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
            }

            // Hitung prev/next angkatan untuk navigasi
            int idx = availableAngkatan.indexOf(angkatan);
            if (idx >= 0) {
                if ("desc".equalsIgnoreCase(sort)) {
                    // Untuk DESC: prev = angkatan lebih besar, next = angkatan lebih kecil
                    prevAngkatan = (idx < availableAngkatan.size() - 1) ? availableAngkatan.get(idx + 1) : null;
                    nextAngkatan = (idx > 0) ? availableAngkatan.get(idx - 1) : null;
                } else {
                    // Untuk ASC: prev = angkatan lebih kecil, next = angkatan lebih besar
                    prevAngkatan = (idx > 0) ? availableAngkatan.get(idx - 1) : null;
                    nextAngkatan = (idx < availableAngkatan.size() - 1) ? availableAngkatan.get(idx + 1) : null;
                }
            }
        }

        model.addAttribute("alumniList", alumniList);
        model.addAttribute("alumni", new Users());
        model.addAttribute("angkatan", angkatan);
        model.addAttribute("availableAngkatan", availableAngkatan);
        model.addAttribute("prevAngkatan", prevAngkatan);
        model.addAttribute("nextAngkatan", nextAngkatan);
        model.addAttribute("sort", sort);
        model.addAttribute("activePage", "alumni");

        return "html/admin/alumni-list";
    }

    /** Riwayat infak terkonfirmasi milik satu alumni, dikirim sebagai DTO tanpa data akun. */
    @GetMapping("/alumni/{alumniId}/infak")
    @ResponseBody
    public List<SetorInfakResponse> getAlumniInfak(@PathVariable Long alumniId) {
        Users alumni = userRepository.findById(alumniId).orElse(null);
        if (alumni == null) {
            return List.of();
        }
        return infakService.riwayatTerkonfirmasi(alumni).stream()
                .map(SetorInfakResponse::from)
                .collect(Collectors.toList());
    }

    @PostMapping("/alumni/add")
    public String saveAlumni(@ModelAttribute("alumni") Users alumni,
            @RequestParam(value = "filterAngkatan", required = false) Long filterAngkatan,
            @RequestParam(value = "sort", defaultValue = "asc") String sort,
            RedirectAttributes redirectAttributes, Model model) {

        // Server-side validation
        Map<String, String> errors = new HashMap<>();

        // Validate nama
        if (alumni.getNama() == null || alumni.getNama().trim().isEmpty()) {
            errors.put("nama", "Nama tidak boleh kosong");
        } else if (alumni.getNama().trim().length() < 2) {
            errors.put("nama", "Nama minimal 2 karakter");
        }

        // Validate jenjang
        if (alumni.getJenjang() == null || alumni.getJenjang().trim().isEmpty()) {
            errors.put("jenjang", "Jenjang harus dipilih");
        } else if (!alumni.getJenjang().equals("D3") && !alumni.getJenjang().equals("S1")) {
            errors.put("jenjang", "Jenjang harus D3 atau S1");
        }

        // Validate angkatan.
        // Batas atas disamakan dengan tarif minimal infak yang tersedia di InfakService.
        // Sebelumnya form menerima angkatan sampai 100, padahal tarifnya hanya sampai 24,
        // sehingga alumni yang terlanjur dibuat di luar rentang itu tidak pernah bisa setor infak.
        if (alumni.getAngkatan() == null) {
            errors.put("angkatan", "Angkatan tidak boleh kosong");
        } else if (!InfakService.isAngkatanDidukung(alumni.getAngkatan())) {
            errors.put("angkatan", "Angkatan harus antara " + InfakService.ANGKATAN_MIN + " - "
                    + InfakService.ANGKATAN_MAX + " (rentang yang punya tarif minimal infak)");
        }

        // If validation errors, redirect back with errors
        if (!errors.isEmpty()) {
            redirectAttributes.addFlashAttribute("alumniErrors", errors);
            redirectAttributes.addFlashAttribute("alumniFormData", alumni);
            redirectAttributes.addFlashAttribute("openTambahModal", true);

            String redirectUrl = "redirect:/admin/alumni?sort=" + sort;
            if (filterAngkatan != null) {
                redirectUrl += "&angkatan=" + filterAngkatan;
            }
            return redirectUrl;
        }

        // Save alumni
        alumni.setRole(Role.USER);
        userRepository.save(alumni);

        // Success message
        redirectAttributes.addFlashAttribute("success", "Alumni berhasil ditambahkan");

        // Prioritaskan angkatan yang baru diinput; jika kosong pakai filter lama
        Long redirectAngkatan = (alumni.getAngkatan() != null) ? alumni.getAngkatan() : filterAngkatan;

        String redirectUrl = "redirect:/admin/alumni?sort=" + sort;
        if (redirectAngkatan != null) {
            redirectUrl += "&angkatan=" + redirectAngkatan;
        }

        return redirectUrl;
    }

    @PostMapping("/alumni/edit/{id}")
    public String updateAlumni(@PathVariable Long id,
            @ModelAttribute("alumni") Users alumni,
            @RequestParam(value = "filterAngkatan", required = false) Long filterAngkatan,
            @RequestParam(value = "sort", defaultValue = "asc") String sort,
            RedirectAttributes redirectAttributes) {

        Users existingAlumni = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Alumni tidak ditemukan: " + id));

        // Server-side validation
        Map<String, String> errors = new HashMap<>();

        // Validate nama
        if (alumni.getNama() == null || alumni.getNama().trim().isEmpty()) {
            errors.put("nama", "Nama tidak boleh kosong");
        } else if (alumni.getNama().trim().length() < 2) {
            errors.put("nama", "Nama minimal 2 karakter");
        }

        // Validate jenjang
        if (alumni.getJenjang() == null || alumni.getJenjang().trim().isEmpty()) {
            errors.put("jenjang", "Jenjang harus dipilih");
        } else if (!alumni.getJenjang().equals("D3") && !alumni.getJenjang().equals("S1")) {
            errors.put("jenjang", "Jenjang harus D3 atau S1");
        }

        // Validate angkatan (sebelumnya tidak divalidasi sama sekali pada jalur edit)
        if (alumni.getAngkatan() == null) {
            errors.put("angkatan", "Angkatan tidak boleh kosong");
        } else if (!InfakService.isAngkatanDidukung(alumni.getAngkatan())) {
            errors.put("angkatan", "Angkatan harus antara " + InfakService.ANGKATAN_MIN + " - "
                    + InfakService.ANGKATAN_MAX + " (rentang yang punya tarif minimal infak)");
        }

        // If validation errors, redirect back with errors
        if (!errors.isEmpty()) {
            redirectAttributes.addFlashAttribute("editErrors", errors);
            redirectAttributes.addFlashAttribute("editFormData", alumni);
            redirectAttributes.addFlashAttribute("openEditModalId", id);

            String redirectUrl = "redirect:/admin/alumni?sort=" + sort;
            if (filterAngkatan != null) {
                redirectUrl += "&angkatan=" + filterAngkatan;
            }
            return redirectUrl;
        }

        // Update data
        existingAlumni.setNama(alumni.getNama());
        existingAlumni.setJenjang(alumni.getJenjang());
        existingAlumni.setAngkatan(alumni.getAngkatan());
        existingAlumni.setRole(Role.USER);

        userRepository.save(existingAlumni);

        // Success message
        redirectAttributes.addFlashAttribute("success", "Alumni berhasil diperbarui");

        // Redirect dengan mempertahankan filter dan sort
        String redirectUrl = "redirect:/admin/alumni?sort=" + sort;
        if (filterAngkatan != null) {
            redirectUrl += "&angkatan=" + filterAngkatan;
        }

        return redirectUrl;
    }

    /** Buat tautan aktivasi sekali pakai untuk alumni yang belum memiliki akun. */
    @PostMapping("/alumni/{id}/undangan")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> buatUndanganAlumni(
            @PathVariable Long id,
            @AuthenticationPrincipal Users admin) {
        try {
            var invitation = alumniInvitationService.issue(id, emailAdmin(admin));
            // Path relatif sengaja dipakai agar bearer URL tidak dapat diarahkan ke
            // host penyerang melalui header Host/X-Forwarded-Host. Browser admin
            // menggabungkannya dengan origin halaman yang sedang terbuka.
            String url = "/auth/aktivasi?token="
                    + URLEncoder.encode(invitation.token(), StandardCharsets.UTF_8);
            return ResponseEntity.ok(Map.of(
                    "url", url,
                    "expiresAt", invitation.expiresAt().toString(),
                    "message", "Undangan baru dibuat. Tautan sebelumnya sudah tidak berlaku."));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Hapus alumni.
     *
     * Alumni yang sudah punya catatan infak tidak boleh dihapus: riwayat keuangannya ikut
     * hilang dan angka laporan bulan-bulan sebelumnya berubah. Versi lama membungkus
     * deleteById dengan catch kosong, sehingga kegagalan foreign key tidak pernah terlihat
     * dan UI tetap menyatakan berhasil.
     */
    @PostMapping("/alumni/hapus/{id}")
    public String deleteAlumni(@PathVariable Long id,
            @RequestParam(value = "angkatan", required = false) Long filterAngkatan,
            @RequestParam(value = "sort", defaultValue = "asc") String sort,
            RedirectAttributes redirectAttributes) {

        try {
            var result = adminService.deleteAlumni(id);
            if (result.status() == AdminService.AlumniDeletionStatus.NOT_FOUND) {
                redirectAttributes.addFlashAttribute("error", "Alumni tidak ditemukan.");
            } else if (result.status() == AdminService.AlumniDeletionStatus.HAS_INFAK) {
                redirectAttributes.addFlashAttribute("error",
                        "Alumni " + result.nama() + " tidak bisa dihapus karena masih punya "
                                + result.jumlahInfak()
                                + " catatan infak. Hapus dulu catatan infaknya bila memang salah input.");
            } else {
                redirectAttributes.addFlashAttribute("success", "Alumni berhasil dihapus.");
            }
        } catch (Exception e) {
            log.error("Gagal menghapus alumni #{}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("error",
                    "Alumni gagal dihapus karena masih terkait data lain.");
        }

        // Redirect dengan mempertahankan filter dan sort
        String redirectUrl = "redirect:/admin/alumni?sort=" + sort;
        if (filterAngkatan != null) {
            redirectUrl += "&angkatan=" + filterAngkatan;
        }

        return redirectUrl;
    }

    /** Data satu alumni sebagai DTO. Entity Users tidak dikirim mentah karena membawa kolom password. */
    @GetMapping("/alumni/{id}/data")
    @ResponseBody
    public ResponseEntity<AlumniResponse> getAlumniData(@PathVariable Long id) {
        return userRepository.findById(id)
                .map(alumni -> ResponseEntity
                        .ok(AlumniResponse.from(alumni, InfakService.isAngkatanDidukung(alumni.getAngkatan()))))
                .orElse(ResponseEntity.notFound().build());
    }

    // Endpoint untuk melihat bukti transfer
    @GetMapping("/infak/bukti/{filename:.+}")
    public ResponseEntity<Resource> lihatBuktiTransfer(@PathVariable String filename) {
        try {
            Path uploadDir = Paths.get("uploads").toAbsolutePath().normalize();
            Path filePath = uploadDir.resolve(filename).normalize();

            if (!filePath.startsWith(uploadDir)) {
                return ResponseEntity.badRequest().build();
            }

            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                // Tentukan content type berdasarkan ekstensi file
                String contentType = determineContentType(filename);

                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentType))
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // Endpoint untuk download bukti transfer
    @GetMapping("/infak/bukti/download/{filename:.+}")
    public ResponseEntity<Resource> downloadBuktiTransfer(@PathVariable String filename) {
        try {
            Path uploadDir = Paths.get("uploads").toAbsolutePath().normalize();
            Path filePath = uploadDir.resolve(filename).normalize();

            if (!filePath.startsWith(uploadDir)) {
                return ResponseEntity.badRequest().build();
            }

            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                // Extract original filename (hapus UUID prefix)
                String originalFilename = extractOriginalFilename(filename);

                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"" + originalFilename + "\"")
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // Endpoint untuk konfirmasi infak
    @PostMapping("/infak/konfirmasi/{id}")
    public String konfirmasiInfak(@PathVariable Long id,
            @AuthenticationPrincipal Users admin,
            RedirectAttributes redirectAttributes) {
        try {
            infakService.konfirmasi(id, emailAdmin(admin));
            redirectAttributes.addFlashAttribute("success", "Setoran infak berhasil dikonfirmasi.");
        } catch (FieldValidationException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (ResourceNotFoundException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/daftar-infak";
    }

    /**
     * Tolak setoran infak.
     *
     * Menolak sekarang menghasilkan status DITOLAK dan menyimpan alasannya, bukan lagi
     * mengembalikan dikonfirmasi ke false. Dulu setoran yang ditolak jadi tidak bisa
     * dibedakan dari setoran yang belum diproses dan menumpuk di antrean selamanya.
     */
    @PostMapping("/infak/tolak/{id}")
    public String tolakInfak(@PathVariable Long id,
            @RequestParam("alasanTolak") String alasanTolak,
            @AuthenticationPrincipal Users admin,
            RedirectAttributes redirectAttributes) {
        try {
            infakService.tolak(id, alasanTolak, emailAdmin(admin));
            redirectAttributes.addFlashAttribute("success", "Setoran infak ditandai ditolak.");
        } catch (FieldValidationException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (ResourceNotFoundException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/daftar-infak";
    }

    /** Kembalikan setoran yang sudah dikonfirmasi atau ditolak ke antrean menunggu. */
    @PostMapping("/infak/batalkan/{id}")
    public String batalkanProsesInfak(@PathVariable Long id,
            @AuthenticationPrincipal Users admin,
            RedirectAttributes redirectAttributes) {
        try {
            infakService.batalkanProses(id, emailAdmin(admin));
            redirectAttributes.addFlashAttribute("success", "Setoran dikembalikan ke status menunggu.");
        } catch (FieldValidationException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (ResourceNotFoundException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/daftar-infak";
    }

    /**
     * Koreksi nominal, bank, dan tanggal satu setoran infak.
     *
     * Endpoint ini sebelumnya tidak pernah ada padahal tombol Edit di halaman daftar infak
     * sudah memanggilnya, sehingga koreksi salah input tidak mungkin dilakukan.
     */
    @PostMapping("/infak/edit/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> editInfak(@PathVariable Long id,
            @RequestParam("nominal") Long nominal,
            @RequestParam(value = "bank", required = false) String bank,
            @RequestParam(value = "tanggalInfak", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tanggalInfak,
            @AuthenticationPrincipal Users admin) {
        try {
            SetorInfak lama = infakService.ambil(id);
            String bankFinal = (bank == null || bank.isBlank()) ? lama.getBank() : bank;
            LocalDate tanggalFinal = tanggalInfak != null ? tanggalInfak : lama.getTanggalInfak();

            SetorInfak hasil = infakService.koreksiInfak(id, nominal, bankFinal, tanggalFinal, emailAdmin(admin));
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Setoran infak berhasil diperbarui",
                    "data", SetorInfakResponse.from(hasil)));
        } catch (FieldValidationException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "field", e.getField() == null ? "" : e.getField(),
                    "error", e.getMessage()));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(404).body(Map.of("success", false, "error", e.getMessage()));
        } catch (RuntimeException e) {
            log.error("Gagal memperbarui infak #{}: {}", id, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "error", "Gagal memperbarui setoran infak"));
        }
    }

    /**
     * Hapus satu setoran infak beserta file buktinya.
     * Sama seperti endpoint edit, sebelumnya hanya ada di JavaScript tanpa handler di server.
     */
    @PostMapping("/infak/hapus/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> hapusInfak(@PathVariable Long id) {
        try {
            infakService.hapusInfak(id);
            return ResponseEntity.ok(Map.of("success", true, "message", "Setoran infak berhasil dihapus"));
        } catch (FieldValidationException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "field", e.getField() == null ? "" : e.getField(),
                    "error", e.getMessage()));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(404).body(Map.of("success", false, "error", e.getMessage()));
        } catch (RuntimeException e) {
            log.error("Gagal menghapus infak #{}: {}", id, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "error", "Gagal menghapus setoran infak"));
        }
    }

    private String emailAdmin(Users admin) {
        return admin != null ? admin.getEmail() : "sistem";
    }

    // Helper method untuk menentukan content type
    private String determineContentType(String filename) {
        String extension = StringUtils.getFilenameExtension(filename);
        if (extension != null) {
            switch (extension.toLowerCase()) {
                case "jpg":
                case "jpeg":
                    return "image/jpeg";
                case "png":
                    return "image/png";
                case "gif":
                    return "image/gif";
                case "pdf":
                    return "application/pdf";
                default:
                    return "application/octet-stream";
            }
        }
        return "application/octet-stream";
    }

    // Helper method untuk extract original filename
    private String extractOriginalFilename(String filename) {
        // Format: UUID_original_filename.ext
        int underscoreIndex = filename.indexOf('_');
        if (underscoreIndex > 0) {
            return filename.substring(underscoreIndex + 1);
        }
        return filename;
    }

    @GetMapping("/daftar-infak")
    public String daftarInfak(
            @RequestParam(value = "bulan", required = false) Integer bulan,
            @RequestParam(value = "tahun", required = false) Integer tahun,
            @RequestParam(value = "status", required = false) String statusStr,
            @RequestParam(value = "search", required = false) String search,
            Model model) {

        LocalDate start = null, end = null;
        if (bulan != null && tahun != null && bulan >= 1 && bulan <= 12) {
            start = LocalDate.of(tahun, bulan, 1);
            end = start.withDayOfMonth(start.lengthOfMonth());
        }

        StatusInfak status = null;
        if ("confirmed".equalsIgnoreCase(statusStr))
            status = StatusInfak.DIKONFIRMASI;
        else if ("pending".equalsIgnoreCase(statusStr))
            status = StatusInfak.MENUNGGU;
        else if ("rejected".equalsIgnoreCase(statusStr))
            status = StatusInfak.DITOLAK;

        // Kotak pencarian di halaman ini sebelumnya mengirim parameter "search"
        // yang tidak pernah dibaca controller, jadi hasilnya tidak pernah tersaring.
        String namaCari = (search == null || search.isBlank()) ? null : search.trim();

        var setoranList = setorInfakRepository.findAll(
                InfakService.filter(start, end, status, namaCari),
                org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Order.desc("tanggalInfak"),
                        org.springframework.data.domain.Sort.Order.desc("id")));

        // Data dropdown tahun dari data transaksi
        List<Integer> tahunList = setorInfakRepository.findAll().stream()
                .map(s -> s.getTanggalInfak())
                .filter(java.util.Objects::nonNull)
                .map(d -> d.getYear())
                .distinct()
                .sorted(java.util.Comparator.reverseOrder())
                .collect(java.util.stream.Collectors.toList());

        model.addAttribute("setoranList", setoranList);
        model.addAttribute("tahunList", tahunList);
        model.addAttribute("selectedBulan", bulan);
        model.addAttribute("selectedTahun", tahun);
        model.addAttribute("selectedStatus", statusStr);
        model.addAttribute("searchTerm", namaCari);
        model.addAttribute("activePage", "infak");
        model.addAttribute("infakRequestKey", UUID.randomUUID());
        return "html/admin/daftar-infak";
    }

    // Endpoint untuk tambah infak manual oleh admin
    @PostMapping("/infak/tambah")
    public String tambahInfak(@Valid @ModelAttribute AdminSetorInfakRequest request,
            BindingResult bindingResult,
            @RequestParam("requestKey") UUID requestKey,
            @AuthenticationPrincipal Users admin,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            var firstError = bindingResult.getFieldErrors().stream().findFirst();
            if (firstError.isPresent()) {
                return redirectInfakWithError(redirectAttributes, firstError.get().getField(),
                        firstError.get().getDefaultMessage());
            }
            return redirectInfakWithError(redirectAttributes, null, "Data tidak valid");
        }

        try {
            FinancialEntryService.Result result = financialEntryService.saveManualInfak(
                    admin != null ? admin.getId() : null,
                    requestKey,
                    request.getUserId(),
                    request.getAngkatan(),
                    request.getBank(),
                    request.getNominal(),
                    request.getTanggalInfak());

            redirectAttributes.addFlashAttribute("success", result.isDuplicate()
                    ? "Permintaan ini sudah diproses; tidak ada infak ganda yang dibuat."
                    : "Infak berhasil ditambahkan");
            return "redirect:/admin/daftar-infak";

        } catch (FieldValidationException e) {
            log.warn("Validation error when adding manual infak: {}", e.getMessage());
            return redirectInfakWithError(redirectAttributes, e.getField(), e.getMessage());
        } catch (RuntimeException e) {
            log.error("Error adding infak: {}", e.getMessage());
            return redirectInfakWithError(redirectAttributes, null, e.getMessage());
        }
    }

    private String redirectInfakWithError(RedirectAttributes redirectAttributes, String field, String message) {
        redirectAttributes.addFlashAttribute("error", message);
        if (field != null && !field.isBlank()) {
            redirectAttributes.addFlashAttribute("errorField", field);
        }
        redirectAttributes.addFlashAttribute("openTambahInfakModal", true);
        StringBuilder url = new StringBuilder("redirect:/admin/daftar-infak?error=")
                .append(URLEncoder.encode(message, StandardCharsets.UTF_8));
        if (field != null && !field.isBlank()) {
            url.append("&errorField=").append(URLEncoder.encode(field, StandardCharsets.UTF_8));
        }
        return url.toString();
    }

    // Endpoint untuk check apakah user exists
    @PostMapping("/check-user")
    @ResponseBody
    public Map<String, Boolean> checkUser(@RequestParam String nama, @RequestParam Long angkatan) {
        boolean exists = userRepository.findByNamaAndAngkatan(nama, angkatan).isPresent();
        return Map.of("exists", exists);
    }

    // API endpoint untuk get daftar angkatan
    @GetMapping("/api/angkatan-list")
    @ResponseBody
    public List<Long> getAngkatanList() {
        return userRepository.findDistinctAngkatanByRole(Role.USER);
    }

    // API endpoint untuk get daftar nama by angkatan
    @GetMapping("/api/nama-by-angkatan")
    @ResponseBody
    public List<Map<String, Object>> getNamaByAngkatan(@RequestParam Long angkatan) {
        return userRepository.findByRoleAndAngkatanOrderByNamaAsc(Role.USER, angkatan).stream()
                .map(user -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", user.getId());
                    item.put("nama", user.getNama());
                    item.put("jenjang", user.getJenjang());
                    item.put("angkatan", user.getAngkatan());
                    return item;
                })
                .collect(Collectors.toList());
    }

    // ...existing code...
    @GetMapping("/laporan-kas")
    public String laporanKas(
            @RequestParam(value = "bulan", required = false) String bulanStr,
            @RequestParam(value = "tahun", required = false) String tahunStr,
            Model model) {

        log.debug("Accessing laporan-kas with bulan={}, tahun={}", bulanStr, tahunStr);
        LocalDate now = LocalDate.now();

        Integer bulan = null;
        Integer tahun = null;

        try {
            if (bulanStr != null && !bulanStr.isBlank()) {
                bulan = Integer.valueOf(bulanStr);
            }
        } catch (NumberFormatException e) {
            log.warn("Invalid bulan parameter: {}", bulanStr);
        }

        try {
            if (tahunStr != null && !tahunStr.isBlank()) {
                tahun = Integer.valueOf(tahunStr);
            }
        } catch (NumberFormatException e) {
            log.warn("Invalid tahun parameter: {}", tahunStr);
        }

        int selectedBulan = (bulan != null && bulan >= 1 && bulan <= 12) ? bulan : now.getMonthValue();
        int selectedTahun = (tahun != null) ? tahun : now.getYear();

        var data = adminService.buildDataLaporanKas(selectedTahun, selectedBulan);
        data.forEach(model::addAttribute);

        // Tambahkan kasData untuk modal form
        var kasData = adminService.getKasData(selectedTahun, selectedBulan);
        model.addAttribute("kasData", kasData);
        // Rincian kas akhir bulan sebelumnya, dipakai untuk mengisi awal form kas awal
        model.addAttribute("kasAwalPrefill", adminService.getRincianKasAkhirBulanLalu(selectedTahun, selectedBulan));

        model.addAttribute("tahunList", adminService.getTahunList());
        model.addAttribute("selectedBulan", selectedBulan);
        model.addAttribute("selectedTahun", selectedTahun);
        model.addAttribute("activePage", "kas");

        return "html/admin/laporan-kas";
    }

    @GetMapping("/laporan-kas/export/excel")
    @ResponseBody
    public ResponseEntity<byte[]> exportLaporanKasExcel(
            @RequestParam int bulan,
            @RequestParam int tahun) {
        if (!periodeValid(tahun, bulan)) {
            return exportBadRequest();
        }

        byte[] content = laporanKasExportService.exportExcel(tahun, bulan);
        return exportResponse(
                content,
                laporanKasExportService.excelFilename(tahun, bulan),
                MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @GetMapping("/laporan-kas/export/pdf")
    @ResponseBody
    public ResponseEntity<byte[]> exportLaporanKasPdf(
            @RequestParam int bulan,
            @RequestParam int tahun) {
        if (!periodeValid(tahun, bulan)) {
            return exportBadRequest();
        }

        byte[] content = laporanKasExportService.exportPdf(tahun, bulan);
        return exportResponse(
                content,
                laporanKasExportService.pdfFilename(tahun, bulan),
                MediaType.APPLICATION_PDF);
    }

    @PostMapping("/laporan-kas")
    public String simpanKasManual(@Valid @ModelAttribute EditKasRequest request,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {
        return simpanKas(request, bindingResult, false, redirectAttributes);
    }

    @PostMapping("/laporan-kas/edit")
    public String updateKasManual(@Valid @ModelAttribute EditKasRequest request,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {
        return simpanKas(request, bindingResult, true, redirectAttributes);
    }

    private String simpanKas(EditKasRequest request, BindingResult bindingResult, boolean harusSudahAda,
            RedirectAttributes redirectAttributes) {

        String redirect = "redirect:/admin/laporan-kas?bulan=" + request.getBulan() + "&tahun=" + request.getTahun();

        if (bindingResult.hasErrors()) {
            String pesan = bindingResult.getFieldErrors().stream()
                    .findFirst()
                    .map(err -> err.getDefaultMessage())
                    .orElse("Data kas tidak valid");
            redirectAttributes.addFlashAttribute("error", pesan);
            return redirect;
        }

        try {
            adminService.simpanKasManual(request.getTahun(), request.getBulan(),
                    request.getAwalBca(), request.getAwalMandiri(), request.getAwalTunai(), request.getAwalBni(),
                    request.getAkhirBca(), request.getAkhirMandiri(), request.getAkhirTunai(), request.getAkhirBni(),
                    request.getInfakLainLain(), request.getPendapatanLainLain(),
                    request.getZis(), request.getBungaBank(), request.getPemasukanLainTunai(),
                    harusSudahAda);

            // Setelah tersimpan, langsung beri tahu bila mutasi bulan ini belum menjelaskan
            // perubahan saldo, supaya selisihnya ketahuan saat itu juga.
            var data = adminService.buildDataLaporanKas(request.getTahun(), request.getBulan());
            boolean seimbang = Boolean.TRUE.equals(data.get("kasSeimbang"));
            if (seimbang) {
                redirectAttributes.addFlashAttribute("success", "Kas berhasil disimpan dan sudah seimbang.");
            } else {
                redirectAttributes.addFlashAttribute("success", "Kas berhasil disimpan.");
                redirectAttributes.addFlashAttribute("warning",
                        "Kas akhir yang diinput belum sama dengan kas awal ditambah mutasi bulan ini. "
                                + "Periksa kembali pemasukan dan pengeluaran periode ini.");
            }
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (RuntimeException e) {
            log.error("Gagal menyimpan kas {}/{}: {}", request.getBulan(), request.getTahun(), e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Gagal menyimpan kas: " + e.getMessage());
        }

        return redirect;
    }

    private static ResponseEntity<byte[]> exportResponse(byte[] content, String filename, MediaType mediaType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        headers.setCacheControl("no-store, no-cache, must-revalidate");
        return ResponseEntity.ok()
                .headers(headers)
                .contentLength(content.length)
                .body(content);
    }

    private static boolean periodeValid(int tahun, int bulan) {
        return bulan >= 1 && bulan <= 12 && tahun >= 2000 && tahun <= 2100;
    }

    private static ResponseEntity<byte[]> exportBadRequest() {
        byte[] message = "Periode laporan tidak valid.".getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.badRequest()
                .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                .contentLength(message.length)
                .body(message);
    }
}
