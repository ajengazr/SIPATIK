package com.projek.sipatik.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.projek.sipatik.models.Role;
import com.projek.sipatik.models.Users;
import com.projek.sipatik.repositories.UserRepository;
import com.projek.sipatik.security.JwtUtil;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AlumniAddCsrfFlowTest {

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;

    @Test
    void formTambahAlumniMemuatInputCsrfSendiri() throws Exception {
        Users admin = admin();
        Cookie jwt = new Cookie("jwt", jwtUtil.generateToken(admin));

        MvcResult page = mvc.perform(get("/admin/alumni").cookie(jwt))
                .andExpect(status().isOk())
                .andReturn();

        String html = page.getResponse().getContentAsString();
        int mulai = html.indexOf("id=\"tambahForm\"");
        int akhir = html.indexOf("</form>", mulai);
        assertThat(mulai).as("form tambah harus ada di halaman").isGreaterThanOrEqualTo(0);
        String formHtml = html.substring(mulai, akhir);

        System.out.println("===== FORM TAMBAH ALUMNI (RENDERED) =====");
        System.out.println(formHtml);
        System.out.println("==========================================");

        assertThat(formHtml)
                .as("form tambah alumni harus memuat input _csrf sendiri")
                .contains("name=\"_csrf\"");
    }

    /**
     * Regresi: dulu token CSRF dirotasi oleh CsrfAuthenticationStrategy pada tiap
     * request terautentikasi (mis. favicon/aset lain yang ikut dikirim cookie jwt),
     * sehingga cookie XSRF-TOKEN berubah setelah halaman dirender dan semua submit
     * form ditolak InvalidCsrfTokenException. Token harus tetap stabil.
     */
    @Test
    void tokenCsrfTetapStabilSetelahRequestAdminLain() throws Exception {
        Users admin = admin();
        Cookie jwt = new Cookie("jwt", jwtUtil.generateToken(admin));

        MvcResult page = mvc.perform(get("/admin/alumni").cookie(jwt))
                .andExpect(status().isOk())
                .andReturn();
        Cookie xsrfPage = page.getResponse().getCookie("XSRF-TOKEN");
        String formToken = extractCsrf(page.getResponse().getContentAsString());
        assertThat(xsrfPage).isNotNull();
        assertThat(formToken).isEqualTo(xsrfPage.getValue());

        // Simulasi request terautentikasi lain di antara (seperti favicon/aset yang
        // ikut memakai cookie jwt) lalu lihat apakah cookie berubah.
        MvcResult antara = mvc.perform(get("/admin/dash-admin").cookie(jwt, xsrfPage))
                .andExpect(status().isOk())
                .andReturn();
        Cookie xsrfSetelah = antara.getResponse().getCookie("XSRF-TOKEN");

        // Submit form lama dengan cookie TERKINI: harus tetap diterima.
        Cookie cookieKirim = xsrfSetelah != null ? xsrfSetelah : xsrfPage;
        mvc.perform(post("/admin/alumni/add")
                        .cookie(jwt, cookieKirim)
                        .param("_csrf", formToken)
                        .param("nama", "Alumni Stabil")
                        .param("jenjang", "S1")
                        .param("angkatan", "20"))
                .andExpect(status().is3xxRedirection());

        assertThat(users.findAll().stream()
                .filter(u -> "Alumni Stabil".equals(u.getNama())).count()).isEqualTo(1);
    }

    @Test
    void alurNormalBisaTambahAlumni() throws Exception {
        Users admin = admin();
        Cookie jwt = new Cookie("jwt", jwtUtil.generateToken(admin));

        MvcResult page = mvc.perform(get("/admin/alumni").cookie(jwt))
                .andExpect(status().isOk())
                .andReturn();

        Cookie xsrfCookie = page.getResponse().getCookie("XSRF-TOKEN");
        String hiddenCsrf = extractCsrf(page.getResponse().getContentAsString());
        assertThat(hiddenCsrf).isNotBlank();

        mvc.perform(post("/admin/alumni/add")
                        .cookie(jwt, xsrfCookie)
                        .param("_csrf", hiddenCsrf)
                        .param("nama", "Alumni Baru Uji")
                        .param("jenjang", "S1")
                        .param("angkatan", "20"))
                .andExpect(status().is3xxRedirection());

        assertThat(users.findAll().stream()
                .filter(u -> "Alumni Baru Uji".equals(u.getNama())).count()).isEqualTo(1);
    }

    @Test
    void csrfHilangDialihkanBalikDenganPesanBukanHalamanIzin() throws Exception {
        Users admin = admin();
        Cookie jwt = new Cookie("jwt", jwtUtil.generateToken(admin));
        MockHttpSession session = new MockHttpSession();

        MvcResult hasil = mvc.perform(post("/admin/alumni/add")
                        .session(session)
                        .header("Referer", "http://localhost/admin/alumni?sort=asc")
                        .cookie(jwt)
                        .param("nama", "Tanpa CSRF")
                        .param("jenjang", "S1")
                        .param("angkatan", "20"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/alumni?sort=asc"))
                .andReturn();

        assertThat(hasil.getResponse().getContentAsString()).doesNotContain("Izin tidak mencukupi");

        // Halaman asal yang dimuat ulang (sesi yang sama) memuat pesan CSRF yang jelas
        // dan modal tambah terbuka lagi dengan isian yang tadi dikirim.
        String reloaded = mvc.perform(get("/admin/alumni?sort=asc").session(session).cookie(jwt))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(reloaded).contains("Sesi keamanan halaman sudah kedaluwarsa");
        assertThat(reloaded).contains("const openTambah = true");
        assertThat(reloaded).contains("value=\"Tanpa CSRF\"");

        assertThat(users.findAll().stream()
                .filter(u -> "Tanpa CSRF".equals(u.getNama())).count()).isZero();
    }

    @Test
    void halamanAdminTidakDisimpanBrowserAgarFormTidakBasi() throws Exception {
        Users admin = admin();
        Cookie jwt = new Cookie("jwt", jwtUtil.generateToken(admin));

        mvc.perform(get("/admin/alumni").cookie(jwt))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Cache-Control",
                                org.hamcrest.Matchers.containsString("no-store")));
    }

    @Test
    void csrfTidakCocokDialihkanBalikBukan403() throws Exception {
        Users admin = admin();
        Cookie jwt = new Cookie("jwt", jwtUtil.generateToken(admin));
        Cookie xsrfCookie = new Cookie("XSRF-TOKEN", "token-cookie-aaa");

        mvc.perform(post("/admin/alumni/add")
                        .header("Referer", "http://localhost/admin/alumni")
                        .cookie(jwt, xsrfCookie)
                        .param("_csrf", "token-param-bbb")
                        .param("nama", "Token Beda")
                        .param("jenjang", "S1")
                        .param("angkatan", "20"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/alumni"));

        assertThat(users.findAll().stream()
                .filter(u -> "Token Beda".equals(u.getNama())).count()).isZero();
    }

    @Test
    void userBukanAdminTetapMendapat403Izin() throws Exception {
        Users user = users.save(Users.builder()
                .nama("Alumni Biasa").email("alumni-flow@example.org")
                .password(passwordEncoder.encode("Password123"))
                .nomorHp("081234567890").angkatan(20L).jenjang("S1").role(Role.USER).build());
        Cookie jwt = new Cookie("jwt", jwtUtil.generateToken(user));

        // Pasangan cookie+param yang cocok lolos CSRF, tapi role USER ditolak untuk /admin/**.
        Cookie xsrfCookie = new Cookie("XSRF-TOKEN", "pasangan-valid");

        mvc.perform(post("/admin/alumni/add")
                        .cookie(jwt, xsrfCookie)
                        .param("_csrf", "pasangan-valid")
                        .param("nama", "Coba Ilegal")
                        .param("jenjang", "S1")
                        .param("angkatan", "20"))
                .andExpect(status().isForbidden());

        assertThat(users.findAll().stream()
                .filter(u -> "Coba Ilegal".equals(u.getNama())).count()).isZero();
    }

    @Test
    void jwtTidakValidMenuju401Bukan403() throws Exception {
        admin();
        Cookie invalidJwt = new Cookie("jwt", "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ4In0.tidak-valid");
        Cookie xsrfCookie = new Cookie("XSRF-TOKEN", "apa-saja");

        mvc.perform(post("/admin/alumni/add")
                        .cookie(invalidJwt, xsrfCookie)
                        .param("_csrf", "apa-saja")
                        .param("nama", "Kedaluwarsa")
                        .param("jenjang", "S1")
                        .param("angkatan", "20"))
                .andExpect(status().isUnauthorized());
    }

    private Users admin() {
        return users.save(Users.builder()
                .nama("Admin Uji").email("admin-flow@example.org")
                .password(passwordEncoder.encode("Password123"))
                .nomorHp("081234567890").angkatan(0L).jenjang("S1").role(Role.ADMIN).build());
    }

    private String extractCsrf(String html) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(html);
        if (m.find()) {
            return m.group(1);
        }
        m = java.util.regex.Pattern.compile("value=\"([^\"]+)\"[^>]*name=\"_csrf\"").matcher(html);
        return m.find() ? m.group(1) : "";
    }
}