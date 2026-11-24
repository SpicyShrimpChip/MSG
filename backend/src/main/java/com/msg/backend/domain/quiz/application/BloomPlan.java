package com.msg.backend.domain.quiz.application;

import com.msg.backend.domain.quiz.entity.BloomLevel;
import com.msg.backend.domain.quiz.entity.QuestionType;
import com.msg.backend.domain.quiz.entity.QuizMode;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 퀴즈에서 평가할 인지 수준(개정 블룸)을 코드로 정한다. 학습 목적마다 평가하려는 능력이 다르므로 기본 수준이 다르다.
 * <ul>
 * <li>시험 대비: 기억·이해·적용·분석 (정의·원리·차이를 확인하고 문제 상황에 적용)</li>
 * <li>자기개발: 이해·적용·분석·평가 (자기 말로 설명 → 새 상황 적용 → 비교·판단 순으로 단계적으로)</li>
 * <li>업무 활용: 적용·분석·평가·창조 (실무 상황을 분석하고 대안을 비교·선택하고 구성을 설계)</li>
 * </ul>
 * 사용자가 수준을 고르면 그 수준을 쓴다. 창조는 서술형으로만 평가하므로 서술형이 없으면 뺀다.
 * 모델이 만든 문제는 모두 이 목록 안의 수준이어야 하고, 문제 수가 허락하는 만큼 서로 다른 수준을 다뤄야 한다(QuizGenerationParser).
 */
public final class BloomPlan {

	static final String CREATE_NEEDS_ESSAY_MESSAGE = "창조 수준은 서술형 문제로만 평가할 수 있어요. 서술형을 함께 고르거나 다른 수준을 골라 주세요.";

	static final String CREATE_ONLY_WITH_OTHER_TYPES_MESSAGE = "창조 수준은 서술형으로만 낼 수 있어요. 객관식·주관식에 쓸 수준을 하나 더 골라 주세요.";

	private BloomPlan() {
	}

	public static List<BloomLevel> defaults(QuizMode mode) {
		return switch (mode) {
			case EXAM_PREP -> List.of(BloomLevel.REMEMBER, BloomLevel.UNDERSTAND, BloomLevel.APPLY, BloomLevel.ANALYZE);
			case SELF_DEVELOPMENT -> List.of(BloomLevel.UNDERSTAND, BloomLevel.APPLY, BloomLevel.ANALYZE,
					BloomLevel.EVALUATE);
			case WORK -> List.of(BloomLevel.APPLY, BloomLevel.ANALYZE, BloomLevel.EVALUATE, BloomLevel.CREATE);
		};
	}

	/**
	 * 평가할 수준 (낮은 수준부터). requested가 비었으면 모드 기본값. 서술형이 없으면 창조를 뺀다.
	 * 남는 수준이 없으면(창조만 골랐는데 서술형이 없음) IllegalArgumentException(CREATE_NEEDS_ESSAY_MESSAGE).
	 * 서술형이 아닌 유형이 있는데 창조밖에 없으면 그 문제에 쓸 수준이 없어 어떤 답도 검증을 통과하지 못하므로
	 * 모델을 부르기 전에 IllegalArgumentException(CREATE_ONLY_WITH_OTHER_TYPES_MESSAGE).
	 */
	public static List<BloomLevel> targets(QuizMode mode, Collection<BloomLevel> requested, Collection<QuestionType> types) {
		Set<BloomLevel> chosen = requested == null || requested.isEmpty() ? EnumSet.copyOf(defaults(mode))
				: EnumSet.copyOf(requested);
		if (!types.contains(QuestionType.ESSAY)) {
			chosen.remove(BloomLevel.CREATE);
		}
		if (chosen.isEmpty()) {
			throw new IllegalArgumentException(CREATE_NEEDS_ESSAY_MESSAGE);
		}
		boolean hasNonEssay = types.stream().anyMatch(type -> type != QuestionType.ESSAY);
		if (hasNonEssay && chosen.equals(EnumSet.of(BloomLevel.CREATE))) {
			throw new IllegalArgumentException(CREATE_ONLY_WITH_OTHER_TYPES_MESSAGE);
		}
		return List.copyOf(chosen);
	}

	/** 문제 수가 허락하는 만큼 다뤄야 하는 서로 다른 수준 수 */
	public static int requiredDistinct(List<BloomLevel> targets, int questionCount) {
		return Math.min(targets.size(), questionCount);
	}

	/** 저장용 "REMEMBER,UNDERSTAND" */
	public static String join(List<BloomLevel> levels) {
		return levels == null || levels.isEmpty() ? null
				: levels.stream().map(BloomLevel::name).collect(Collectors.joining(","));
	}

	public static List<BloomLevel> split(String joined) {
		if (joined == null || joined.isBlank()) {
			return List.of();
		}
		return Arrays.stream(joined.split(",")).map(String::strip).filter(s -> !s.isEmpty()).map(s -> {
			try {
				return BloomLevel.valueOf(s);
			} catch (IllegalArgumentException e) {
				return null;
			}
		}).filter(level -> level != null).distinct().toList();
	}
}
