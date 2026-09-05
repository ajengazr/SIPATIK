package com.projek.sipatik.security;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.projek.sipatik.models.Users;
import com.projek.sipatik.repositories.UserRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(JwtFilter.class);

    /**
     * Path yang benar-benar publik. Perhatikan bahwa "/api" TIDAK ada di sini:
     * versi sebelumnya melewati seluruh prefix /api tanpa mengisi SecurityContext,
     * padahal SecurityConfig mewajibkan role untuk /api/**, sehingga semua endpoint
     * REST selalu berakhir 401 dan tidak pernah bisa dipakai.
     */
    private static final List<String> PATH_PUBLIK = List.of(
            "/auth",
            "/auth-adm",
            "/css",
            "/js",
            "/assets",
            "/images",
            "/webjars",
            "/.well-known",
            "/test-error");

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getServletPath();

        if (path.equals("/") || path.equals("/error") || PATH_PUBLIK.stream().anyMatch(path::startsWith)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = ambilToken(request, path);

        if (token != null && jwtUtil.validateToken(token)) {
            try {
                String email = jwtUtil.extractEmail(token);
                Optional<Users> userOpt = userRepository.findByEmail(email);

                if (userOpt.isPresent()) {
                    Users user = userOpt.get();
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(user, null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (Exception e) {
                log.warn("JWT validation error untuk {}: {}", path, e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Token dibaca dari cookie untuk halaman web, dan dari header Authorization untuk
     * klien REST.
     *
     * Khusus /api/**, cookie sengaja tidak diterima. Kredensial yang dikirim browser
     * secara otomatis adalah syarat terjadinya CSRF; dengan hanya menerima header,
     * permukaan REST menjadi benar-benar stateless dan boleh dikecualikan dari
     * proteksi CSRF di SecurityConfig.
     */
    private String ambilToken(HttpServletRequest request, String path) {
        String dariHeader = jwtUtil.resolveTokenFromRequest(request);
        if (dariHeader != null) {
            return dariHeader;
        }
        if (path.startsWith("/api/")) {
            return null;
        }
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("jwt".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
