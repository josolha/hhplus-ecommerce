package com.sparta.ecommerce.infrastructure.config.security;


import com.sparta.ecommerce.domain.user.entity.Role;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = resolveBearerToken(request);

        // 토큰 없으면 통과
        if (!StringUtils.hasText(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // 1) 기본 검증
            if (!jwtProvider.validate(token)) {
                fail(request, response, "INVALID_TOKEN");
                return;
            }

            // 2) 인증 세팅
            setAuthentication(request, token);

            filterChain.doFilter(request, response);

        } catch (ExpiredJwtException e) {
            // 만료된 토큰
            fail(request, response, "EXPIRED_TOKEN", e);
        } catch (JwtException | IllegalArgumentException e) {
            // 유효하지 않은 토큰
            fail(request, response, "INVALID_TOKEN", e);
        }
    }

    private void setAuthentication(HttpServletRequest request, String token) {
        String userId = jwtProvider.getUserId(token);
        Role role = jwtProvider.getRole(token);

        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
        var authentication = new UsernamePasswordAuthenticationToken(userId, null, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private void fail(HttpServletRequest request, HttpServletResponse response, String code)
            throws IOException, ServletException {
        fail(request, response, code, null);
    }

    private void fail(HttpServletRequest request, HttpServletResponse response, String code, Exception e)
            throws IOException, ServletException {
        SecurityContextHolder.clearContext();
        authenticationEntryPoint.commence(
                request,
                response,
                new org.springframework.security.core.AuthenticationException(code, e) {}
        );
    }

    private String resolveBearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(header) || !header.startsWith("Bearer ")){
            return null;
        }
        return header.substring(7);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return    path.equals("/api/auth/signup")
                || path.equals("/api/auth/login")
                || path.equals("/api/auth/refresh");
    }
}
