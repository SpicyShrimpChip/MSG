package com.msg.backend.global.security;

import com.msg.backend.global.security.jwt.JwtAuthenticationFilter;
import com.msg.backend.global.security.jwt.JwtTokenProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	// 로그인 없이 쓸 수 있는 주소. 나머지는 모두 로그인이 필요하다
	private static final String[] PUBLIC_GET = {"/api/notices", "/api/notices/**",
			// 소셜 로그인: 브라우저가 직접 이동하는 시작·콜백 주소와 버튼 목록, 가입 마무리 정보(서명 쿠키로 확인)
			"/api/auth/social/providers", "/api/auth/kakao/start", "/api/auth/kakao/callback", "/api/auth/social/pending"};
	private static final String[] PUBLIC_AUTH = {
			"/api/auth/signup", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout"
	};
	private static final String[] PUBLIC_DOCS = {"/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**"};

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtTokenProvider tokenProvider,
			SessionValidator sessionValidator, ApiErrorResponder errorResponder) throws Exception {
		http
				// 인증은 Authorization 헤더의 Access Token으로 한다. 쿠키(Refresh Token)를 쓰는 /api/auth/**는
				// SameSite=Strict 쿠키 + X-Requested-With 헤더 확인(AuthController)으로 CSRF를 막는다
				.csrf(AbstractHttpConfigurer::disable)
				.cors(Customizer.withDefaults())
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.exceptionHandling(handling -> handling
						.authenticationEntryPoint(errorResponder)
						.accessDeniedHandler(errorResponder))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.GET, PUBLIC_GET).permitAll()
						.requestMatchers(HttpMethod.POST, PUBLIC_AUTH).permitAll()
						.requestMatchers(PUBLIC_DOCS).permitAll()
						.requestMatchers("/error").permitAll()
						// 관리자 전용. 화면에서 메뉴를 숨기는 것과 별개로 서버가 매번 역할을 확인한다
						.requestMatchers("/api/admin/**").hasRole("ADMIN")
						.anyRequest().authenticated())
				.addFilterBefore(new JwtAuthenticationFilter(tokenProvider, sessionValidator),
						UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
