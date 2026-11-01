package com.ailms.backend.domain.conversation.api;

import com.ailms.backend.global.common.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 대화 가져오기 요청의 크기를 본문을 읽기 전에 확인한다. 스프링은 JSON 본문 크기를 막지 않아서
 * (multipart 한도는 파일 올리기에만 걸린다) Content-Length로 직접 막는다.
 * 크기를 밝히지 않은 요청(chunked)은 받지 않는다. 브라우저의 axios 요청은 항상 Content-Length가 있다.
 */
@Component
@RequiredArgsConstructor
public class ConversationImportSizeFilter extends OncePerRequestFilter {

	static final String TOO_LARGE_MESSAGE = "한 번에 보낼 수 있는 크기(2MB)를 넘었어요. 대화를 나눠서 보내 주세요.";
	static final String LENGTH_REQUIRED_MESSAGE = "요청 크기를 알 수 없어요. 다시 시도해 주세요.";

	private final ConversationImportProperties properties;
	private final ObjectMapper objectMapper;

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !(HttpMethod.POST.matches(request.getMethod()) && "/api/conversations".equals(request.getRequestURI()));
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		long length = request.getContentLengthLong();
		if (length < 0) {
			write(response, HttpStatus.LENGTH_REQUIRED, LENGTH_REQUIRED_MESSAGE);
			return;
		}
		if (length > properties.maxRequestSize().toBytes()) {
			write(response, HttpStatus.PAYLOAD_TOO_LARGE, TOO_LARGE_MESSAGE);
			return;
		}
		chain.doFilter(request, response);
	}

	private void write(HttpServletResponse response, HttpStatus status, String message) throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		objectMapper.writeValue(response.getWriter(), ApiResponse.error(message));
	}
}
