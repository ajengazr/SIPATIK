package com.projek.sipatik.controllers;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.validation.BindingResult;

import com.projek.sipatik.dto.AdminSetorInfakRequest;
import com.projek.sipatik.dto.PengeluaranRequest;
import com.projek.sipatik.models.JenisPengeluaran;
import com.projek.sipatik.models.KategoriBeban;
import com.projek.sipatik.models.Pengeluaran;
import com.projek.sipatik.models.Role;
import com.projek.sipatik.models.Users;
import com.projek.sipatik.models.SetorInfak;
import com.projek.sipatik.repositories.PengeluaranRepository;
import com.projek.sipatik.repositories.SetorInfakRepository;
import com.projek.sipatik.repositories.UserRepository;
import com.projek.sipatik.services.AdminService;
import com.projek.sipatik.services.UserService;
import com.projek.sipatik.exception.FieldValidationException;

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
    private UserService userService;

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
        return "html/admin/add-pengeluaran";
    }

    @PostMapping("/add")
    public String tambahPengeluaran(
            @RequestParam(value = "jenis", required = false) List<JenisPengeluaran> jenisList,
            @RequestParam(value = "nominalList", required = false) List<Long> nominalList) {

        List<KategoriBeban> semuaBeban = Arrays.asList(KategoriBeban.values());

        if (jenisList == null)
            jenisList = List.of();
        if (nominalList == null)
            nominalList = List.of();

        int max = Math.min(semuaBeban.size(), Math.min(jenisList.size(), nominalList.size()));

        for (int i = 0; i < max; i++) {
            Long nominal = nominalList.get(i);
            if (nominal != null && nominal > 0) {
                Pengeluaran pengeluaran = Pengeluaran.builder()
                        .jenis(jenisList.get(i))
                        .kategori(semuaBeban.get(i))
                        .nominal(nominal)
                        .tanggalPengeluaran(LocalDate.now())
                        .build();
                pengeluaranRepository.save(pengeluaran);
            }
        }

        return "redirect:/admin/dash-admin";
    }

    @PutMapping("/edit-pengeluaran/{id}")
    public String updatePengeluaran(
            @PathVariable Long id,
            @RequestParam("jenis") JenisPengeluaran jenis,
            @RequestParam("kategori") KategoriBeban kategori,
            @RequestParam("nominal") Long nominal) {

        Pengeluaran pengeluaran = pengeluaranRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ID tidak ditemukan"));

        pengeluaran.setJenis(jenis);
        pengeluaran.setKategori(kategori);
        pengeluaran.setNominal(nominal);
        pengeluaran.setTanggalPengeluaran(LocalDate.now());

        pengeluaranRepository.save(pengeluaran);

        return "redirect:/admin/dash-admin";
    }

    @DeleteMapping("/hapus-pengeluaran/{id}")
    public String hapusPengeluaran(@PathVariable Long id) {
        pengeluaranRepository.deleteById(id);
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

    @GetMapping("/alumni/{alumniId}/infak")
    @ResponseBody
    public List<SetorInfak> getAlumniInfak(@PathVariable Long alumniId) {
        // Get all confirmed infak for this alumni
        Users alumni = userRepository.findById(alumniId).orElse(null);
        if (alumni == null) {
            return List.of();
        }
        return setorInfakRepository.findByUserAndDikonfirmasiTrue(alumni);
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

        // Validate angkatan
        if (alumni.getAngkatan() == null) {
            errors.put("angkatan", "Angkatan tidak boleh kosong");
        } else if (alumni.getAngkatan() < 1 || alumni.getAngkatan() > 100) {
            errors.put("angkatan", "Angkatan harus antara 1 - 100");
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

    @PostMapping("/alumni/hapus/{id}")
    public String deleteAlumni(@PathVariable Long id,
            @RequestParam(value = "angkatan", required = false) Long filterAngkatan,
            @RequestParam(value = "sort", defaultValue = "asc") String sort) {

        try {
            userRepository.deleteById(id);
        } catch (Exception e) {
            // Log error atau handle exception jika diperlukan
        }

        // Redirect dengan mempertahankan filter dan sort
        String redirectUrl = "redirect:/admin/alumni?sort=" + sort;
        if (filterAngkatan != null) {
            redirectUrl += "&angkatan=" + filterAngkatan;
        }

        return redirectUrl;
    }

    @GetMapping("/alumni/{id}/data")
    @ResponseBody
    public ResponseEntity<Users> getAlumniData(@PathVariable Long id) {
        return userRepository.findById(id)
                .map(alumni -> ResponseEntity.ok().body(alumni))
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
    public String konfirmasiInfak(@PathVariable Long id) {
        SetorInfak setorInfak = setorInfakRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Setor infak tidak ditemukan"));

        setorInfak.setDikonfirmasi(true);
        setorInfakRepository.save(setorInfak);

        return "redirect:/admin/daftar-infak";
    }

    // Endpoint untuk tolak infak
    @PostMapping("/infak/tolak/{id}")
    public String tolakInfak(@PathVariable Long id) {
        SetorInfak setorInfak = setorInfakRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Setor infak tidak ditemukan"));

        setorInfak.setDikonfirmasi(false);
        setorInfakRepository.save(setorInfak);

        return "redirect:/admin/daftar-infak";
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
            Model model) {

        LocalDate start = null, end = null;
        if (bulan != null && tahun != null && bulan >= 1 && bulan <= 12) {
            start = LocalDate.of(tahun, bulan, 1);
            end = start.withDayOfMonth(start.lengthOfMonth());
        }

        Boolean status = null;
        if ("confirmed".equalsIgnoreCase(statusStr))
            status = Boolean.TRUE;
        else if ("pending".equalsIgnoreCase(statusStr))
            status = Boolean.FALSE;

        var setoranList = setorInfakRepository.filterByDateAndStatus(start, end, status);

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
        model.addAttribute("activePage", "infak");
        return "html/admin/daftar-infak";
    }

    // Endpoint untuk tambah infak manual oleh admin
    @PostMapping("/infak/tambah")
    public String tambahInfak(@Valid @ModelAttribute AdminSetorInfakRequest request,
            BindingResult bindingResult,
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
            Users user = userRepository.findById(request.getUserId())
                    .filter(u -> u.getRole() == Role.USER)
                    .orElseThrow(() -> new RuntimeException("Alumni tidak ditemukan atau tidak valid"));

            if (!Objects.equals(user.getAngkatan(), request.getAngkatan())) {
                throw new RuntimeException("Angkatan yang dipilih tidak sesuai dengan data alumni");
            }

            userService.validateInfakNominal(user, request.getNominal());

            SetorInfak setorInfak = new SetorInfak();
            setorInfak.setUser(user);
            setorInfak.setBank(request.getBank());
            setorInfak.setNominal(request.getNominal());
            setorInfak.setTanggalInfak(request.getTanggalInfak());
            setorInfak.setDikonfirmasi(true);
            setorInfak.setBuktiTransfer("admin-manual-entry");

            setorInfakRepository.save(setorInfak);

            redirectAttributes.addFlashAttribute("success", "Infak berhasil ditambahkan");
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

        model.addAttribute("tahunList", adminService.getTahunList());
        model.addAttribute("selectedBulan", selectedBulan);
        model.addAttribute("selectedTahun", selectedTahun);
        model.addAttribute("activePage", "kas");

        return "html/admin/laporan-kas";
    }

    // ...existing code...
    @PostMapping("/laporan-kas")
    public String simpanKasManual(
            @RequestParam("bulan") Integer bulan,
            @RequestParam("tahun") Integer tahun,
            @RequestParam(value = "awalBca", required = false, defaultValue = "0") java.math.BigDecimal awalBca,
            @RequestParam(value = "awalMandiri", required = false, defaultValue = "0") java.math.BigDecimal awalMandiri,
            @RequestParam(value = "awalTunai", required = false, defaultValue = "0") java.math.BigDecimal awalTunai,
            @RequestParam(value = "awalBni", required = false, defaultValue = "0") java.math.BigDecimal awalBni,
            @RequestParam(value = "akhirBca", required = false, defaultValue = "0") java.math.BigDecimal akhirBca,
            @RequestParam(value = "akhirMandiri", required = false, defaultValue = "0") java.math.BigDecimal akhirMandiri,
            @RequestParam(value = "akhirTunai", required = false, defaultValue = "0") java.math.BigDecimal akhirTunai,
            @RequestParam(value = "akhirBni", required = false, defaultValue = "0") java.math.BigDecimal akhirBni,
            @RequestParam(value = "infakLainLain", required = false, defaultValue = "0") java.math.BigDecimal infakLainLain,
            @RequestParam(value = "pendapatanLainLain", required = false, defaultValue = "0") java.math.BigDecimal pendapatanLainLain) {
        adminService.saveKasManual(tahun, bulan, awalBca, awalMandiri, awalTunai, awalBni,
                akhirBca, akhirMandiri, akhirTunai, akhirBni,
                infakLainLain, pendapatanLainLain);
        return "redirect:/admin/laporan-kas?bulan=" + bulan + "&tahun=" + tahun;
    }

    @PostMapping("/laporan-kas/edit")
    public String updateKasManual(
            @RequestParam("bulan") Integer bulan,
            @RequestParam("tahun") Integer tahun,
            @RequestParam(value = "awalBca", required = false, defaultValue = "0") java.math.BigDecimal awalBca,
            @RequestParam(value = "awalMandiri", required = false, defaultValue = "0") java.math.BigDecimal awalMandiri,
            @RequestParam(value = "awalTunai", required = false, defaultValue = "0") java.math.BigDecimal awalTunai,
            @RequestParam(value = "awalBni", required = false, defaultValue = "0") java.math.BigDecimal awalBni,
            @RequestParam(value = "akhirBca", required = false, defaultValue = "0") java.math.BigDecimal akhirBca,
            @RequestParam(value = "akhirMandiri", required = false, defaultValue = "0") java.math.BigDecimal akhirMandiri,
            @RequestParam(value = "akhirTunai", required = false, defaultValue = "0") java.math.BigDecimal akhirTunai,
            @RequestParam(value = "akhirBni", required = false, defaultValue = "0") java.math.BigDecimal akhirBni,
            @RequestParam(value = "infakLainLain", required = false, defaultValue = "0") java.math.BigDecimal infakLainLain,
            @RequestParam(value = "pendapatanLainLain", required = false, defaultValue = "0") java.math.BigDecimal pendapatanLainLain,
            RedirectAttributes redirectAttributes) {
        try {
            adminService.updateKasManual(tahun, bulan, awalBca, awalMandiri, awalTunai, awalBni,
                    akhirBca, akhirMandiri, akhirTunai, akhirBni,
                    infakLainLain, pendapatanLainLain);
            redirectAttributes.addFlashAttribute("success", "Kas berhasil diperbarui.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Gagal memperbarui kas: " + e.getMessage());
        }
        return "redirect:/admin/laporan-kas?bulan=" + bulan + "&tahun=" + tahun;
    }
}