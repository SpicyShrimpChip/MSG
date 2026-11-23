package com.msg.backend.global.llm;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 학습 분석에 쓰는 제공사 목록 (GPT, Claude, Gemini 순서). 테스트는 이 빈을 가짜 모델 목록으로 바꿔 끼운다
 * (가짜 모델은 테스트 코드에만 있고 서비스에는 들어가지 않는다).
 */
@Component
public class LlmClients {

	private final List<LlmClient> clients;

	public LlmClients(List<LlmClient> clients) {
		this.clients = clients.stream().sorted(Comparator.comparing(LlmClient::provider)).toList();
	}

	public List<LlmClient> all() {
		return clients;
	}

	/**
	 * 키와 모델 이름이 설정된 모델만 주어진 순서대로 돌려준다. 순서가 없으면(null) GPT, Claude, Gemini 순서다. 같은 제공사는 한 번만
	 * 넣는다.
	 */
	public List<LlmClient> configuredInOrder(List<LlmProvider> order) {
		List<LlmProvider> providers = order == null ? List.of(LlmProvider.values()) : order;
		Map<LlmProvider, LlmClient> byProvider = clients.stream().filter(LlmClient::isConfigured)
				.collect(Collectors.toMap(LlmClient::provider, Function.identity()));
		return providers.stream().map(byProvider::get).filter(Objects::nonNull).distinct().toList();
	}
}
