package com.projek.sipatik.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import com.projek.sipatik.models.AdminToken;
import com.projek.sipatik.models.OtpToken;
import com.projek.sipatik.models.Role;
import com.projek.sipatik.models.Users;
import com.projek.sipatik.repositories.AdminTokenRepository;
import com.projek.sipatik.repositories.OtpTokenRepository;
import com.projek.sipatik.repositories.UserRepository;

class AuthServiceSecurityTest {

    @Test
    void otpCooldownMencegahEmailDanTokenTambahan() {
        AuthService service = spy(new AuthService(mock(JavaMailSender.class)));
        UserRepository users = mock(UserRepository.class);
        OtpTokenRepository tokens = mock(OtpTokenRepository.class);
        ReflectionTestUtils.setField(service, "userRepository", users);
        ReflectionTestUtils.setField(service, "otpTokenRepository", tokens);
        Users user = Users.builder().id(1L).email("user@example.org").role(Role.USER).build();
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        OtpToken latest = new OtpToken();
        latest.setCreatedAt(LocalDateTime.now());
        when(tokens.findTopByUserIdOrderByIdDesc(1L)).thenReturn(Optional.of(latest));

        assertThatThrownBy(() -> service.createAndSendOtp(user))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Tunggu");

        verify(tokens, never()).save(any(OtpToken.class));
        verify(service, never()).sendOtpEmail(anyString(), anyString(), any());
    }

    @Test
    void otpBaruMencabutKodeLamaSebelumMengirim() {
        AuthService service = spy(new AuthService(mock(JavaMailSender.class)));
        UserRepository users = mock(UserRepository.class);
        OtpTokenRepository tokens = mock(OtpTokenRepository.class);
        ReflectionTestUtils.setField(service, "userRepository", users);
        ReflectionTestUtils.setField(service, "otpTokenRepository", tokens);
        doNothing().when(service).sendOtpEmail(anyString(), anyString(), anyString());
        Users user = Users.builder().id(1L).nama("Alumni").email("user@example.org").role(Role.USER).build();
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        OtpToken previous = new OtpToken();
        previous.setCreatedAt(LocalDateTime.now().minusMinutes(2));
        previous.setVerified(false);
        when(tokens.findTopByUserIdOrderByIdDesc(1L)).thenReturn(Optional.of(previous));
        when(tokens.findAllByUserIdAndVerifiedFalse(1L)).thenReturn(List.of(previous));

        service.createAndSendOtp(user);

        assertThat(previous.isVerified()).isTrue();
        ArgumentCaptor<OtpToken> captured = ArgumentCaptor.forClass(OtpToken.class);
        verify(tokens).save(captured.capture());
        assertThat(captured.getValue().getFailedAttempts()).isZero();
        verify(service).sendOtpEmail(user.getEmail(), captured.getValue().getOtp(), user.getNama());
    }

    @Test
    void resendTokenAdminDitolakSelamaCooldownServer() {
        AuthService service = new AuthService(mock(JavaMailSender.class));
        AdminTokenRepository repository = mock(AdminTokenRepository.class);
        ReflectionTestUtils.setField(service, "adminTokenRepository", repository);

        AdminToken latest = new AdminToken();
        latest.setCreatedAt(LocalDateTime.now());
        latest.setLastResendTime(LocalDateTime.now());
        when(repository.findTopByEmailOrderByCreatedAtDesc("admin@example.org"))
                .thenReturn(Optional.of(latest));

        assertThatThrownBy(() -> service.resendToken("admin@example.org", new Users()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Tunggu")
                .hasMessageContaining("detik");

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any(AdminToken.class));
    }

    @Test
    void resendTokenMencabutTokenAktifDanMembuatKodeSekaliPakaiBaru() {
        AuthService service = spy(new AuthService(mock(JavaMailSender.class)));
        AdminTokenRepository repository = mock(AdminTokenRepository.class);
        ReflectionTestUtils.setField(service, "adminTokenRepository", repository);
        doNothing().when(service).sendTokenEmail(anyString(), anyString());

        AdminToken latest = new AdminToken();
        latest.setToken("token-lama");
        latest.setCreatedAt(LocalDateTime.now().minusMinutes(2));
        latest.setUsed(false);
        when(repository.findTopByEmailOrderByCreatedAtDesc("admin@example.org"))
                .thenReturn(Optional.of(latest));
        when(repository.findAllByEmailAndUsedFalse("admin@example.org"))
                .thenReturn(List.of(latest));
        when(repository.save(any(AdminToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.resendToken("admin@example.org", new Users());

        assertThat(latest.isUsed()).isTrue();
        verify(repository).saveAll(List.of(latest));

        ArgumentCaptor<AdminToken> tokenCaptor = ArgumentCaptor.forClass(AdminToken.class);
        verify(repository).save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().getToken())
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                .isNotEqualTo("token-lama");
        verify(service).sendTokenEmail("admin@example.org", tokenCaptor.getValue().getToken());
    }
}
