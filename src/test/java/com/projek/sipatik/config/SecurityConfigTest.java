package com.projek.sipatik.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.projek.sipatik.models.Role;
import com.projek.sipatik.models.Users;
import com.projek.sipatik.repositories.UserRepository;

class SecurityConfigTest {

    private final SecurityConfig securityConfig = new SecurityConfig();
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    @Test
    void bootstrapAdminTidakMengaksesDatabaseSaatDinonaktifkan() throws Exception {
        CommandLineRunner runner = securityConfig.initAdmin(
                userRepository, passwordEncoder, false, "", "", "");

        runner.run();

        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    void bootstrapAdminAktifMewajibkanEmailDanPassword() {
        CommandLineRunner tanpaEmail = securityConfig.initAdmin(
                userRepository, passwordEncoder, true, "Bendahara", "", "rahasia-kuat");
        CommandLineRunner tanpaPassword = securityConfig.initAdmin(
                userRepository, passwordEncoder, true, "Bendahara", "admin@example.org", "");

        assertThatThrownBy(tanpaEmail::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_BOOTSTRAP_ADMIN_EMAIL");
        assertThatThrownBy(tanpaPassword::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_BOOTSTRAP_ADMIN_PASSWORD");

        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    void bootstrapAdminMenggunakanKredensialYangDikonfigurasi() throws Exception {
        when(userRepository.findByEmail("admin@example.org")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("rahasia-kuat")).thenReturn("bcrypt-hash");
        CommandLineRunner runner = securityConfig.initAdmin(
                userRepository,
                passwordEncoder,
                true,
                "Bendahara Utama",
                " admin@example.org ",
                "rahasia-kuat");

        runner.run();

        ArgumentCaptor<Users> captor = ArgumentCaptor.forClass(Users.class);
        verify(userRepository).save(captor.capture());
        Users admin = captor.getValue();
        assertThat(admin.getNama()).isEqualTo("Bendahara Utama");
        assertThat(admin.getEmail()).isEqualTo("admin@example.org");
        assertThat(admin.getPassword()).isEqualTo("bcrypt-hash");
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.getAngkatan()).isZero();
    }

    @Test
    void bootstrapAdminTidakMenimpaAkunYangSudahAda() throws Exception {
        when(userRepository.findByEmail("admin@example.org")).thenReturn(Optional.of(new Users()));
        CommandLineRunner runner = securityConfig.initAdmin(
                userRepository,
                passwordEncoder,
                true,
                "Bendahara",
                "admin@example.org",
                "rahasia-kuat");

        runner.run();

        verify(userRepository, never()).save(any(Users.class));
        verifyNoInteractions(passwordEncoder);
    }
}
