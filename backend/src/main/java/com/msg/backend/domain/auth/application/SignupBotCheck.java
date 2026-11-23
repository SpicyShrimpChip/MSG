package com.msg.backend.domain.auth.application;

import com.msg.backend.global.error.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 회원가입 요청이 사람이 보낸 것인지 Cloudflare Turnstile로 확인한다. 깡통 계정을 스크립트로 대량으로 만드는 것을 막는다.
 * 비밀 키가 없으면(로컬·테스트) 확인하지 않는다. 확인 서버에 닿지 못하면 가입을 막는다(확인 없이 통과시키지 않는다).
 * 토큰은 한 번만 쓸 수 있으니 가입이 다른 이유로 실패하면 프론트가 새 토큰을 받는다.
 */
@Slf4j
@Component
public class SignupBotCheck {

	static final String MISSING_MESSAGE = "자동 가입 방지 확인을 마친 뒤 다시 가입해 주세요.";
	static final String FAILED_MESSAGE = "자동 가입 방지 확인에 실패했어요. 확인을 다시 한 뒤 가입해 주세요.";
	static final String UNAVAILABLE_MESSAGE = "자동 가입 방지 확인을 지금 할 수 없어요. 잠시 후 다시 시도해 주세요.";

	private final TurnstileProperties properties;
	private final RestClient restClient;

	public SignupBotCheck(TurnstileProperties properties) {
		this.properties = properties;
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.timeout());
		this.restClient = RestClient.builder().requestFactory(requestFactory).build();
	}

	public void verify(String token) {
		if (!properties.enabled()) {
			return;
		}
		if (token == null || token.isBlank()) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, MISSING_MESSAGE);
		}
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("secret", properties.secretKey());
		form.add("response", token);
		JsonNode result;
		try {
			result = restClient.post().uri(properties.verifyUrl())
					.contentType(MediaType.APPLICATION_FORM_URLENCODED)
					.body(form)
					.retrieve()
					.body(JsonNode.class);
		} catch (RestClientException e) {
			log.warn("자동 가입 방지 확인 서버에 닿지 못했어요 ({})", e.getClass().getSimpleName());
			throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE_MESSAGE);
		}
		if (result == null || !result.path("success").asBoolean(false)) {
			// 오류 코드만 남긴다 (토큰·비밀 키는 남기지 않는다)
			log.info("자동 가입 방지 확인 실패 {}", result == null ? "(빈 응답)" : result.path("error-codes"));
			throw new BusinessException(HttpStatus.BAD_REQUEST, FAILED_MESSAGE);
		}
	}
}
