package com.ailms.backend.domain.quiz.dto;

import com.ailms.backend.domain.quiz.entity.QuizGeneration;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * AI로 퀴즈 만들기 (frontend/src/types/quiz.ts의 QuizCreateInput). 값의 뜻은 QuizGenerationService에서 검사한다.
 */
public record QuizCreateRequest(
		@Schema(description = "프론트가 만든 요청 키(UUID 등). 같은 키로 다시 보내면 새로 만들지 않는다")
		@NotBlank(message = "요청 키가 없어요. 화면을 새로 고쳐 주세요.")
		@Size(max = QuizGeneration.REQUEST_KEY_MAX_LENGTH, message = "요청 키가 너무 길어요.")
		String requestKey,
		@NotNull(message = "과목을 골라 주세요.") Long courseId,
		@Schema(description = "exam_prep / self_development / work")
		@NotBlank(message = "학습 모드를 골라 주세요.") String mode,
		@Schema(description = "multiple_choice / short_answer / essay 중 1~3개")
		@NotEmpty(message = "문제 유형을 하나 이상 골라 주세요.") @Size(max = 3, message = "문제 유형을 다시 골라 주세요.")
		List<String> questionTypes,
		@Schema(description = "문제 수 (고른 유형 수 이상, 설정된 최대 이하)")
		@NotNull(message = "문제 수를 골라 주세요.") @Min(value = 1, message = "문제 수를 다시 골라 주세요.") Integer questionCount,
		@Schema(description = "easy / medium / hard. 없으면 medium") String difficulty,
		@Schema(description = "다룰 개념(과목 키워드). 없으면 과목 키워드 전체")
		@Size(max = 20, message = "개념은 20개까지 고를 수 있어요.") List<String> concepts,
		@Schema(description = "시험 범위 (시험 대비 모드, 200자까지)") String examScope,
		@Schema(description = "업무 활용 모드의 직무. 없으면 내 관심 직무, 그것도 없으면 일반 실무") String jobField,
		@Schema(description = "jobField가 other일 때 분야 이름") String jobFieldOther,
		@Schema(description = "연결할 내 자료 (선택). 자료 본문 추출이 아직 없어 본문은 문제에 쓰지 않는다") Long materialId,
		@Schema(description = "모의시험으로 만들기 (exam_prep만, 문제 20개까지). 없으면 false") Boolean mockExam,
		@Schema(description = "사전·사후평가: pre / post. 학습자료(materialId) 필수. post는 같은 자료의 최신 사전평가와 개념·유형·문항 수·난이도를 서버가 맞춘다")
		String assessmentKind,
		@Schema(description = "학습자료 본문(발췌, 12,000자까지)을 외부 AI로 보내 문제 만들기. TXT·MD·PPTX만 읽고 PDF는 아직 못 읽는다")
		Boolean useMaterialContent,
		@Schema(description = "자료 본문을 외부 AI로 보내는 데 대한 이번 요청의 확인 (useMaterialContent일 때 필수)")
		Boolean materialContentConsent) {

	// 일반 퀴즈 만들기 (사전·사후평가·자료 본문 없이)
	public QuizCreateRequest(String requestKey, Long courseId, String mode, List<String> questionTypes,
			Integer questionCount, String difficulty, List<String> concepts, String examScope, String jobField,
			String jobFieldOther, Long materialId, Boolean mockExam) {
		this(requestKey, courseId, mode, questionTypes, questionCount, difficulty, concepts, examScope, jobField,
				jobFieldOther, materialId, mockExam, null, null, null);
	}
}
