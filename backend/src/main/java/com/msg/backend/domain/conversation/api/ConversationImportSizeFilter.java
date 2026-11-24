package com.msg.backend.domain.conversation.api;

import com.msg.backend.global.security.ApiErrorResponder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 대화 가져오기 요청의 크기를 본문을 읽기 전에 확인한다. 스프링은 JSON 본문 크기를 막지 않아서
 * (multipart 한도는 파일 올리기에만 걸린다) Content-Length로 직접 막는다.
 * 크기를 밝히지 않은 요청(chunked)은 받지 않는다. 브라우저의 axios 요청은 항상 Content-Length가 있다.
 */
@Component
@RequiredArgsConstructor
public class ConversationImportSizeFilter extends OncePerRequestFilter {

	static final String LENGTH_REQUIRED_MESSAGE = "요청 크기를 알 수 없어요. 다시 시도해 주세요.";

	private final ConversationImportProperties properties;
	private final ApiErrorResponder errorResponder;

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !(HttpMethod.POST.matches(request.getMethod()) && "/api/conversations".equals(request.getRequestURI()));
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		long length = request.getContentLengthLong();
		if (length < 0) {
			errorResponder.write(response, HttpStatus.LENGTH_REQUIRED, LENGTH_REQUIRED_MESSAGE);
			return;
		}
		if (length > properties.maxRequestSize().toBytes()) {
			errorResponder.write(response, HttpStatus.PAYLOAD_TOO_LARGE, tooLargeMessage(properties.maxRequestSize()));
			return;
		}
		chain.doFilter(request, response);
	}

	// 설정값(app.conversation-import.max-request-size)으로 문구를 만든다 (예: 2MB)
	static String tooLargeMessage(DataSize limit) {
		return "한 번에 보낼 수 있는 크기(" + label(limit) + ")를 넘었어요. 대화를 나눠서 보내 주세요.";
	}

	private static String label(DataSize size) {
		long bytes = size.toBytes();
		if (bytes > 0 && bytes % DataSize.ofMegabytes(1).toBytes() == 0) {
			return size.toMegabytes() + "MB";
		}
		if (bytes > 0 && bytes % DataSize.ofKilobytes(1).toBytes() == 0) {
			return size.toKilobytes() + "KB";
		}
		return bytes + "바이트";
	}
}
