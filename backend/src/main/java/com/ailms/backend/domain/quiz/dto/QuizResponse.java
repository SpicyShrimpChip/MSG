package com.ailms.backend.domain.quiz.dto;

import com.ailms.backend.domain.quiz.entity.QuestionType;
import com.ailms.backend.domain.quiz.entity.Quiz;
import com.ailms.backend.domain.quiz.entity.QuizMode;
import com.ailms.backend.domain.quiz.entity.QuizQuestion;
import com.ailms.backend.domain.quiz.entity.QuizRubricItem;
import com.ailms.backend.global.common.ApiEnumValues;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 풀기 화면용 퀴즈 (frontend/src/types/quiz.ts의 Quiz). 정답 번호·허용 답·모범 답안·해설·배점별 기준 점수는 싣지 않는다.
 * 필드 자체가 없어서 실수로 내보낼 수 없다. 정답은 제출 뒤 QuizResultResponse로만 나간다.
 * 업무 활용 문제는 무엇을 보고 평가하는지(criteria, 기준 이름만)를 미리 보여 준다. 개념은 결과에서만 보여 준다(V6 계약).
 */
public record QuizResponse(
		Long id,
		String title,
		Long courseId,
		Long materialId,
		@Schema(description = "exam_prep / self_development / work. V6 퀴즈는 null") String mode,
		@Schema(description = "easy / medium / hard. 없으면 null") String difficulty,
		String jobField,
		String jobFieldOther,
		String examScope,
		@Schema(description = "문제를 만든 AI. 사람이 넣은 퀴즈는 null") GeneratedBy generatedBy,
		@Schema(description = "학습자료 본문(발췌)을 AI에 보내 만들었는지") boolean materialContentUsed,
		@Schema(description = "모의시험인지") boolean mockExam,
		@Schema(description = "사전·사후평가: pre / post. 일반 퀴즈는 null") String assessmentKind,
		@Schema(description = "사후평가가 비교하는 사전평가 퀴즈 id") Long preAssessmentId,
		@Schema(description = "자료 본문을 요청했지만 쓰지 못한 이유 (만든 직후 응답에만): NO_TEXT(스캔 PDF) / ENCRYPTED / EMPTY / FAILED / PERSONAL_INFO / DISABLED(전송 기능 꺼짐)")
		String materialContentNote,
		// 풀기 화면 응답에는 문제별 개념을 싣지 않는다(QuizApiTest). 그래서 평가 단위의 출처만 "topic"이라는 이름으로 준다
		@Schema(description = "평가 개념의 출처: material(자료에서 뽑은 개념) / course(과목 키워드). 일반 퀴즈는 null")
		String topicSource,
		// 같은 이유로 "topic"이라는 이름을 쓴다. 개념 확인 퀴즈면 그 개념(퀴즈 전체가 이 개념 하나만 다룬다), 아니면 null
		@Schema(description = "개념 확인 퀴즈가 다루는 자료 개념 (materialId, topicId = 개념 키, name). 그 밖의 퀴즈는 null")
		CheckTopic checkTopic,
		List<Question> questions) {

	public record GeneratedBy(String provider, String model) {
	}

	public record CheckTopic(Long materialId, String topicId, String name) {
	}

	public record Question(
			Long id,
			@Schema(description = "multiple_choice / short_answer / essay") String type,
			String text,
			@Schema(description = "객관식 보기. 다른 유형은 빈 배열") List<String> choices,
			@Schema(description = "업무 상황 (업무 활용)") String scenario,
			@Schema(description = "사용자 역할 (업무 활용)") String role,
			@Schema(description = "답변 작성 안내") String guide,
			@Schema(description = "평가 기준 이름 (업무 활용 서술형만, 배점·모범 답안은 없음)") List<String> criteria,
			int maxScore) {
	}

	public static QuizResponse of(Quiz quiz, List<QuizQuestion> questions) {
		return new QuizResponse(quiz.getId(), quiz.getTitle(), quiz.getCourseId(), quiz.getMaterialId(),
				ApiEnumValues.of(quiz.getMode()), ApiEnumValues.of(quiz.getDifficulty()),
				ApiEnumValues.of(quiz.getJobField()), quiz.getJobFieldOther(), quiz.getExamScope(),
				quiz.getGeneratedProvider() == null ? null
						: new GeneratedBy(quiz.getGeneratedProvider(), quiz.getGeneratedModel()),
				quiz.isMaterialContentUsed(), quiz.isMockExam(), ApiEnumValues.of(quiz.getAssessmentKind()),
				quiz.getPreAssessmentId(), null,
				quiz.getConceptSource() == null ? null : quiz.getConceptSource().toLowerCase(java.util.Locale.ROOT),
				quiz.getCheckConceptKey() == null ? null
						: new CheckTopic(quiz.getMaterialId(), quiz.getCheckConceptKey(), quiz.getCheckConceptName()),
				questions.stream().map(q -> new Question(q.getId(), q.getType().apiValue(), q.getContent(),
						List.copyOf(q.getChoices()), q.getScenario(), q.getWorkRole(), q.getAnswerGuide(),
						quiz.getMode() == QuizMode.WORK && q.getType() == QuestionType.ESSAY
								? q.getRubric().stream().map(QuizRubricItem::getCriterion).toList()
								: List.of(),
						q.maxScore()))
						.toList());
	}

	public QuizResponse withMaterialContentNote(String note) {
		return new QuizResponse(id, title, courseId, materialId, mode, difficulty, jobField, jobFieldOther, examScope,
				generatedBy, materialContentUsed, mockExam, assessmentKind, preAssessmentId, note, topicSource,
				checkTopic, questions);
	}
}
