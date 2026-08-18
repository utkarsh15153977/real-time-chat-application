package com.chatapp.auth_service.security;

import com.chatapp.auth_service.repository.BlacklistedTokenRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;
    private final BlacklistedTokenRepository blacklistRepository;
    public JwtAuthenticationFilter(
            JwtUtil jwtUtil,
            BlacklistedTokenRepository blacklistRepository) {
        this.jwtUtil = jwtUtil;
        this.blacklistRepository = blacklistRepository;
    }
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {
        String authHeader =
                request.getHeader("Authorization");
        /*
         * Check whether Authorization header exists
         * and contains a Bearer token.
         */
        if (authHeader != null
                && authHeader.startsWith("Bearer ")
                && SecurityContextHolder
                .getContext()
                .getAuthentication() == null) {
            String token = authHeader.substring(7);
            try {
                /*
                 * 1. Check whether token has been blacklisted.
                 *
                 * If the user has already logged out,
                 * this JWT must not authenticate the request.
                 */
                if (blacklistRepository
                        .findByToken(token)
                        .isPresent()) {
                    SecurityContextHolder.clearContext();
                    filterChain.doFilter(
                            request,
                            response
                    );
                    return;
                }
                /*
                 * 2. Validate JWT signature and expiration.
                 */
                if (jwtUtil.validateToken(token)) {
                    /*
                     * 3. Extract information from JWT.
                     */
                    String email =
                            jwtUtil.extractUsername(token);
                    Long userId =
                            jwtUtil.extractUserId(token);
                    /*
                     * 4. Create Spring Security Authentication.
                     */
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    email,
                                    null,
                                    Collections.singletonList(
                                            new SimpleGrantedAuthority(
                                                    "ROLE_USER"
                                            )
                                    )
                            );
                    /*
                     * Store userId in authentication details.
                     */
                    authentication.setDetails(userId);
                    /*
                     * 5. Store authentication in SecurityContext.
                     */
                    SecurityContextHolder
                            .getContext()
                            .setAuthentication(
                                    authentication
                            );
                }
            } catch (Exception e) {
                /*
                 * Invalid/expired JWT or any other JWT error.
                 * Clear authentication and continue.
                 */
                SecurityContextHolder.clearContext();
            }
        }
        /*
         * Continue request processing.
         */
        filterChain.doFilter(
                request,
                response
        );
    }
}