package com.projek.sipatik.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

import com.projek.sipatik.models.Role;
import com.projek.sipatik.models.Users;
import com.projek.sipatik.repositories.UserRepository;
import com.projek.sipatik.security.JwtFilter;

import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {
    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Autowired
    private JwtFilter jwtFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // Token CSRF disimpan di cookie XSRF-TOKEN yang bisa dibaca JavaScript, supaya
        // form Thymeleaf (token otomatis disuntik) maupun pemanggilan fetch (header
        // X-XSRF-TOKEN) sama-sama terlindungi. Autentikasi halaman web memakai cookie,
        // jadi tanpa CSRF setiap aksi admin bisa dipicu dari situs lain.
        //
        // /api/** dikecualikan karena JwtFilter hanya menerima header Authorization di
        // sana: tidak ada kredensial yang dikirim browser secara otomatis, sehingga
        // permukaan itu memang tidak rentan CSRF.
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName(null);

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(csrfHandler)
                        .ignoringRequestMatchers("/api/**"))
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers("/auth/**", "/auth-adm/**", "/", "/favicon.ico", "/css/**", "/js/**",
                                "/assets/**", "/uploads/**", "/error/**")
                        .permitAll()
                        .requestMatchers("/api/nama-by-angkatan").permitAll()
                        .requestMatchers("/admin/api/**").hasRole("ADMIN")
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/user/**").hasRole("USER")
                        .requestMatchers("/api/**").hasAnyRole("USER", "ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            if (request.getRequestURI().startsWith("/api/")) {
                                response.setContentType("application/json");
                                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                                response.getWriter().write("{\"timestamp\":\"" + LocalDateTime.now() +
                                        "\",\"status\":403,\"error\":\"Forbidden\",\"message\":\"Anda tidak memiliki akses ke halaman ini\",\"path\":\""
                                        + request.getRequestURI() + "\"}");
                            } else {
                                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                            }
                        })
                        .authenticationEntryPoint((request, response, authException) -> {
                            if (request.getRequestURI().startsWith("/api/")) {
                                response.setContentType("application/json");
                                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                response.getWriter().write("{\"timestamp\":\"" + LocalDateTime.now() +
                                        "\",\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Token tidak valid atau tidak ditemukan\",\"path\":\""
                                        + request.getRequestURI() + "\"}");
                            } else {
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                            }
                        })

                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                // Paksa token dibuat pada tiap request halaman supaya cookie XSRF-TOKEN
                // sudah tersedia sebelum JavaScript membutuhkannya.
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Membaca token CSRF pada setiap request agar CookieCsrfTokenRepository benar-benar
     * menulis cookie XSRF-TOKEN. Tanpa ini token dibuat malas dan cookie baru muncul
     * setelah ada request yang gagal.
     */
    static class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                FilterChain filterChain) throws ServletException, IOException {
            CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (token != null) {
                token.getToken();
            }
            filterChain.doFilter(request, response);
        }
    }

    @Bean
    CommandLineRunner initAdmin(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.bootstrap-admin.enabled:false}") boolean bootstrapEnabled,
            @Value("${app.bootstrap-admin.name:Bendahara Eksternal}") String bootstrapName,
            @Value("${app.bootstrap-admin.email:}") String bootstrapEmail,
            @Value("${app.bootstrap-admin.password:}") String bootstrapPassword) {
        return args -> {
            if (!bootstrapEnabled) {
                return;
            }

            String email = bootstrapEmail == null ? "" : bootstrapEmail.trim().toLowerCase(java.util.Locale.ROOT);
            if (email.isBlank() || bootstrapPassword == null || bootstrapPassword.isBlank()) {
                throw new IllegalStateException(
                        "Bootstrap admin aktif, tetapi APP_BOOTSTRAP_ADMIN_EMAIL atau "
                                + "APP_BOOTSTRAP_ADMIN_PASSWORD belum diisi");
            }

            if (userRepository.findByEmail(email).isEmpty()) {
                Users admin = new Users();
                admin.setNama(bootstrapName == null || bootstrapName.isBlank()
                        ? "Bendahara Eksternal"
                        : bootstrapName.trim());
                admin.setEmail(email);
                admin.setPassword(passwordEncoder.encode(bootstrapPassword));
                admin.setRole(Role.ADMIN);
                admin.setAngkatan(0L);

                userRepository.save(admin);
                log.info("Bootstrap admin account created for configured email");
            } else {
                log.info("Bootstrap admin skipped because the configured account already exists");
            }
        };
    }
}
