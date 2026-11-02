package com.ailms.backend.domain.quiz.dto;

import com.ailms.backend.domain.quiz.application.QuizGrader.Graded;
import com.ailms.backend.domain.quiz.entity.QuizSubmission;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

// 채점 결과 (frontend/src/types/quiz.ts의 QuizResult). 정오는 서버 코드가 정하고, aiExplanation만 AI가 쓴 글이다
public record QuizResultResponse(
		Long submissionId,
		LocalDateTime submittedAt,
		int correctCount,
		int totalCount,
		List<Result> results) {

	public record Result(
			Long questionId,
			@Schema(description = "고른 보기 순서(0부터). 고르지 않았으면 -1") int choiceIndex,
			boolean correct,
			int correctChoiceIndex,
			@Schema(description = "이 문제가 다루는 개념. conceptId는 학습 분석의 개념 키와 같은 값") List<Concept> concepts,
			@Schema(description = "AI가 쓴 해설. 없으면 null") String aiExplanation) {
	}

	public record Concept(String conceptId, String name) {
	}

	public static QuizResultResponse of(QuizSubmission submission, List<Graded> graded) {
		return new QuizResultResponse(submission.getId(), submission.getSubmittedAt(), submission.getCorrectCount(),
				submission.getTotalCount(), graded.stream()
						.map(g -> new Result(g.question().getId(), g.choiceIndex(), g.correct(),
								g.question().getCorrectChoiceIndex(),
								g.question().getConcepts().stream()
										.map(c -> new Concept(c.getConceptKey(), c.getConceptName()))
										.toList(),
								g.question().getAiExplanation()))
						.toList());
	}
}
