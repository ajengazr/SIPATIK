package com.projek.sipatik.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import com.projek.sipatik.dto.UserRequest;
import com.projek.sipatik.services.AlumniInvitationService;
import com.projek.sipatik.services.AlumniInvitationService.InvitationAccount;

class AuthControllerInvitationErrorTest {
    private static final String TOKEN = "abcdefghijklmnopqrstuvwxyzABCDEFGH123456789";

    private AuthController controller;
    private AlumniInvitationService invitationService;

    @BeforeEach
    void setUp() {
        controller = new AuthController();
        invitationService = mock(AlumniInvitationService.class);
        ReflectionTestUtils.setField(controller, "invitationService", invitationService);
        ReflectionTestUtils.setField(controller, "secureJwtCookie", true);
    }

    @Test
    void konflikConstraintMemulihkanIdentitasUndanganUntukPercobaanUlang() {
        UserRequest request = validRequest();
        when(invitationService.register(eq(TOKEN), same(request), anyMap()))
                .thenThrow(new DataIntegrityViolationException("unique email"));
        when(invitationService.findActive(TOKEN))
                .thenReturn(Optional.of(new InvitationAccount(42L, "Alumni Tepercaya", 20L)));

        ExtendedModelMap model = new ExtendedModelMap();
        String view = controller.prosesForm(
                request,
                TOKEN,
                new MockHttpServletResponse(),
                model,
                new RedirectAttributesModelMap());

        assertThat(view).isEqualTo("html/auth/daftar");
        assertThat(model.get("namaAlumni")).isEqualTo("Alumni Tepercaya");
        assertThat(model.get("angkatanAlumni")).isEqualTo(20L);
        assertThat(model.get("fieldErrors"))
                .isEqualTo(Map.of("email", "Email sudah digunakan"));
    }

    @Test
    void konflikConstraintDenganUndanganYangSudahTidakAktifDitolakBersih() {
        UserRequest request = validRequest();
        when(invitationService.register(eq(TOKEN), same(request), anyMap()))
                .thenThrow(new DataIntegrityViolationException("unique email"));
        when(invitationService.findActive(TOKEN)).thenReturn(Optional.empty());

        MockHttpServletResponse response = new MockHttpServletResponse();
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        String view = controller.prosesForm(request, TOKEN, response, new ExtendedModelMap(), redirect);

        assertThat(view).isEqualTo("redirect:/auth/cek");
        assertThat(response.getHeader("Set-Cookie"))
                .contains("sipatik_activation=", "Max-Age=0", "HttpOnly", "Secure", "SameSite=Lax");
        assertThat(redirect.getFlashAttributes()).containsKey("error");
    }

    private UserRequest validRequest() {
        return UserRequest.builder()
                .email("alumni@example.org")
                .password("Password123")
                .nomorHp("081234567899")
                .jenjang("S1")
                .build();
    }
}
