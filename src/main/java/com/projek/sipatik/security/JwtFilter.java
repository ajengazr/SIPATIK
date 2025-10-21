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
    
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @Override
    @SuppressWarnings("null")
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getServletPath();
        
        // Skip JWT validation for public paths
        if (path.startsWith("/auth")
                || path.startsWith("/auth-adm")
                || path.startsWith("/api")
                || path.startsWith("/css")
                || path.startsWith("/js")
                || path.startsWith("/assets")
                || path.startsWith("/images")
                || path.startsWith("/webjars")
                || path.startsWith("/.well-known")
                || path.equals("/error")
                || path.startsWith("/test-error")
                || path.equals("/")) {
            filterChain.doFilter(request, response);
            return;
        }
        
        // For protected paths, check JWT token
        String token = null;

        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("jwt".equals(cookie.getName())) {
                    token = cookie.getValue();
                    break;
                }
            }
        }

        if (token != null) {
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
                log.error("JWT validation error: {}", e.getMessage());
            }
        }
        
        filterChain.doFilter(request, response);
    }
}
