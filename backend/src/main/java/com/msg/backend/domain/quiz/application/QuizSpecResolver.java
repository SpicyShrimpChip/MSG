package com.msg.backend.domain.quiz.application;

import com.msg.backend.domain.material.application.MaterialContentProperties;
import com.msg.backend.domain.material.application.MaterialTextExtractor;
import com.msg.backend.domain.material.application.SourceText;

import com.msg.backend.domain.conversation.application.PersonalInfoDetector;
import com.msg.backend.domain.course.dao.CourseRepository;
import com.msg.backend.domain.course.entity.Course;
import com.msg.backend.domain.material.dao.MaterialConceptRepository;
import com.msg.backend.domain.material.dao.MaterialRepository;
import com.msg.backend.domain.material.entity.Material;
import com.msg.backend.domain.material.entity.MaterialConcept;
import com.msg.backend.domain.quiz.dao.QuizQuestionRepository;
import com.msg.backend.domain.quiz.dao.QuizRepository;
import com.msg.backend.domain.quiz.dto.QuizCreateRequest;
import com.msg.backend.domain.quiz.entity.AssessmentKind;
import com.msg.backend.domain.quiz.entity.BloomLevel;
import com.msg.backend.domain.quiz.entity.QuestionType;
import com.msg.backend.domain.quiz.entity.Quiz;
import com.msg.backend.domain.quiz.entity.QuizDifficulty;
import com.msg.backend.domain.quiz.entity.QuizMode;
import com.msg.backend.domain.quiz.entity.QuizQuestion;
import com.msg.backend.domain.user.application.LearningProfileValidator;
import com.msg.backend.domain.user.dao.UserRepository;
import com.msg.backend.domain.user.entity.JobField;
import com.msg.backend.domain.user.entity.User;
import com.msg.backend.global.error.BusinessException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 퀴즈 만들기 요청을 검사해 QuizSpec으로 만든다. 사용자 행을 잠근 QuizGenerationService의 트랜잭션 안에서 부른다.
 *
 * <p>검사 순서(첫 오류를 돌려준다): 과목 → 자료 → 평가 종류 → 개념 확인 퀴즈 → 사후평가의 사전평가 → 모드 → 유형 → 문제 수·모의시험
 * → 난이도 → 인지 수준 → 개념 → 시험 범위 → 직무 분야 → 자료 본문. 사후평가는 사전평가의 개념·유형·문항 수·난이도·모드·인지 수준을 쓰고
 * 요청 값은 쓰지 않는다.
 *
 * <p>자료 본문: 원문을 위치(쪽·슬라이드·문단)가 붙은 부분으로 나눠 개념이 나오는 부분만 골라 보낸다(SourceText). 고른 위치와
 * 자료에서 찾지 못한 개념을 함께 남겨 화면이 "자료 어디로 만들었는지"를 보여 준다.
 */
@Component
class QuizSpecResolver {

	static final String CHECK_NEEDS_MATERIAL_MESSAGE = "개념 확인 퀴즈는 그 개념을 뽑은 학습자료를 골라야 만들 수 있어요.";
	static final String CHECK_COMBINATION_MESSAGE = "개념 확인 퀴즈는 사전·사후평가나 모의시험과 함께 만들 수 없어요.";
	static final String CHECK_CONCEPT_NOT_FOUND_MESSAGE = "이 자료에서 그 개념을 찾을 수 없어요. 핵심 개념을 먼저 뽑아 주세요.";
	static final String MATERIAL_COURSE_MESSAGE = "고른 자료가 이 과목에 연결돼 있지 않아요.";
	static final String MODE_MESSAGE = "학습 모드를 다시 골라 주세요.";
	static final String TYPE_MESSAGE = "문제 유형을 다시 골라 주세요.";
	static final String DIFFICULTY_MESSAGE = "난이도를 다시 골라 주세요.";
	static final String NO_CONCEPTS_MESSAGE = "과목에 키워드를 먼저 넣어 주세요. 키워드가 퀴즈의 개념이 돼요.";
	static final String UNKNOWN_CONCEPT_MESSAGE = "과목 키워드에 없는 개념이 있어요. 과목 키워드에서 골라 주세요.";
	static final String SCOPE_TOO_LONG_MESSAGE = "시험 범위는 " + Quiz.EXAM_SCOPE_MAX_LENGTH + "자까지 적을 수 있어요.";
	static final String SCOPE_PERSONAL_INFO_MESSAGE = "시험 범위에 이메일·전화번호 같은 개인정보는 적지 말아 주세요.";
	static final String MOCK_EXAM_MODE_MESSAGE = "모의시험은 시험 대비 모드에서만 만들 수 있어요.";
	static final String ASSESSMENT_KIND_MESSAGE = "평가 종류를 다시 골라 주세요.";
	static final String ASSESSMENT_MATERIAL_MESSAGE = "사전·사후평가는 과목에 연결된 학습자료를 골라야 해요.";
	static final String NO_PRE_ASSESSMENT_MESSAGE = "이 자료로 먼저 사전평가를 만들어 주세요. 사후평가는 사전평가와 같은 개념으로 만들어요.";
	static final String CONTENT_NEEDS_MATERIAL_MESSAGE = "자료 본문을 쓰려면 학습자료를 골라 주세요.";
	static final String CONTENT_CONSENT_MESSAGE = "자료 본문을 외부 AI로 보내려면 확인에 체크해 주세요.";
	static final String BLOOM_MESSAGE = "인지 수준을 다시 골라 주세요.";
	static final int MAX_CONCEPTS = 10;
	static final int MAX_AVOID_QUESTIONS = 20;

	private final UserRepository userRepository;
	private final CourseRepository courseRepository;
	private final MaterialRepository materialRepository;
	private final MaterialConceptRepository materialConceptRepository;
	private final QuizRepository quizRepository;
	private final QuizQuestionRepository questionRepository;
	private final LearningProfileValidator profileValidator;
	private final PersonalInfoDetector personalInfoDetector;
	private final MaterialTextExtractor textExtractor;
	private final MaterialContentProperties contentProperties;
	private final QuizProperties properties;

	QuizSpecResolver(UserRepository userRepository, CourseRepository courseRepository,
			MaterialRepository materialRepository, MaterialConceptRepository materialConceptRepository,
			QuizRepository quizRepository, QuizQuestionRepository questionRepository,
			LearningProfileValidator profileValidator, PersonalInfoDetector personalInfoDetector,
			MaterialTextExtractor textExtractor, MaterialContentProperties contentProperties, QuizProperties properties) {
		this.userRepository = userRepository;
		this.courseRepository = courseRepository;
		this.materialRepository = materialRepository;
		this.materialConceptRepository = materialConceptRepository;
		this.quizRepository = quizRepository;
		this.questionRepository = questionRepository;
		this.profileValidator = profileValidator;
		this.personalInfoDetector = personalInfoDetector;
		this.textExtractor = textExtractor;
		this.contentProperties = contentProperties;
		this.properties = properties;
	}

	// 사후평가가 따르는 사전평가 (사후평가가 아니면 없음)
	private record Pre(Quiz quiz, List<QuizQuestion> questions) {

		static final Pre NONE = new Pre(null, List.of());

		boolean hasQuestions() {
			return quiz != null && !questions.isEmpty();
		}
	}

	private record Content(String excerpt, String note, List<String> scope, List<String> missing) {

		static Content none(String note) {
			return new Content(null, note, List.of(), List.of());
		}
	}

	QuizSpec resolve(Long userId, QuizCreateRequest request) {
		Course course = courseRepository.getOwned(request.courseId(), userId);
		Material material = material(userId, request, course);
		AssessmentKind kind = assessmentKind(request, material);
		MaterialConcept checkConcept = checkConcept(userId, request, material, kind);
		Pre pre = kind == AssessmentKind.POST ? preAssessment(userId, material) : Pre.NONE;
		QuizMode mode = pre.quiz() != null && pre.quiz().getMode() != null ? pre.quiz().getMode()
				: QuizMode.fromApiValue(request.mode()).orElseThrow(() -> badRequest(MODE_MESSAGE));
		Set<QuestionType> types = types(request, pre);
		int count = pre.hasQuestions() ? pre.questions().size() : request.questionCount();
		boolean mockExam = Boolean.TRUE.equals(request.mockExam()) && kind == null;
		checkCount(request, mode, mockExam, count, types);
		QuizDifficulty difficulty = difficulty(request, pre);
		List<BloomLevel> bloomTargets = bloomTargets(request, pre, mode, types);
		String conceptSource = null;
		List<String> concepts;
		// 개념 체계: 사후평가는 사전평가 문제의 개념 그대로, 평가는 자료에서 뽑은 개념이 있으면 그것, 아니면 과목 키워드
		if (pre.hasQuestions()) {
			concepts = preConcepts(course, pre.questions());
			conceptSource = pre.quiz().getConceptSource();
		} else if (checkConcept != null) {
			concepts = List.of(checkConcept.getName());
		} else if (kind != null) {
			List<String> fromMaterial = materialConceptRepository
					.findAllByMaterialIdAndUserIdOrderBySortOrderAsc(material.getId(), userId).stream()
					.map(MaterialConcept::getName)
					.limit(MAX_CONCEPTS)
					.toList();
			concepts = fromMaterial.isEmpty() ? concepts(course, request.concepts()) : fromMaterial;
			conceptSource = fromMaterial.isEmpty() ? "COURSE" : "MATERIAL";
		} else {
			concepts = concepts(course, request.concepts());
		}
		String examScope = examScope(request, mode);
		JobField jobField = null;
		String jobFieldOther = null;
		if (mode == QuizMode.WORK) {
			if (StringUtils.hasText(request.jobField())) {
				var profile = profileValidator.validate(null, request.jobField(), request.jobFieldOther());
				jobField = profile.jobField();
				jobFieldOther = profile.jobFieldOther();
			} else {
				User user = userRepository.findById(userId).orElseThrow();
				jobField = user.getJobField();
				jobFieldOther = user.getJobFieldOther();
			}
		}
		Content content = materialContent(request, material, pre.quiz(), concepts);
		return new QuizSpec(course, material == null ? null : material.getId(),
				material == null ? null : material.getTitle(), mode, List.copyOf(types),
				QuizTypePlan.plan(mode, types, count), difficulty, concepts, examScope, jobField, jobFieldOther, mockExam,
				kind, pre.quiz() == null ? null : pre.quiz().getId(),
				pre.questions().stream().map(QuizQuestion::getContent).limit(MAX_AVOID_QUESTIONS).toList(),
				content.excerpt(), content.note(), conceptSource,
				checkConcept == null ? null : checkConcept.getConceptKey(),
				checkConcept == null ? null : checkConcept.getName(), bloomTargets, content.scope(), content.missing());
	}

	// 인지 수준: 사후평가는 사전평가에서 고른 수준(없으면 사전평가 문제의 수준), 아니면 요청 값, 없으면 모드 기본값
	private static List<BloomLevel> bloomTargets(QuizCreateRequest request, Pre pre, QuizMode mode,
			Set<QuestionType> types) {
		List<BloomLevel> requested = new ArrayList<>();
		if (pre.quiz() != null) {
			requested.addAll(BloomPlan.split(pre.quiz().getBloomTargets()));
			if (requested.isEmpty()) {
				pre.questions().stream().map(QuizQuestion::getBloomLevel).filter(level -> level != null).distinct()
						.forEach(requested::add);
			}
		} else if (request.bloomLevels() != null) {
			Set<BloomLevel> seen = EnumSet.noneOf(BloomLevel.class);
			for (String value : request.bloomLevels()) {
				if (!StringUtils.hasText(value)) {
					continue;
				}
				BloomLevel level = BloomLevel.fromApiValue(value).orElseThrow(() -> badRequest(BLOOM_MESSAGE));
				if (seen.add(level)) {
					requested.add(level);
				}
			}
		}
		try {
			return BloomPlan.targets(mode, requested, types);
		} catch (IllegalArgumentException e) {
			throw badRequest(e.getMessage());
		}
	}

	// 고른 자료는 내 것이고 이 과목에 연결돼 있어야 한다
	private Material material(Long userId, QuizCreateRequest request, Course course) {
		if (request.materialId() == null) {
			return null;
		}
		Material material = materialRepository.getOwned(request.materialId(), userId);
		if (!course.getId().equals(material.getCourseId())) {
			throw badRequest(MATERIAL_COURSE_MESSAGE);
		}
		return material;
	}

	// 사전·사후평가는 자료가 있어야 한다
	private static AssessmentKind assessmentKind(QuizCreateRequest request, Material material) {
		if (!StringUtils.hasText(request.assessmentKind())) {
			return null;
		}
		AssessmentKind kind = AssessmentKind.fromApiValue(request.assessmentKind())
				.orElseThrow(() -> badRequest(ASSESSMENT_KIND_MESSAGE));
		if (material == null) {
			throw badRequest(ASSESSMENT_MATERIAL_MESSAGE);
		}
		return kind;
	}

	// 개념 확인 퀴즈: 자료에서 뽑은 개념 하나만 다룬다(다른 개념 문제는 파서가 거절). 평가·모의시험과 함께 쓰지 않는다
	private MaterialConcept checkConcept(Long userId, QuizCreateRequest request, Material material,
			AssessmentKind kind) {
		if (!StringUtils.hasText(request.checkConceptId())) {
			return null;
		}
		if (material == null) {
			throw badRequest(CHECK_NEEDS_MATERIAL_MESSAGE);
		}
		if (kind != null || Boolean.TRUE.equals(request.mockExam())) {
			throw badRequest(CHECK_COMBINATION_MESSAGE);
		}
		String key = request.checkConceptId().strip();
		return materialConceptRepository.findAllByMaterialIdAndUserIdOrderBySortOrderAsc(material.getId(), userId)
				.stream()
				.filter(concept -> concept.getConceptKey().equals(key))
				.findFirst()
				.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, CHECK_CONCEPT_NOT_FOUND_MESSAGE));
	}

	// 사후평가: 같은 자료의 최신 사전평가
	private Pre preAssessment(Long userId, Material material) {
		Quiz pre = quizRepository.findFirstByUserIdAndMaterialIdAndAssessmentKindOrderByIdDesc(userId,
				material.getId(), AssessmentKind.PRE)
				.orElseThrow(() -> new BusinessException(HttpStatus.CONFLICT, NO_PRE_ASSESSMENT_MESSAGE));
		return new Pre(pre, questionRepository.findAllByQuizIdOrderBySortOrderAsc(pre.getId()));
	}

	// 유형: 사후평가는 사전평가 문제의 유형, 아니면 요청 값(같은 유형 두 번은 거절)
	private static Set<QuestionType> types(QuizCreateRequest request, Pre pre) {
		Set<QuestionType> types = new LinkedHashSet<>();
		if (pre.hasQuestions()) {
			pre.questions().forEach(question -> types.add(question.getType()));
			return types;
		}
		for (String value : request.questionTypes()) {
			if (!types.add(QuestionType.fromApiValue(value).orElseThrow(() -> badRequest(TYPE_MESSAGE)))) {
				throw badRequest(TYPE_MESSAGE);
			}
		}
		return types;
	}

	private void checkCount(QuizCreateRequest request, QuizMode mode, boolean mockExam, int count,
			Set<QuestionType> types) {
		if (Boolean.TRUE.equals(request.mockExam()) && mode != QuizMode.EXAM_PREP) {
			throw badRequest(MOCK_EXAM_MODE_MESSAGE);
		}
		int max = mockExam ? properties.mockExamMaxQuestionCount() : properties.maxQuestionCount();
		if (count > max) {
			throw badRequest((mockExam ? "모의시험 문제는 " : "문제는 ") + max + "개까지 만들 수 있어요.");
		}
		if (count < types.size()) {
			throw badRequest("고른 유형이 " + types.size() + "가지라 문제 수는 " + types.size() + "개 이상이어야 해요.");
		}
	}

	private static QuizDifficulty difficulty(QuizCreateRequest request, Pre pre) {
		if (pre.quiz() != null && pre.quiz().getDifficulty() != null) {
			return pre.quiz().getDifficulty();
		}
		return StringUtils.hasText(request.difficulty())
				? QuizDifficulty.fromApiValue(request.difficulty()).orElseThrow(() -> badRequest(DIFFICULTY_MESSAGE))
				: QuizDifficulty.MEDIUM;
	}

	// 시험 범위는 시험 대비 모드에서만 쓴다. 길이와 개인정보를 확인한다
	private String examScope(QuizCreateRequest request, QuizMode mode) {
		if (mode != QuizMode.EXAM_PREP || !StringUtils.hasText(request.examScope())) {
			return null;
		}
		String examScope = request.examScope().strip();
		if (examScope.length() > Quiz.EXAM_SCOPE_MAX_LENGTH) {
			throw badRequest(SCOPE_TOO_LONG_MESSAGE);
		}
		if (personalInfoDetector.containsPersonalInfoExceptIp(examScope)) {
			throw badRequest(SCOPE_PERSONAL_INFO_MESSAGE);
		}
		return examScope;
	}

	// 학습자료 본문: 사용자가 이 요청에서 보내기를 고르고 확인한 경우에만 읽는다. 사후평가는 사전평가가 본문을 썼으면 같게
	private Content materialContent(QuizCreateRequest request, Material material, Quiz pre, List<String> concepts) {
		boolean requested = Boolean.TRUE.equals(request.useMaterialContent());
		if (requested && !contentProperties.enabled()) {
			// 운영 설정·처리방침 확정 전에는 자료 본문을 외부로 보내지 않는다
			throw new BusinessException(HttpStatus.FORBIDDEN, MaterialContentProperties.DISABLED_MESSAGE);
		}
		boolean wantContent = contentProperties.enabled() && (requested || (pre != null && pre.isMaterialContentUsed()));
		String note = pre != null && pre.isMaterialContentUsed() && !contentProperties.enabled() ? "DISABLED" : null;
		if (!wantContent) {
			return Content.none(note);
		}
		if (material == null) {
			throw badRequest(CONTENT_NEEDS_MATERIAL_MESSAGE);
		}
		if (!Boolean.TRUE.equals(request.materialContentConsent())) {
			throw badRequest(CONTENT_CONSENT_MESSAGE);
		}
		MaterialTextExtractor.Extracted extracted = textExtractor.extract(material, contentProperties.sourceMaxChars());
		if (extracted.status() != MaterialTextExtractor.Status.OK) {
			return Content.none(extracted.status().name());
		}
		// 원문을 위치가 붙은 부분으로 나눠 개념이 나오는 부분만 고른다 (관련 없는 본문은 보내지 않는다)
		SourceText source = SourceText.parse(extracted.text());
		SourceText.Selection selection = source.select(concepts, contentProperties.maxChars());
		if (selection.text().isBlank()) {
			return Content.none(MaterialTextExtractor.Status.EMPTY.name());
		}
		if (personalInfoDetector.containsPersonalInfoExceptIp(selection.text())) {
			// 보낼 부분에 개인정보 패턴이 있으면 보내지 않고 과목·개념으로만 만든다
			return Content.none("PERSONAL_INFO");
		}
		return new Content(selection.text(), note, selection.locations(), source.missingTerms(concepts));
	}

	// 사전평가 문제들이 다룬 개념 (과목 키워드 이름으로, 처음 나온 순서)
	private static List<String> preConcepts(Course course, List<QuizQuestion> questions) {
		Map<String, String> chosenByKey = new LinkedHashMap<>();
		questions.forEach(question -> question.getConcepts()
				.forEach(concept -> chosenByKey.putIfAbsent(concept.getConceptKey(), concept.getConceptName())));
		Set<String> chosen = new LinkedHashSet<>(chosenByKey.values());
		if (chosen.isEmpty()) {
			return concepts(course, null);
		}
		return List.copyOf(chosen);
	}

	// 요청 개념은 과목 키워드 안에서만 (대소문자 무시). 비었으면 과목 키워드 앞에서부터 MAX_CONCEPTS개
	private static List<String> concepts(Course course, List<String> requested) {
		Map<String, String> keywords = new LinkedHashMap<>();
		course.getKeywords().forEach(keyword -> keywords.putIfAbsent(Course.nameKey(keyword), keyword));
		if (keywords.isEmpty()) {
			throw badRequest(NO_CONCEPTS_MESSAGE);
		}
		if (requested == null || requested.stream().noneMatch(StringUtils::hasText)) {
			return keywords.values().stream().limit(MAX_CONCEPTS).toList();
		}
		Set<String> chosen = new LinkedHashSet<>();
		for (String concept : requested) {
			if (!StringUtils.hasText(concept)) {
				continue;
			}
			String name = keywords.get(Course.nameKey(concept.strip()));
			if (name == null) {
				throw badRequest(UNKNOWN_CONCEPT_MESSAGE);
			}
			chosen.add(name);
		}
		if (chosen.size() > MAX_CONCEPTS) {
			throw badRequest("개념은 한 번에 " + MAX_CONCEPTS + "개까지 고를 수 있어요.");
		}
		return List.copyOf(chosen);
	}

	private static BusinessException badRequest(String message) {
		return new BusinessException(HttpStatus.BAD_REQUEST, message);
	}
}
