package com.msg.backend.global.security.jwt;

import com.msg.backend.domain.user.entity.Role;
import com.msg.backend.global.security.AuthUser;
import com.msg.backend.global.security.SessionValidator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authorization: Bearer 토큰이 유효하고 세션이 살아 있으면 인증 정보를 채운다.
 * 토큰이 없거나 틀리면 아무것도 하지 않는다 (보호된 API면 뒤에서 401, 공개 API면 그대로 통과).
 * 서블릿 필터로 자동 등록되지 않도록 빈이 아니라 SecurityConfig에서 직접 만든다.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtTokenProvider tokenProvider;
	private final SessionValidator sessionValidator;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header != null && header.startsWith(BEARER_PREFIX)) {
			tokenProvider.parse(header.substring(BEARER_PREFIX.length()))
					.ifPresent(user -> sessionValidator.findActiveRole(user.userId(), user.sessionId())
							.ifPresent(role -> authenticate(user, role)));
		}
		chain.doFilter(request, response);
	}

	// 역할은 토큰이 아니라 요청마다 DB에서 읽은 값을 쓴다 (역할을 바꾸면 바로 적용)
	private void authenticate(AuthUser user, Role role) {
		var authentication = new UsernamePasswordAuthenticationToken(
				user, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
		SecurityContextHolder.getContext().setAuthentication(authentication);
	}
}
