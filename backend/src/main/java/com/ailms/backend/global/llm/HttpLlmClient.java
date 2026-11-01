package com.ailms.backend.global.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * HTTP로 부르는 제공사 공통 부분: 시간 제한, 오류를 LlmException으로 바꾸기.
 * 요청·응답 본문(사용자 대화)은 예외 메시지와 로그에 넣지 않는다.
 */
abstract class HttpLlmClient implements LlmClient {

	private final LlmProvider provider;
	private final LlmProperties.Provider config;
	private final RestClient restClient;
	protected final ObjectMapper objectMapper;

	HttpLlmClient(LlmProvider provider, LlmProperties.Provider config, String defaultBaseUrl, Duration timeout,
			ObjectMapper objectMapper) {
		this.provider = provider;
		this.config = config;
		this.objectMapper = objectMapper;
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(timeout);
		String baseUrl = config.baseUrl() == null || config.baseUrl().isBlank() ? defaultBaseUrl : config.baseUrl();
		this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
	}

	@Override
	public LlmProvider provider() {
		return provider;
	}

	@Override
	public String model() {
		return config.model() == null || config.model().isBlank() ? null : config.model();
	}

	@Override
	public boolean isConfigured() {
		return config.isConfigured();
	}

	@Override
	public LlmResponse complete(LlmRequest request) {
		if (!isConfigured()) {
			throw new IllegalStateException(provider + " 모델이 설정되지 않았다");
		}
		return send(request);
	}

	protected abstract LlmResponse send(LlmRequest request);

	protected String apiKey() {
		return config.apiKey();
	}

	protected JsonNode post(String uri, Map<String, String> headers, Object body) {
		try {
			String response = restClient.post()
					.uri(uri)
					.headers(h -> headers.forEach(h::set))
					.contentType(MediaType.APPLICATION_JSON)
					.body(body)
					.retrieve()
					.body(String.class);
			return objectMapper.readTree(response == null ? "" : response);
		} catch (RestClientResponseException e) {
			throw new LlmException(codeOf(e.getStatusCode().value()),
					provider + " 응답 상태 " + e.getStatusCode().value());
		} catch (ResourceAccessException e) {
			boolean timeout = e.getCause() instanceof HttpTimeoutException
					|| e.getCause() instanceof SocketTimeoutException;
			throw new LlmException(timeout ? LlmException.Code.TIMEOUT : LlmException.Code.NETWORK,
					provider + (timeout ? " 응답 시간 초과" : " 연결 실패"), e);
		} catch (com.fasterxml.jackson.core.JsonProcessingException e) {
			throw new LlmException(LlmException.Code.BAD_RESPONSE, provider + " 응답이 JSON이 아니다");
		}
	}

	// 응답 JSON에서 꼭 있어야 하는 글자 칸을 꺼낸다
	protected String requireText(JsonNode node, String what) {
		if (node == null || !node.isTextual()) {
			throw new LlmException(LlmException.Code.BAD_RESPONSE, provider + " 응답에 " + what + "이 없다");
		}
		return node.asText();
	}

	protected static Integer intOrNull(JsonNode node) {
		return node != null && node.canConvertToInt() ? node.asInt() : null;
	}

	private static LlmException.Code codeOf(int status) {
		if (status == 429) {
			return LlmException.Code.RATE_LIMITED;
		}
		if (status == 401 || status == 403) {
			return LlmException.Code.AUTH_FAILED;
		}
		if (status >= 500) {
			return LlmException.Code.SERVER_ERROR;
		}
		return LlmException.Code.REJECTED;
	}
}
