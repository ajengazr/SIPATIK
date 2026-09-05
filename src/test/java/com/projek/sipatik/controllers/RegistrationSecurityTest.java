package com.projek.sipatik.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDateTime;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.projek.sipatik.models.AlumniInvitation;
import com.projek.sipatik.models.Role;
import com.projek.sipatik.models.Users;
import com.projek.sipatik.repositories.AlumniInvitationRepository;
import com.projek.sipatik.repositories.UserRepository;
import com.projek.sipatik.services.AlumniInvitationService;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RegistrationSecurityTest {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private AlumniInvitationRepository invitations;
    @Autowired private AlumniInvitationService invitationService;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void postPendaftaranTanpaUndanganTidakDapatMengklaimUserKosongAtauAdmin() throws Exception {
        Users candidate = candidate("Kandidat Tanpa Undangan");
        Users admin = active(Role.ADMIN, "Admin Tidak Bisa Diambil", "admin-register@example.org");

        postRegistration(null).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/auth/cek"));

        assertThat(users.findById(candidate.getId()).orElseThrow().getEmail()).isNull();
        Users unchangedAdmin = users.findById(admin.getId()).orElseThrow();
        assertThat(unchangedAdmin.getEmail()).isEqualTo("admin-register@example.org");
        assertThat(passwordEncoder.matches("Password123", unchangedAdmin.getPassword())).isTrue();
    }

    @Test
    void undanganMemakaiToken256BitHashOnlyDanPembuatanUlangMembatalkanLinkLama() {
        Users candidate = candidate("Kandidat Token");

        var first = invitationService.issue(candidate.getId(), "admin@example.org");
        assertThat(Base64.getUrlDecoder().decode(first.token())).hasSize(32);
        AlumniInvitation stored = invitations.findByUserId(candidate.getId()).orElseThrow();
        assertThat(stored.getTokenHash()).hasSize(64).doesNotContain(first.token());
        assertThat(stored.getCreatedBy()).isEqualTo("admin@example.org");

        var second = invitationService.issue(candidate.getId(), "admin@example.org");
        assertThat(second.token()).isNotEqualTo(first.token());
        assertThat(invitationService.findActive(first.token())).isEmpty();
        assertThat(invitationService.findActive(second.token())).isPresent();
    }

    @Test
    void tautanDitukarKeCookieBersihDanUndanganHanyaBisaDipakaiSekali() throws Exception {
        Users candidate = candidate("Kandidat Aktivasi");
        var invitation = invitationService.issue(candidate.getId(), "admin@example.org");

        mvc.perform(get("/auth/aktivasi").param("token", invitation.token()))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/auth/daftar"))
                .andExpect(header().string("Cache-Control", "no-store, max-age=0"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("sipatik_activation="),
                        org.hamcrest.Matchers.containsString("HttpOnly"),
                        org.hamcrest.Matchers.containsString("SameSite=Lax"))));

        Cookie activation = new Cookie("sipatik_activation", invitation.token());
        mvc.perform(get("/auth/daftar").cookie(activation))
                .andExpect(status().isOk())
                .andExpect(view().name("html/auth/daftar"));

        postRegistration(activation)
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.startsWith("/auth/sukses?message=")))
                .andExpect(header().string("Set-Cookie",
                        org.hamcrest.Matchers.containsString("sipatik_activation=;")));

        Users registered = users.findById(candidate.getId()).orElseThrow();
        assertThat(registered.getEmail()).isEqualTo("invited@example.org");
        assertThat(passwordEncoder.matches("Password123", registered.getPassword())).isTrue();
        assertThat(invitations.findByUserId(candidate.getId()).orElseThrow().getUsedAt()).isNotNull();

        postRegistration(activation)
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/auth/cek"));
    }

    @Test
    void endpointAdminMembuatUrlUndanganDanMenolakAkunAktif() throws Exception {
        Users admin = active(Role.ADMIN, "Admin Pembuat", "admin-invite@example.org");
        Users candidate = candidate("Alumni Diundang");
        Users active = active(Role.USER, "Alumni Aktif", "already-active@example.org");

        mvc.perform(post("/admin/alumni/{id}/undangan", candidate.getId())
                        .header("Host", "attacker.example")
                        .with(authentication(adminAuthentication(admin))).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.url")
                        .value(org.hamcrest.Matchers.startsWith("/auth/aktivasi?token=")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.token")
                        .doesNotExist());

        mvc.perform(post("/admin/alumni/{id}/undangan", active.getId())
                        .with(authentication(adminAuthentication(admin))).with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void endpointUndanganMemerlukanCsrfDanRoleAdmin() throws Exception {
        Users admin = active(Role.ADMIN, "Admin CSRF", "admin-csrf-invite@example.org");
        Users ordinaryUser = active(Role.USER, "User Bukan Admin", "user-cannot-invite@example.org");
        Users candidate = candidate("Alumni Dilindungi CSRF");

        mvc.perform(post("/admin/alumni/{id}/undangan", candidate.getId())
                        .with(authentication(adminAuthentication(admin))))
                .andExpect(status().isForbidden());

        mvc.perform(post("/admin/alumni/{id}/undangan", candidate.getId())
                        .with(authentication(userAuthentication(ordinaryUser))).with(csrf()))
                .andExpect(status().isForbidden());
        assertThat(invitations.findByUserId(candidate.getId())).isEmpty();
    }

    @Test
    void undanganKedaluwarsaDitolakDanTidakMenjadiCookieAktivasi() throws Exception {
        Users candidate = candidate("Kandidat Kedaluwarsa");
        var issued = invitationService.issue(candidate.getId(), "admin@example.org");
        AlumniInvitation stored = invitations.findByUserId(candidate.getId()).orElseThrow();
        stored.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        invitations.saveAndFlush(stored);

        assertThat(invitationService.findActive(issued.token())).isEmpty();
        mvc.perform(get("/auth/aktivasi").param("token", issued.token()))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/auth/cek"))
                .andExpect(header().stringValues("Set-Cookie",
                        org.hamcrest.Matchers.hasItem(
                                org.hamcrest.Matchers.containsString("sipatik_activation=;"))));
    }

    @Test
    void undanganTidakMenghalangiAdminMenghapusAlumniTanpaRiwayat() throws Exception {
        Users admin = active(Role.ADMIN, "Admin Penghapus", "admin-delete-invite@example.org");
        Users candidate = candidate("Alumni Undangan Dihapus");
        invitationService.issue(candidate.getId(), admin.getEmail());

        mvc.perform(post("/admin/alumni/hapus/{id}", candidate.getId())
                        .with(authentication(adminAuthentication(admin))).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/alumni?sort=asc"));

        assertThat(users.findById(candidate.getId())).isEmpty();
        assertThat(invitations.findByUserId(candidate.getId())).isEmpty();
    }

    @Test
    void apiNamaPublikTidakPernahMenyertakanAdmin() throws Exception {
        Users admin = active(Role.ADMIN, "Nama Admin Rahasia", "hidden-admin@example.org");
        admin.setAngkatan(19L);
        users.save(admin);
        Users alumni = active(Role.USER, "Nama Alumni Publik", "visible-user@example.org");
        alumni.setAngkatan(19L);
        users.save(alumni);

        mvc.perform(get("/api/nama-by-angkatan").param("angkatan", "19"))
                .andExpect(status().isOk())
                .andExpect(content().json("[\"Nama Alumni Publik\"]"));
    }

    private org.springframework.test.web.servlet.ResultActions postRegistration(Cookie activation) throws Exception {
        var request = post("/auth/pendaftaran").with(csrf())
                .param("email", "  INVITED@Example.ORG ")
                .param("password", "Password123")
                .param("nomorHp", "081234567899")
                .param("jenjang", "S1");
        if (activation != null) {
            request.cookie(activation);
        }
        return mvc.perform(request);
    }

    private Users candidate(String nama) {
        return users.save(Users.builder().nama(nama).angkatan(20L).jenjang("S1").role(Role.USER).build());
    }

    private Users active(Role role, String nama, String email) {
        return users.save(Users.builder()
                .nama(nama).email(email).password(passwordEncoder.encode("Password123"))
                .nomorHp("081234567890").angkatan(20L).jenjang("S1").role(role).build());
    }

    private UsernamePasswordAuthenticationToken adminAuthentication(Users admin) {
        return new UsernamePasswordAuthenticationToken(
                admin,
                null,
                java.util.List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private UsernamePasswordAuthenticationToken userAuthentication(Users user) {
        return new UsernamePasswordAuthenticationToken(
                user,
                null,
                java.util.List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
