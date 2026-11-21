package com.ailms.backend.global.security;

import com.ailms.backend.global.common.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * 보안 필터 단계(컨트롤러에 닿기 전)에서 거절된 요청도 { success: false, message, data: null } 형식으로 돌려준다.
 * 다른 필터(ConversationImportSizeFilter)도 같은 형식으로 거절할 때 {@link #write}를 쓴다.
 */
@Component
@RequiredArgsConstructor
public class ApiErrorResponder implements AuthenticationEntryPoint, AccessDeniedHandler {

	public static final String UNAUTHORIZED_MESSAGE = "로그인이 필요해요. 다시 로그인해 주세요.";
	public static final String FORBIDDEN_MESSAGE = "이 기능을 사용할 권한이 없어요.";

	private final ObjectMapper objectMapper;

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException e) throws IOException {
		write(response, HttpStatus.UNAUTHORIZED, UNAUTHORIZED_MESSAGE);
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException e) throws IOException {
		write(response, HttpStatus.FORBIDDEN, FORBIDDEN_MESSAGE);
	}

	public void write(HttpServletResponse response, HttpStatus status, String message) throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		objectMapper.writeValue(response.getWriter(), ApiResponse.error(message));
	}
}
