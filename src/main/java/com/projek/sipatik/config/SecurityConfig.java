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
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.support.SessionFlashMapManager;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

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
                        // Rotasi token CSRF bawaan (CsrfAuthenticationStrategy) sengaja dimatikan.
                        // Strategi itu menganggap tiap autentikasi sebagai sesi baru dan mengganti
                        // token CSRF, tapi JwtFilter membangun autentikasi pada SETIAP request
                        // (aplikasi stateless), sehingga token berganti di tiap respons. Akibatnya
                        // cookie XSRF-TOKEN selalu berubah setelah halaman dirender — mis. oleh
                        // request favicon/aset yang ikut terautentikasi — dan token di form tidak
                        // pernah cocok dengan cookie saat dikirim, jadi semua submit form ditolak
                        // dengan InvalidCsrfTokenException.
                        .sessionAuthenticationStrategy((authentication, request, response) -> {
                            // no-op: token CSRF tetap stabil antar request
                        })
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
                            } else if (accessDeniedException instanceof MissingCsrfTokenException
                                    || accessDeniedException instanceof InvalidCsrfTokenException) {
                                // Token CSRF hilang atau tidak cocok pada form web. Ini bukan soal izin:
                                // menampilkan halaman 403 "Izin tidak mencukupi" hanya membingungkan admin
                                // (halaman yang sama sering kebetulan punya pasangan cookie+token yang
                                // valid). Kembalikan ke halaman asal dengan pesan singkat; halaman yang
                                // dimuat ulang otomatis membawa pasangan cookie+token yang baru.
                                //
                                // Untuk form tambah alumni, data yang sudah diketik ikut dikembalikan
                                // lewat flash: modal terbuka lagi dengan isian tetap utuh, dan token
                                // halaman yang baru pasti cocok dengan cookie.
                                log.info("CSRF ditolak untuk {} {} dari {} ({})", request.getMethod(),
                                        request.getRequestURI(), request.getRemoteAddr(),
                                        accessDeniedException.getClass().getSimpleName());
                                FlashMap flashMap = new FlashMap();
                                if ("/admin/alumni/add".equals(request.getRequestURI())) {
                                    Map<String, Object> formData = new HashMap<>();
                                    if (request.getParameter("nama") != null) {
                                        formData.put("nama", request.getParameter("nama"));
                                    }
                                    if (request.getParameter("jenjang") != null) {
                                        formData.put("jenjang", request.getParameter("jenjang"));
                                    }
                                    if (request.getParameter("angkatan") != null) {
                                        try {
                                            formData.put("angkatan", Long.valueOf(request.getParameter("angkatan")));
                                        } catch (NumberFormatException ignored) {
                                            // biarkan kosong; validasi server yang menegur
                                        }
                                    }
                                    if (!formData.isEmpty()) {
                                        flashMap.put("alumniFormData", formData);
                                        flashMap.put("openTambahModal", true);
                                        flashMap.put("error",
                                                "Sesi keamanan halaman sudah kedaluwarsa. Isian Anda sudah dikembalikan ke formulir — cukup klik Simpan sekali lagi.");
                                    } else {
                                        flashMap.put("error",
                                                "Sesi keamanan halaman sudah kedaluwarsa. Muat ulang halaman lalu coba lagi.");
                                    }
                                } else {
                                    flashMap.put("error",
                                            "Sesi keamanan halaman sudah kedaluwarsa. Muat ulang halaman lalu coba lagi.");
                                }
                                try {
                                    new SessionFlashMapManager().saveOutputFlashMap(flashMap, request, response);
                                } catch (Exception e) {
                                    log.warn("Gagal menyimpan pesan CSRF: {}", e.getMessage());
                                }
                                response.sendRedirect(jalurKembali(request));
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
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                // Halaman admin/user berisi form dengan token CSRF sekali pakai. Kalau
                // browser menyajikan salinan lama (back/forward cache), token di form
                // tidak cocok lagi dengan cookie dan semua submit jadi ditolak dengan
                // kesan "izin tidak mencukupi". No-store memaksa halaman selalu segar.
                .addFilterAfter(new NoStoreCacheFilter(), CsrfCookieFilter.class);

        return http.build();
    }

    /**
     * Tujuan pengalihan saat CSRF gagal: halaman asal (header Referer) selama masih
     * same-origin, agar redirect tidak bisa diarahkan ke situs luar lewat header Host
     * atau X-Forwarded-Host. Tanpa referer yang aman, kembali ke beranda.
     */
    private String jalurKembali(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer != null) {
            try {
                URI uri = URI.create(referer);
                if (uri.getHost() != null
                        && uri.getHost().equalsIgnoreCase(request.getServerName())
                        && (uri.getPort() == -1 || uri.getPort() == request.getServerPort())) {
                    String path = uri.getRawPath();
                    if (path != null && !path.isBlank()) {
                        return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
                    }
                }
            } catch (IllegalArgumentException e) {
                // Referer tidak valid; jatuh ke "/"
            }
        }
        return "/";
    }

    /**
     * Membaca token CSRF pada setiap request agar CookieCsrfTokenRepository benar-benar
     * menulis cookie XSRF-TOKEN. Tanpa ini token dibuat malas dan cookie baru muncul
     * setelah ada request yang gagal.
     */
    static class NoStoreCacheFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                FilterChain filterChain) throws ServletException, IOException {
            String path = request.getServletPath();
            if (path.startsWith("/admin/") || path.startsWith("/user/")) {
                response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
                response.setHeader("Pragma", "no-cache");
            }
            filterChain.doFilter(request, response);
        }
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
