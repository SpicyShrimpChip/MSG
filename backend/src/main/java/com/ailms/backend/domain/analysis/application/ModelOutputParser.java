package com.ailms.backend.domain.analysis.application;

import com.ailms.backend.domain.analysis.entity.ConceptJudgment;
import com.ailms.backend.domain.analysis.entity.EvidenceKind;
import com.ailms.backend.domain.analysis.entity.JudgmentEvidence;
import com.ailms.backend.domain.analysis.entity.UnderstandingStatus;
import com.ailms.backend.domain.conversation.entity.MessageRole;
import com.ailms.backend.domain.course.entity.Course;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 모델 답변(JSON)을 검증해 개념별 판단으로 바꾼다. 모든 모델에 같은 규칙을 쓴다.
 * <ul>
 * <li>개념은 보낸 목록(과목 키워드)에 있는 것만 받는다. 같은 개념이 두 번 오면 처음 것만 쓴다.</li>
 * <li>근거 메시지는 이번에 보낸 메시지여야 하고, user_ 근거는 사용자 메시지, ai_explanation은 AI 메시지여야 한다.
 * 맞지 않는 근거는 버리고 개수를 센다.</li>
 * <li>이해·부분 이해·이해 부족이라고 했는데 사용자가 쓴 메시지 근거가 하나도 없으면 판단 근거 부족으로 본다
 * (모델이 보고한 상태는 reportedStatus에 그대로 남긴다). 근거 부족을 이해 부족으로 바꾸지는 않는다.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
class ModelOutputParser {

	static final String NO_USER_EVIDENCE_REASON = "사용자가 쓴 메시지 근거가 없어 판단 근거 부족으로 봤어요.";

	private final ObjectMapper objectMapper;

	record ParsedEvidence(Long messageId, EvidenceKind kind, String note) {
	}

	record ParsedJudgment(AnalysisInput.Concept concept, UnderstandingStatus reportedStatus,
			UnderstandingStatus status, String adjustedReason, String reason, String needsCheck,
			String recommendedActivity, List<ParsedEvidence> evidence) {
	}

	record ParsedOutput(List<ParsedJudgment> judgments, int droppedEvidenceCount) {
	}

	static class InvalidOutputException extends Exception {

		InvalidOutputException(String message) {
			super(message);
		}
	}

	ParsedOutput parse(String text, AnalysisInput input) throws InvalidOutputException {
		JsonNode root = readJson(text);
		JsonNode concepts = root.get("concepts");
		if (concepts == null || !concepts.isArray()) {
			throw new InvalidOutputException("concepts 목록이 없다");
		}
		Map<String, AnalysisInput.Concept> known = new LinkedHashMap<>();
		input.concepts().forEach(concept -> known.put(concept.key(), concept));

		List<ParsedJudgment> judgments = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		int dropped = 0;
		for (JsonNode node : concepts) {
			String name = node.path("concept").asText("").strip().replaceAll("\\s+", " ");
			AnalysisInput.Concept concept = known.get(Course.nameKey(name));
			var reported = UnderstandingStatus.fromApiValue(node.path("status").asText(null));
			if (concept == null || reported.isEmpty() || !seen.add(concept.key())) {
				continue;
			}
			List<ParsedEvidence> evidence = new ArrayList<>();
			Set<String> evidenceKeys = new HashSet<>();
			for (JsonNode item : node.path("evidence")) {
				Long messageId = messageId(item.get("messageId"));
				var kind = EvidenceKind.fromApiValue(item.path("kind").asText(null));
				MessageRole role = messageId == null ? null : input.sentMessages().get(messageId);
				if (role == null || kind.isEmpty() || kind.get().writtenByUser() != (role == MessageRole.USER)) {
					dropped++;
					continue;
				}
				if (evidenceKeys.add(messageId + ":" + kind.get())) {
					evidence.add(new ParsedEvidence(messageId, kind.get(),
							text(item.get("note"), JudgmentEvidence.NOTE_MAX_LENGTH)));
				}
			}
			UnderstandingStatus status = reported.get();
			String adjusted = null;
			if (status != UnderstandingStatus.INSUFFICIENT_EVIDENCE
					&& evidence.stream().noneMatch(item -> item.kind().writtenByUser())) {
				status = UnderstandingStatus.INSUFFICIENT_EVIDENCE;
				adjusted = NO_USER_EVIDENCE_REASON;
			}
			judgments.add(new ParsedJudgment(concept, reported.get(), status, adjusted,
					text(node.get("reason"), ConceptJudgment.REASON_MAX_LENGTH),
					text(node.get("needsCheck"), ConceptJudgment.NEEDS_CHECK_MAX_LENGTH),
					text(node.get("recommendedActivity"), ConceptJudgment.ACTIVITY_MAX_LENGTH),
					evidence));
		}
		if (judgments.isEmpty()) {
			throw new InvalidOutputException("목록에 있는 개념에 대한 판단이 없다");
		}
		return new ParsedOutput(judgments, dropped);
	}

	// 코드 블록 표시(```json)나 앞뒤 글이 붙어 와도 첫 { 부터 마지막 } 까지를 읽는다
	private JsonNode readJson(String text) throws InvalidOutputException {
		if (text == null) {
			throw new InvalidOutputException("답변이 비어 있다");
		}
		int start = text.indexOf('{');
		int end = text.lastIndexOf('}');
		if (start < 0 || end <= start) {
			throw new InvalidOutputException("JSON 객체가 없다");
		}
		try {
			JsonNode root = objectMapper.readTree(text.substring(start, end + 1));
			if (!root.isObject()) {
				throw new InvalidOutputException("JSON 객체가 아니다");
			}
			return root;
		} catch (JsonProcessingException e) {
			throw new InvalidOutputException("JSON으로 읽을 수 없다");
		}
	}

	// 123, "123", "m123" 모두 받는다
	private static Long messageId(JsonNode node) {
		if (node == null) {
			return null;
		}
		if (node.canConvertToLong()) {
			return node.asLong();
		}
		String text = node.asText("").strip();
		if (text.startsWith("m") || text.startsWith("M")) {
			text = text.substring(1);
		}
		try {
			return Long.parseLong(text);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static String text(JsonNode node, int maxLength) {
		if (node == null || !node.isTextual()) {
			return null;
		}
		String text = node.asText().strip();
		if (text.isEmpty()) {
			return null;
		}
		return text.length() <= maxLength ? text : text.substring(0, maxLength);
	}
}
