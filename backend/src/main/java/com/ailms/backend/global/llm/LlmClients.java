package com.ailms.backend.global.llm;

import java.util.Comparator;
import java.util.List;
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
}
