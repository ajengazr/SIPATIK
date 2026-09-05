package com.projek.sipatik.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import com.projek.sipatik.dto.LoginRequest;
import com.projek.sipatik.dto.LoginResponse;
import com.projek.sipatik.models.Role;
import com.projek.sipatik.models.Users;
import com.projek.sipatik.repositories.UserRepository;
import com.projek.sipatik.services.AuthService;

class JwtCookieSecurityTest {

    @Test
    void cookieLoginUserMemakaiAtributKeamanan() {
        AuthController controller = new AuthController();
        ReflectionTestUtils.setField(controller, "secureJwtCookie", true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        ReflectionTestUtils.invokeMethod(controller, "addJwtCookie", response, "token-user", 3600L);

        assertCookieAman(response.getHeader(HttpHeaders.SET_COOKIE), "token-user");
    }

    @Test
    void cookieLoginAdminMemakaiAtributKeamanan() {
        AdmAuthController controller = new AdmAuthController();
        ReflectionTestUtils.setField(controller, "secureJwtCookie", true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        ReflectionTestUtils.invokeMethod(controller, "addJwtCookie", response, "token-admin");

        assertCookieAman(response.getHeader(HttpHeaders.SET_COOKIE), "token-admin");
    }

    @Test
    void modeHttpLokalDapatMenonaktifkanSecureTanpaMelepasProteksiLain() {
        AuthController controller = new AuthController();
        ReflectionTestUtils.setField(controller, "secureJwtCookie", false);
        MockHttpServletResponse response = new MockHttpServletResponse();

        ReflectionTestUtils.invokeMethod(controller, "addJwtCookie", response, "token-local", 3600L);

        String header = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(header)
                .contains("HttpOnly")
                .contains("SameSite=Lax")
                .doesNotContain("Secure");
    }

    @Test
    void loginAlumniMenolakAdminSebelumCookieDibuat() {
        AuthController controller = new AuthController();
        AuthService authService = mock(AuthService.class);
        ReflectionTestUtils.setField(controller, "authService", authService);
        ReflectionTestUtils.setField(controller, "secureJwtCookie", true);

        LoginRequest request = new LoginRequest();
        request.setEmail("admin@example.org");
        request.setPassword("Rahasia1");
        BeanPropertyBindingResult errors = new BeanPropertyBindingResult(request, "loginRequest");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(authService.validateLogin("admin@example.org", "Rahasia1"))
                .thenReturn(new LoginResponse("admin-token", "admin@example.org", "Admin", 0L, Role.ADMIN));

        String view = controller.processLogin(
                request,
                errors,
                response,
                new RedirectAttributesModelMap());

        assertThat(view).isEqualTo("html/auth/login");
        assertThat(errors.getFieldError("email")).isNotNull();
        assertThat(errors.getFieldError("email").getDefaultMessage())
                .isEqualTo("Akun admin harus masuk melalui halaman login admin.");
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }

    @Test
    void resendAdminTidakMembocorkanKeberadaanAtauRoleAkun() {
        AdmAuthController controller = new AdmAuthController();
        AuthService authService = mock(AuthService.class);
        UserRepository userRepository = mock(UserRepository.class);
        ReflectionTestUtils.setField(controller, "authService", authService);
        ReflectionTestUtils.setField(controller, "userRepository", userRepository);

        when(userRepository.findByEmail("tidak-ada@example.org")).thenReturn(Optional.empty());
        ResponseEntity<Map<String, String>> tidakAda = controller.resendToken("tidak-ada@example.org");

        Users alumni = new Users();
        alumni.setRole(Role.USER);
        when(userRepository.findByEmail("alumni@example.org")).thenReturn(Optional.of(alumni));
        ResponseEntity<Map<String, String>> roleSalah = controller.resendToken("alumni@example.org");

        assertThat(tidakAda.getStatusCode()).isEqualTo(roleSalah.getStatusCode());
        assertThat(tidakAda.getBody()).isEqualTo(roleSalah.getBody());
        verifyNoInteractions(authService);
    }

    @Test
    void resendAdminSaatCooldownTetapMemberiResponsGenerikYangSama() {
        AdmAuthController controller = new AdmAuthController();
        AuthService authService = mock(AuthService.class);
        UserRepository userRepository = mock(UserRepository.class);
        ReflectionTestUtils.setField(controller, "authService", authService);
        ReflectionTestUtils.setField(controller, "userRepository", userRepository);

        Users admin = new Users();
        admin.setRole(Role.ADMIN);
        when(userRepository.findByEmail("admin@example.org")).thenReturn(Optional.of(admin));
        doThrow(new IllegalStateException("cooldown internal"))
                .when(authService).resendToken("admin@example.org", admin);

        ResponseEntity<Map<String, String>> response = controller.resendToken("admin@example.org");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody())
                .containsEntry("status", "accepted")
                .doesNotContainValue("cooldown internal");
        verify(authService).resendToken("admin@example.org", admin);
    }

    private void assertCookieAman(String header, String token) {
        assertThat(header)
                .startsWith("jwt=" + token)
                .contains("Path=/")
                .contains("Max-Age=3600")
                .contains("Secure")
                .contains("HttpOnly")
                .contains("SameSite=Lax");
    }
}
