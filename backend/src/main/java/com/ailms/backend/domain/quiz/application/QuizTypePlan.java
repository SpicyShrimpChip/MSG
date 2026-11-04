package com.ailms.backend.domain.quiz.application;

import com.ailms.backend.domain.quiz.entity.QuestionType;
import com.ailms.backend.domain.quiz.entity.QuizMode;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 고른 문제 유형마다 몇 문제를 만들지 코드로 정한다. 고른 유형은 모두 한 문제 이상 들어가고, 남는 문제는 모드의 기본 유형부터 하나씩 더한다.
 * 업무 활용은 상황 중심 서술형이 기본이라 서술형부터, 나머지 모드는 객관식부터 더한다.
 * 모델이 이 수와 다르게 만들면 형식 오류로 보고 저장하지 않는다.
 */
public final class QuizTypePlan {

	private static final List<QuestionType> DEFAULT_PRIORITY = List.of(QuestionType.MULTIPLE_CHOICE,
			QuestionType.SHORT_ANSWER, QuestionType.ESSAY);
	private static final List<QuestionType> WORK_PRIORITY = List.of(QuestionType.ESSAY, QuestionType.SHORT_ANSWER,
			QuestionType.MULTIPLE_CHOICE);

	private QuizTypePlan() {
	}

	// 유형 → 문제 수 (유형 순서는 모드의 기본 순서). count가 유형 수보다 적으면 IllegalArgumentException
	public static Map<QuestionType, Integer> plan(QuizMode mode, Collection<QuestionType> types, int count) {
		List<QuestionType> ordered = (mode == QuizMode.WORK ? WORK_PRIORITY : DEFAULT_PRIORITY).stream()
				.filter(types::contains)
				.toList();
		if (ordered.isEmpty() || count < ordered.size()) {
			throw new IllegalArgumentException("문제 수는 고른 유형 수 이상이어야 한다");
		}
		Map<QuestionType, Integer> plan = new LinkedHashMap<>();
		int base = count / ordered.size();
		int remainder = count % ordered.size();
		for (int i = 0; i < ordered.size(); i++) {
			plan.put(ordered.get(i), base + (i < remainder ? 1 : 0));
		}
		return plan;
	}
}
