package com.msg.backend.domain.analysis.application;

import com.msg.backend.domain.analysis.entity.ConceptJudgment;
import com.msg.backend.domain.analysis.entity.EvidenceKind;
import com.msg.backend.domain.analysis.entity.JudgmentEvidence;
import com.msg.backend.domain.analysis.entity.LearningBehavior;
import com.msg.backend.domain.analysis.entity.UnderstandingStatus;
import com.msg.backend.domain.conversation.entity.MessageRole;
import com.msg.backend.domain.course.entity.Course;
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
 * <li>학습 행동(behavior)은 사용자 근거에만, 근거 종류와 맞을 때만 받는다(맞지 않으면 행동만 버린다). 상태는 근거의 행동과 맞아야 한다:
 * 이해는 정확한 자기 설명·적용, 이해 부족은 이해 부족 표현·잘못된 설명, 부분 이해는 설명·적용·이해 부족 표현 중 하나가 있어야 한다.
 * 재설명 요청·심화 질문·"알겠어"만 있으면 판단 근거 부족으로 낮춘다(질문 횟수만으로 이해 부족을 확정하지 않는다).
 * 행동이 없는 근거(v1 답변 등)는 종류로 본다: 사용자 설명·풀이는 어느 상태든 받쳐 주고, 질문은 판단에 쓰지 않는다.</li>
 * <li>오개념 후보(misconception)는 잘못된 자기 설명 근거가 있을 때만 남긴다.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
class ModelOutputParser {

	static final String NO_USER_EVIDENCE_REASON = "사용자가 쓴 메시지 근거가 없어 판단 근거 부족으로 봤어요.";
	static final String QUESTION_ONLY_REASON = "재설명 요청·질문만으로는 이해 부족을 확정하지 않아요. 확인 퀴즈로 확인해 보세요.";
	static final String NO_SELF_EXPLANATION_REASON = "'알겠어' 같은 반응이나 질문만으로는 이해했다고 확정하지 않아요. 자기 설명·적용 근거가 필요해요.";
	static final String DEEPENING_ONLY_REASON = "심화 질문은 약점의 근거가 아니어서, 이것만으로는 판단하지 않아요.";
	static final int MISCONCEPTION_MAX_LENGTH = 300;

	private final ObjectMapper objectMapper;

	record ParsedEvidence(Long messageId, EvidenceKind kind, String note, LearningBehavior behavior) {

		ParsedEvidence(Long messageId, EvidenceKind kind, String note) {
			this(messageId, kind, note, null);
		}

		// 상태를 받쳐 주는 신호. 행동이 없으면 종류로 본다: 설명·풀이는 어느 쪽이든(UNSPECIFIED), 질문은 판단에 쓰지 않음
		Support support() {
			if (!kind.writtenByUser()) {
				return Support.NONE;
			}
			if (behavior == null) {
				return kind == EvidenceKind.USER_QUESTION ? Support.NONE : Support.UNSPECIFIED;
			}
			return switch (behavior.signal()) {
				case POSITIVE -> Support.POSITIVE;
				case NEGATIVE -> Support.NEGATIVE;
				case NEUTRAL -> Support.NONE;
			};
		}
	}

	enum Support {
		POSITIVE, NEGATIVE, UNSPECIFIED, NONE
	}

	record ParsedJudgment(AnalysisInput.Concept concept, UnderstandingStatus reportedStatus,
			UnderstandingStatus status, String adjustedReason, String reason, String needsCheck,
			String recommendedActivity, List<ParsedEvidence> evidence, String misconception) {
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
					// 행동은 사용자 근거에, 종류와 맞을 때만 (맞지 않으면 행동만 버리고 근거는 남긴다)
					LearningBehavior behavior = LearningBehavior.fromApiValue(item.path("behavior").asText(null))
							.filter(value -> value.fits(kind.get())).orElse(null);
					evidence.add(new ParsedEvidence(messageId, kind.get(),
							text(item.get("note"), JudgmentEvidence.NOTE_MAX_LENGTH), behavior));
				}
			}
			UnderstandingStatus status = reported.get();
			String adjusted = null;
			if (status != UnderstandingStatus.INSUFFICIENT_EVIDENCE
					&& evidence.stream().noneMatch(item -> item.kind().writtenByUser())) {
				status = UnderstandingStatus.INSUFFICIENT_EVIDENCE;
				adjusted = NO_USER_EVIDENCE_REASON;
			} else if (status != UnderstandingStatus.INSUFFICIENT_EVIDENCE) {
				adjusted = unsupportedReason(status, evidence);
				if (adjusted != null) {
					status = UnderstandingStatus.INSUFFICIENT_EVIDENCE;
				}
			}
			boolean incorrect = evidence.stream()
					.anyMatch(item -> item.behavior() == LearningBehavior.INCORRECT_EXPLANATION);
			judgments.add(new ParsedJudgment(concept, reported.get(), status, adjusted,
					text(node.get("reason"), ConceptJudgment.REASON_MAX_LENGTH),
					text(node.get("needsCheck"), ConceptJudgment.NEEDS_CHECK_MAX_LENGTH),
					text(node.get("recommendedActivity"), ConceptJudgment.ACTIVITY_MAX_LENGTH),
					evidence, incorrect ? text(node.get("misconception"), MISCONCEPTION_MAX_LENGTH) : null));
		}
		if (judgments.isEmpty()) {
			throw new InvalidOutputException("목록에 있는 개념에 대한 판단이 없다");
		}
		return new ParsedOutput(judgments, dropped);
	}

	/**
	 * 상태를 받쳐 주는 사용자 근거가 없으면 그 이유 (있으면 null). 이해 ← 긍정, 이해 부족 ← 부정, 부분 이해 ← 긍정·부정 중 하나.
	 * 행동을 밝히지 않은 사용자 설명·풀이는 어느 상태든 받쳐 준다(v1 호환).
	 */
	static String unsupportedReason(UnderstandingStatus status, List<ParsedEvidence> evidence) {
		Set<Support> supports = new HashSet<>();
		evidence.forEach(item -> supports.add(item.support()));
		boolean unspecified = supports.contains(Support.UNSPECIFIED);
		boolean supported = switch (status) {
			case UNDERSTOOD -> unspecified || supports.contains(Support.POSITIVE);
			case NOT_UNDERSTOOD -> unspecified || supports.contains(Support.NEGATIVE);
			case PARTIAL -> unspecified || supports.contains(Support.POSITIVE) || supports.contains(Support.NEGATIVE);
			case INSUFFICIENT_EVIDENCE -> true;
		};
		if (supported) {
			return null;
		}
		boolean deepeningOnly = evidence.stream().filter(item -> item.kind().writtenByUser())
				.allMatch(item -> item.behavior() == LearningBehavior.DEEPENING_QUESTION);
		if (status == UnderstandingStatus.UNDERSTOOD) {
			return NO_SELF_EXPLANATION_REASON;
		}
		return deepeningOnly ? DEEPENING_ONLY_REASON : QUESTION_ONLY_REASON;
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
