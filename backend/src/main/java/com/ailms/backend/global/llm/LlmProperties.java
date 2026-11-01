package com.ailms.backend.global.llm;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 모델 설정 (app.llm). API 키는 환경변수(OPENAI_API_KEY, ANTHROPIC_API_KEY, GEMINI_API_KEY)로만 받는다.
 * 모델 이름도 환경변수로 받고 기본값을 두지 않는다: 어떤 모델을 쓸지는 비용·품질을 보고 팀이 정한다.
 *
 * @param timeout      모델 한 번 호출을 기다리는 시간
 * @param maxRetries   시간 초과·한도 초과·서버 오류일 때 다시 부르는 횟수
 * @param retryBackoff 다시 부르기 전 기다리는 시간 (n번째 재시도는 n배)
 */
@ConfigurationProperties(prefix = "app.llm")
public record LlmProperties(Duration timeout, int maxRetries, Duration retryBackoff, Provider openai,
		Provider anthropic, Provider gemini) {

	/**
	 * @param apiKey  비어 있으면 미설정
	 * @param model   비어 있으면 미설정
	 * @param baseUrl 제공사 API 주소 (테스트에서 바꿔 끼운다)
	 */
	public record Provider(String apiKey, String model, String baseUrl) {

		public boolean isConfigured() {
			return apiKey != null && !apiKey.isBlank() && model != null && !model.isBlank();
		}
	}
}
