package com.ailms.backend.domain.quiz.application;

import com.ailms.backend.domain.quiz.dto.QuizAnswerRequest;
import com.ailms.backend.domain.quiz.entity.QuizQuestion;
import com.ailms.backend.global.error.BusinessException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * 객관식 채점. 정오는 저장된 정답 번호와 고른 번호가 같은지로만 정한다(AI는 관여하지 않는다).
 * 답하지 않은 문제는 -1(틀림)로 채운다. 이 퀴즈에 없는 문제, 같은 문제를 두 번 보낸 답, 범위 밖 보기 번호는 거절한다.
 */
public final class QuizGrader {

	public static final int NO_ANSWER = -1;
	static final String INVALID_ANSWER_MESSAGE = "제출한 답을 확인할 수 없어요. 퀴즈를 다시 열어 주세요.";

	private QuizGrader() {
	}

	public record Graded(QuizQuestion question, int choiceIndex, boolean correct) {
	}

	// 문제 순서대로 돌려준다
	public static List<Graded> grade(List<QuizQuestion> questions, List<QuizAnswerRequest> answers) {
		Map<Long, QuizQuestion> byId = new HashMap<>();
		for (QuizQuestion question : questions) {
			byId.put(question.getId(), question);
		}
		Map<Long, Integer> chosen = new HashMap<>();
		for (QuizAnswerRequest answer : answers) {
			QuizQuestion question = byId.get(answer.questionId());
			int choiceIndex = answer.choiceIndex();
			boolean inRange = choiceIndex == NO_ANSWER
					|| (question != null && choiceIndex >= 0 && choiceIndex < question.getChoices().size());
			if (question == null || !inRange || chosen.putIfAbsent(answer.questionId(), choiceIndex) != null) {
				throw new BusinessException(HttpStatus.BAD_REQUEST, INVALID_ANSWER_MESSAGE);
			}
		}
		List<Graded> graded = new ArrayList<>();
		for (QuizQuestion question : questions) {
			int choiceIndex = chosen.getOrDefault(question.getId(), NO_ANSWER);
			graded.add(new Graded(question, choiceIndex, choiceIndex == question.getCorrectChoiceIndex()));
		}
		return graded;
	}
}
