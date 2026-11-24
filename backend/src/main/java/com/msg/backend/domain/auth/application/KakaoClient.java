package com.msg.backend.domain.auth.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.msg.backend.domain.auth.entity.SocialProvider;
import com.msg.backend.global.common.RestClients;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 카카오 REST API. 인가 코드로 토큰을 받아 사용자 정보를 읽고(토큰은 쓰고 버린다, 저장하지 않음), 탈퇴 때 연결을 끊는다.
 * 실패·시간 초과는 SocialLoginException(FAILED). 코드·토큰·키는 로그에 남기지 않는다.
 */
@Slf4j
@Component
public class KakaoClient {

	private final KakaoProperties properties;
	private final RestClient restClient;

	public KakaoClient(KakaoProperties properties) {
		this.properties = properties;
		this.restClient = RestClients.withTimeouts(Duration.ofSeconds(5), properties.timeout()).build();
	}

	public SocialProfile fetchProfile(String code) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "authorization_code");
		form.add("client_id", properties.clientId());
		form.add("client_secret", properties.clientSecret());
		form.add("redirect_uri", properties.redirectUri());
		form.add("code", code);
		try {
			JsonNode token = restClient.post().uri(properties.authBaseUrl() + "/oauth/token")
					.contentType(MediaType.APPLICATION_FORM_URLENCODED)
					.body(form)
					.retrieve()
					.body(JsonNode.class);
			String accessToken = token == null ? "" : token.path("access_token").asText("");
			if (accessToken.isEmpty()) {
				throw failed("토큰 응답에 access_token이 없어요", null);
			}
			JsonNode me = restClient.get().uri(properties.apiBaseUrl() + "/v2/user/me")
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
					.retrieve()
					.body(JsonNode.class);
			return toProfile(me);
		} catch (RestClientException e) {
			throw failed("카카오 로그인 요청 실패", e);
		}
	}

	// 탈퇴한 사용자의 카카오 연결을 끊는다. 어드민 키가 없으면 건너뛴다
	public void unlink(String providerUserId) {
		if (!properties.hasAdminKey()) {
			log.warn("KAKAO_ADMIN_KEY가 없어 카카오 연결 끊기를 건너뛰었어요");
			return;
		}
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("target_id_type", "user_id");
		form.add("target_id", providerUserId);
		try {
			restClient.post().uri(properties.apiBaseUrl() + "/v1/user/unlink")
					.header(HttpHeaders.AUTHORIZATION, "KakaoAK " + properties.adminKey())
					.contentType(MediaType.APPLICATION_FORM_URLENCODED)
					.body(form)
					.retrieve()
					.toBodilessEntity();
		} catch (RestClientException e) {
			throw failed("카카오 연결 끊기 실패", e);
		}
	}

	private static SocialProfile toProfile(JsonNode me) {
		if (me == null || !me.path("id").canConvertToLong()) {
			throw failed("사용자 정보에 회원번호가 없어요", null);
		}
		JsonNode account = me.path("kakao_account");
		String email = account.path("email").isTextual() ? account.path("email").asText() : null;
		boolean verified = email != null && account.path("is_email_valid").asBoolean(false)
				&& account.path("is_email_verified").asBoolean(false);
		JsonNode nicknameNode = account.path("profile").path("nickname");
		String nickname = nicknameNode.isTextual() && !nicknameNode.asText().isBlank() ? nicknameNode.asText() : null;
		return new SocialProfile(SocialProvider.KAKAO, String.valueOf(me.path("id").asLong()), email, verified,
				nickname);
	}

	private static SocialLoginException failed(String reason, Exception cause) {
		// 응답 본문·토큰은 남기지 않고 예외 종류만 남긴다
		log.warn("{} ({})", reason, cause == null ? "-" : cause.getClass().getSimpleName());
		return new SocialLoginException(SocialError.FAILED);
	}
}
