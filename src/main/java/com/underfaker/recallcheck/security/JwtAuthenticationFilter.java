package com.underfaker.recallcheck.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 요청마다 토큰 파싱 후 인증 주입.
 *
 * 토큰 안에 user_id·email·role 이 들어 있으므로 요청마다 DB 를 조회하지 않는다.
 * (권한을 즉시 회수해야 하는 요구사항이 생기면 그때 CustomUserDetailsService 조회로 바꿀 것)
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = jwtProvider.resolveToken(request.getHeader(JwtProvider.HEADER));

        if (token != null
                && jwtProvider.validate(token)
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            CustomUserDetailsService.CustomUserDetails principal = new CustomUserDetailsService.CustomUserDetails(
                    jwtProvider.getUserId(token),
                    jwtProvider.getEmail(token),
                    "",
                    jwtProvider.getRole(token));

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }
}
