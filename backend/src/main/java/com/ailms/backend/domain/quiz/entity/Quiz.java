package com.ailms.backend.domain.quiz.entity;

import com.ailms.backend.domain.user.entity.JobField;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 퀴즈 하나. 문제는 QuizQuestion에 따로 두고, 올린 본인(userId)만 볼 수 있다.
 * V6 퀴즈는 모드·생성 정보가 없고(null), V7부터 AI로 만든 퀴즈는 학습 모드·난이도·만든 모델을 함께 둔다.
 */
@Entity
@Table(name = "quiz")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Quiz {

	public static final int TITLE_MAX_LENGTH = 100;
	public static final int EXAM_SCOPE_MAX_LENGTH = 200;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long userId;

	@Column(nullable = false)
	private Long courseId;

	// 이 퀴즈와 연결한 자료. 자료 없이 만든 퀴즈는 null.
	// 자료 본문 추출이 아직 없어서, AI로 만든 퀴즈에 자료가 연결돼 있어도 본문은 읽지 않았다(materialContentUsed=false)
	private Long materialId;

	@Column(nullable = false, length = TITLE_MAX_LENGTH)
	private String title;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private QuizMode mode;

	@Enumerated(EnumType.STRING)
	@Column(length = 10)
	private QuizDifficulty difficulty;

	@Enumerated(EnumType.STRING)
	@Column(length = 40)
	private JobField jobField;

	@Column(length = JobField.OTHER_MAX_LENGTH)
	private String jobFieldOther;

	@Column(length = EXAM_SCOPE_MAX_LENGTH)
	private String examScope;

	// 문제를 만든 AI 제공사(gpt·claude·gemini)와 모델. 사람이 넣은 퀴즈는 null
	@Column(length = 10)
	private String generatedProvider;

	@Column(length = 100)
	private String generatedModel;

	// AI가 제안한 다음 학습 개념 (자기개발 모드). 결과 화면에서 "AI 제안"으로 따로 보여 준다
	@ElementCollection
	@CollectionTable(name = "quiz_suggestion", joinColumns = @JoinColumn(name = "quiz_id"))
	@OrderColumn(name = "sort_order")
	private List<QuizSuggestion> suggestions = new ArrayList<>();

	// 모의시험 (시험 대비 모드에서 범위 전체를 실제 시험처럼, V8). 기존 퀴즈는 false
	@Column(nullable = false)
	private boolean mockExam;

	// 사전·사후평가 (V9). 일반 퀴즈는 null
	@Enumerated(EnumType.STRING)
	@Column(length = 10)
	private AssessmentKind assessmentKind;

	// 사후평가가 비교하는 사전평가
	private Long preAssessmentId;

	// 학습자료 본문(발췌)을 AI에 보내 만들었는지 (V9)
	@Column(nullable = false)
	private boolean materialContentUsed;

	// 평가 개념의 출처: MATERIAL(자료에서 뽑은 개념) / COURSE(과목 키워드). 일반 퀴즈는 null (V10)
	@Column(length = 10)
	private String conceptSource;

	// 개념 확인 퀴즈가 다루는 자료 개념 (material_id의 개념 키·이름, V12). 그 밖의 퀴즈는 null
	@Column(length = 20)
	private String checkConceptKey;

	@Column(length = 20)
	private String checkConceptName;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	public static Quiz create(Long userId, Long courseId, Long materialId, String title, LocalDateTime now) {
		Quiz quiz = new Quiz();
		quiz.userId = userId;
		quiz.courseId = courseId;
		quiz.materialId = materialId;
		quiz.title = title;
		quiz.createdAt = now;
		return quiz;
	}

	// AI로 만든 퀴즈. 값은 QuizGenerationService가 검증한 것만 받는다
	public static Quiz generated(Long userId, Long courseId, Long materialId, String title, QuizMode mode,
			QuizDifficulty difficulty, JobField jobField, String jobFieldOther, String examScope, String provider,
			String model, List<QuizSuggestion> suggestions, boolean mockExam, LocalDateTime now) {
		Quiz quiz = create(userId, courseId, materialId, title, now);
		quiz.mode = mode;
		quiz.difficulty = difficulty;
		quiz.jobField = jobField;
		quiz.jobFieldOther = jobField == JobField.OTHER ? jobFieldOther : null;
		quiz.examScope = examScope;
		quiz.generatedProvider = provider;
		quiz.generatedModel = model;
		quiz.suggestions.addAll(suggestions);
		quiz.mockExam = mockExam;
		return quiz;
	}

	// 사전·사후평가로 표시한다 (만들 때만)
	public Quiz asAssessment(AssessmentKind kind, Long preAssessmentId) {
		this.assessmentKind = kind;
		this.preAssessmentId = kind == AssessmentKind.POST ? preAssessmentId : null;
		return this;
	}

	public Quiz withConceptSource(String source) {
		this.conceptSource = source;
		return this;
	}

	// 개념 확인 퀴즈로 표시한다 (만들 때만). 자료(materialId)의 개념 하나
	public Quiz asConceptCheck(String conceptKey, String conceptName) {
		this.checkConceptKey = conceptKey;
		this.checkConceptName = conceptName;
		return this;
	}

	public Quiz withMaterialContentUsed(boolean used) {
		this.materialContentUsed = used;
		return this;
	}
}
