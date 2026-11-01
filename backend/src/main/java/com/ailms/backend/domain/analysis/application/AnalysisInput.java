package com.ailms.backend.domain.analysis.application;

import com.ailms.backend.domain.conversation.entity.MessageRole;
import java.util.List;
import java.util.Map;

/**
 * 모델로 보낼 입력. 같은 입력을 모든 모델에 보낸다.
 *
 * @param concepts        평가할 개념 (과목 키워드)
 * @param conversationIds 보낸 대화
 * @param sentMessages    보낸 메시지 id와 말한 사람. 모델이 든 근거는 이 안에 있어야 한다
 * @param userPrompt      모델에 보내는 글
 */
record AnalysisInput(List<Concept> concepts, List<Long> conversationIds, Map<Long, MessageRole> sentMessages,
		int truncatedMessageCount, String userPrompt) {

	/** @param key 비교용 이름 (소문자, Course.nameKey) */
	record Concept(String key, String name) {
	}
}
