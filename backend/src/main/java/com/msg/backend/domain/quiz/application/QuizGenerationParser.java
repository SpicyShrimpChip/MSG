package com.msg.backend.domain.quiz.application;

import com.msg.backend.domain.course.entity.Course;
import com.msg.backend.domain.material.application.SourceText;
import com.msg.backend.domain.quiz.entity.BloomLevel;
import com.msg.backend.domain.quiz.entity.QuestionType;
import com.msg.backend.domain.quiz.entity.QuizConcept;
import com.msg.backend.domain.quiz.entity.QuizMode;
import com.msg.backend.domain.quiz.entity.QuizQuestion;
import com.msg.backend.domain.quiz.entity.QuizRubricItem;
import com.msg.backend.domain.quiz.entity.QuizSuggestion;
import com.msg.backend.domain.quiz.entity.SuggestionKind;
import com.msg.backend.global.common.Texts;
import com.msg.backend.global.common.VerificationStatus;
import com.msg.backend.global.llm.LlmJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 모델이 만든 퀴즈(JSON)를 검증한다. 하나라도 어기면 퀴즈 전체를 버린다(불완전한 퀴즈를 저장하지 않는다).
 * <ul>
 * <li>전체 문제 수와 유형별 문제 수가 코드가 정한 수(QuizTypePlan)와 같아야 한다.</li>
 * <li>객관식: 보기 3~5개, 서로 다른 글, 정답 번호는 숫자 하나이고 보기 범위 안.</li>
 * <li>주관식: 허용 답 1~5개.</li>
 * <li>서술형: 채점 기준 2~6개, 배점 2~5, 모범 답안 필수.</li>
 * <li>업무 활용: 모든 문제에 상황과 역할 필수.</li>
 * <li>개념은 요청한 개념만, 문제마다 1개 이상.</li>
 * <li>"반드시 출제"처럼 출제를 보장하는 표현이 있으면 거절한다.</li>
 * <li>인지 수준(평가할 수준이 정해진 경우): 문제마다 bloomLevel 필수(한글 이름도 받음), 정한 수준 안, 창조는 서술형만,
 * objective는 표시용이라 빠지면 그 문제만 목표 없이 둔다,
 * 서로 다른 수준을 minDistinct개 이상. 실제 사고 수준이 맞는지는 코드로 판정하지 않는다(형식 검증).</li>
 * <li>자료 근거(자료 발췌를 보낸 경우): 문제마다 source.quote를 보낸 발췌에서 글자 단위로 확인한다(SourceText.check).
 * 위치는 서버가 찾고, AI가 적은 위치가 다르면 일부 일치로 낮추고 메모를 남긴다. 근거를 확인한(일치·일부 일치) 문제가
 * 절반이 안 되면 퀴즈 전체를 버린다. 정답 표현이 근거 부분에 나오는지는 참고 신호로만 남긴다(사실성 검증이 아니다).</li>
 * </ul>
 * 다음 학습 제안(자기개발)은 문제와 달리 틀린 항목만 버린다. 실패 이유는 로그용이고 모델 답변 내용은 넣지 않는다.
 */
@Component
class QuizGenerationParser {

	static final int MIN_CHOICES = 3;
	static final int MAX_CHOICES = 5;
	static final int MAX_ACCEPTED_ANSWERS = 5;
	static final int MIN_RUBRIC = 2;
	static final int MAX_RUBRIC = 6;
	static final int MAX_SUGGESTIONS = 6;
	// 출제를 보장하는 표현 (시험 대비 문제는 추정이다)
	private static final List<String> GUARANTEE_PHRASES = List.of("반드시 출제", "반드시 나오", "반드시 나옵", "꼭 출제",
			"꼭 나오", "꼭 나옵", "100% 출제", "출제 확정", "무조건 출제", "무조건 나오");

	private final ObjectMapper objectMapper;

	QuizGenerationParser(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	static class InvalidOutputException extends Exception {
		InvalidOutputException(String reason) {
			super(reason);
		}
	}

	/** 형식 검증 외에 확인할 것: 평가할 인지 수준(비면 확인하지 않음)과 보낸 자료 발췌(없으면 null) */
	record Expectation(List<BloomLevel> bloomTargets, int minDistinctLevels, String materialExcerpt) {
	}

	/** 자료 근거 검증 결과. check가 null이면 자료 없이 만든 문제 */
	record Source(VerificationStatus check, String quote, String location, String note, Boolean answerInSource) {

		static final Source NONE = new Source(null, null, null, null, null);

		boolean grounded() {
			return check == VerificationStatus.VERIFIED || check == VerificationStatus.PARTIAL;
		}
	}

	record ParsedQuestion(QuestionType type, String text, List<String> choices, Integer answerIndex,
			List<String> acceptedAnswers, List<QuizRubricItem> rubric, String modelAnswer, String guide,
			String explanation, List<QuizConcept> concepts, String scenario, String role, BloomLevel bloomLevel,
			String objective, Source source) {

		QuizQuestion toEntity(Long quizId, int sortOrder) {
			QuizQuestion question = switch (type) {
				case MULTIPLE_CHOICE -> QuizQuestion.create(quizId, sortOrder, text, choices, answerIndex, explanation,
						concepts);
				case SHORT_ANSWER -> QuizQuestion.shortAnswer(quizId, sortOrder, text, acceptedAnswers, guide,
						explanation, concepts);
				case ESSAY -> QuizQuestion.essay(quizId, sortOrder, text, rubric, modelAnswer, guide, explanation,
						concepts);
			};
			return question.withWorkContext(scenario, role).withLearningMeta(bloomLevel, objective)
					.withSource(source.check(), source.quote(), source.location(), source.note(), source.answerInSource());
		}
	}

	record ParsedQuiz(List<ParsedQuestion> questions, List<QuizSuggestion> suggestions) {
	}

	ParsedQuiz parse(String raw, QuizMode mode, Map<QuestionType, Integer> plan, List<String> concepts,
			Expectation expectation) throws InvalidOutputException {
		JsonNode root = LlmJson.readObject(objectMapper, raw)
				.orElseThrow(() -> new InvalidOutputException("JSON 객체가 아니다"));
		JsonNode items = root.get("questions");
		int expected = QuizTypePlan.total(plan);
		if (items == null || !items.isArray() || items.size() != expected) {
			throw new InvalidOutputException("문제 수가 다르다");
		}
		Map<String, String> conceptByKey = new LinkedHashMap<>();
		concepts.forEach(name -> conceptByKey.put(Course.nameKey(name), name));
		Map<QuestionType, Integer> counted = new EnumMap<>(QuestionType.class);
		List<ParsedQuestion> questions = new ArrayList<>();
		for (JsonNode item : items) {
			ParsedQuestion question = question(item, mode, conceptByKey, expectation);
			counted.merge(question.type(), 1, Integer::sum);
			questions.add(question);
		}
		if (!counted.equals(new EnumMap<>(plan))) {
			throw new InvalidOutputException("유형별 문제 수가 다르다");
		}
		if (!expectation.bloomTargets().isEmpty()) {
			long distinct = questions.stream().map(ParsedQuestion::bloomLevel).distinct().count();
			if (distinct < expectation.minDistinctLevels()) {
				throw new InvalidOutputException("다룬 인지 수준이 모자라다");
			}
		}
		if (expectation.materialExcerpt() != null) {
			long grounded = questions.stream().filter(question -> question.source().grounded()).count();
			if (grounded * 2 < questions.size()) {
				throw new InvalidOutputException("자료 근거를 확인한 문제가 절반이 안 된다");
			}
		}
		List<QuizSuggestion> suggestions = mode == QuizMode.SELF_DEVELOPMENT ? suggestions(root.get("suggestions"))
				: List.of();
		return new ParsedQuiz(questions, suggestions);
	}

	private ParsedQuestion question(JsonNode item, QuizMode mode, Map<String, String> conceptByKey,
			Expectation expectation) throws InvalidOutputException {
		if (item == null || !item.isObject()) {
			throw new InvalidOutputException("문제가 객체가 아니다");
		}
		QuestionType type = QuestionType.fromApiValue(LlmJson.text(item.get("type")))
				.orElseThrow(() -> new InvalidOutputException("모르는 유형"));
		String text = required(item.get("text"), QuizQuestion.CONTENT_MAX_LENGTH, "문제 글");
		String explanation = optional(item.get("explanation"), QuizQuestion.EXPLANATION_MAX_LENGTH, "해설");
		String guide = optional(item.get("guide"), QuizQuestion.ANSWER_GUIDE_MAX_LENGTH, "작성 안내");
		List<QuizConcept> concepts = concepts(item.get("concepts"), conceptByKey);
		String scenario = null;
		String role = null;
		if (mode == QuizMode.WORK) {
			scenario = required(item.get("scenario"), QuizQuestion.SCENARIO_MAX_LENGTH, "업무 상황");
			role = required(item.get("role"), QuizQuestion.WORK_ROLE_MAX_LENGTH, "역할");
		}
		List<String> choices = List.of();
		Integer answerIndex = null;
		List<String> accepted = List.of();
		List<QuizRubricItem> rubric = List.of();
		String modelAnswer = null;
		switch (type) {
			case MULTIPLE_CHOICE -> {
				choices = choices(item.get("choices"));
				JsonNode index = item.get("answerIndex");
				if (index == null || !index.isIntegralNumber() || index.asInt() < 0 || index.asInt() >= choices.size()) {
					throw new InvalidOutputException("정답 번호가 하나가 아니거나 범위 밖이다");
				}
				answerIndex = index.asInt();
				guide = null;
			}
			case SHORT_ANSWER -> accepted = acceptedAnswers(item.get("acceptedAnswers"));
			case ESSAY -> {
				rubric = rubric(item.get("rubric"));
				modelAnswer = required(item.get("modelAnswer"), QuizQuestion.MODEL_ANSWER_MAX_LENGTH, "모범 답안");
			}
		}
		rejectGuarantee(text, explanation, scenario, guide, modelAnswer);
		for (String choice : choices) {
			rejectGuarantee(choice);
		}
		BloomLevel bloomLevel = BloomLevel.fromApiValue(LlmJson.text(item.get("bloomLevel"))).orElse(null);
		String objective = optional(item.get("objective"), QuizQuestion.OBJECTIVE_MAX_LENGTH, "출제 목표");
		if (!expectation.bloomTargets().isEmpty()) {
			if (bloomLevel == null || !expectation.bloomTargets().contains(bloomLevel)) {
				throw new InvalidOutputException("인지 수준이 없거나 정한 수준 밖이다");
			}
		}
		if (bloomLevel == BloomLevel.CREATE && type != QuestionType.ESSAY) {
			throw new InvalidOutputException("창조 수준은 서술형만");
		}
		Source source = expectation.materialExcerpt() == null ? Source.NONE
				: source(item.get("source"), expectation.materialExcerpt(), type, choices, answerIndex, accepted);
		return new ParsedQuestion(type, text, choices, answerIndex, accepted, rubric, modelAnswer, guide, explanation,
				concepts, scenario, role, bloomLevel, objective, source);
	}

	// 근거 문장을 보낸 발췌에서 확인한다. 위치는 서버가 찾은 값을 쓴다
	private static Source source(JsonNode node, String excerpt, QuestionType type, List<String> choices,
			Integer answerIndex, List<String> accepted) {
		String quote = node == null ? null : LlmJson.text(node.get("quote"));
		String claimed = node == null ? null : LlmJson.text(node.get("location"));
		SourceText.QuoteCheck check = SourceText.check(excerpt, quote);
		VerificationStatus status = check.status();
		String note = switch (status) {
			case INSUFFICIENT -> "AI가 근거 문장을 들지 않았어요.";
			case FAILED -> "AI가 든 근거 문장을 보낸 자료에서 찾지 못했어요.";
			case PARTIAL -> "근거 문장이 자료와 일부만 일치해요.";
			default -> null;
		};
		if ((status == VerificationStatus.VERIFIED || status == VerificationStatus.PARTIAL) && claimed != null
				&& check.location() != null && !sameLocation(claimed, check.location())) {
			status = VerificationStatus.PARTIAL;
			String fixed = "AI가 적은 위치(" + Texts.clip(claimed, 20) + ")가 실제 위치와 달라 실제 위치로 고쳤어요.";
			note = note == null ? fixed : note + " " + fixed;
		}
		Boolean answerInSource = null;
		if (check.sectionText() != null) {
			if (type == QuestionType.MULTIPLE_CHOICE && answerIndex != null) {
				answerInSource = SourceText.mentions(check.sectionText(), choices.get(answerIndex));
			} else if (type == QuestionType.SHORT_ANSWER) {
				answerInSource = accepted.stream().anyMatch(answer -> SourceText.mentions(check.sectionText(), answer));
			}
		}
		String storedQuote = check.quote() == null ? null
				: Texts.clip(check.quote(), QuizQuestion.SOURCE_QUOTE_MAX_LENGTH);
		return new Source(status, storedQuote, check.location(), note == null ? null
				: Texts.clip(note, QuizQuestion.SOURCE_NOTE_MAX_LENGTH), answerInSource);
	}

	// "[p. 3]", "p.3", "P. 3"을 같은 위치로 본다
	private static boolean sameLocation(String a, String b) {
		return a.replaceAll("[\\[\\]\\s]", "").toLowerCase(Locale.ROOT)
				.equals(b.replaceAll("[\\[\\]\\s]", "").toLowerCase(Locale.ROOT));
	}

	private static List<QuizConcept> concepts(JsonNode node, Map<String, String> conceptByKey)
			throws InvalidOutputException {
		if (node == null || !node.isArray() || node.isEmpty()) {
			throw new InvalidOutputException("개념이 없다");
		}
		List<QuizConcept> concepts = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (JsonNode item : node) {
			String name = LlmJson.text(item);
			String key = name == null ? null : Course.nameKey(name);
			if (key == null || !conceptByKey.containsKey(key)) {
				throw new InvalidOutputException("요청하지 않은 개념");
			}
			if (seen.add(key)) {
				concepts.add(QuizConcept.of(key, conceptByKey.get(key)));
			}
		}
		return concepts;
	}

	private static List<String> choices(JsonNode node) throws InvalidOutputException {
		if (node == null || !node.isArray() || node.size() < MIN_CHOICES || node.size() > MAX_CHOICES) {
			throw new InvalidOutputException("보기 수가 맞지 않다");
		}
		List<String> choices = new ArrayList<>();
		Set<String> normalized = new HashSet<>();
		for (JsonNode item : node) {
			String choice = required(item, QuizQuestion.CHOICE_MAX_LENGTH, "보기");
			if (!normalized.add(ShortAnswerMatcher.normalize(choice))) {
				throw new InvalidOutputException("같은 보기가 있다");
			}
			choices.add(choice);
		}
		return choices;
	}

	private static List<String> acceptedAnswers(JsonNode node) throws InvalidOutputException {
		if (node == null || !node.isArray() || node.isEmpty()) {
			throw new InvalidOutputException("허용 답이 없다");
		}
		List<String> answers = new ArrayList<>();
		Set<String> normalized = new HashSet<>();
		for (JsonNode item : node) {
			String answer = required(item, QuizQuestion.ACCEPTED_ANSWER_MAX_LENGTH, "허용 답");
			if (!ShortAnswerMatcher.normalize(answer).isEmpty() && normalized.add(ShortAnswerMatcher.normalize(answer))
					&& answers.size() < MAX_ACCEPTED_ANSWERS) {
				answers.add(answer);
			}
		}
		if (answers.isEmpty()) {
			throw new InvalidOutputException("허용 답이 없다");
		}
		return answers;
	}

	private static List<QuizRubricItem> rubric(JsonNode node) throws InvalidOutputException {
		if (node == null || !node.isArray() || node.size() < MIN_RUBRIC || node.size() > MAX_RUBRIC) {
			throw new InvalidOutputException("채점 기준 수가 맞지 않다");
		}
		List<QuizRubricItem> rubric = new ArrayList<>();
		for (JsonNode item : node) {
			String criterion = required(item == null ? null : item.get("criterion"), QuizRubricItem.CRITERION_MAX_LENGTH,
					"채점 기준");
			JsonNode points = item.get("points");
			if (points == null || !points.isIntegralNumber() || points.asInt() < QuizRubricItem.MIN_POINTS
					|| points.asInt() > QuizRubricItem.MAX_POINTS) {
				throw new InvalidOutputException("배점이 범위 밖이다");
			}
			rubric.add(QuizRubricItem.of(criterion, points.asInt()));
		}
		return rubric;
	}

	private static List<QuizSuggestion> suggestions(JsonNode node) {
		if (node == null || !node.isArray()) {
			return List.of();
		}
		List<QuizSuggestion> suggestions = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (JsonNode item : node) {
			if (suggestions.size() >= MAX_SUGGESTIONS || item == null || !item.isObject()) {
				continue;
			}
			var kind = SuggestionKind.fromApiValue(LlmJson.text(item.get("kind")));
			String concept = LlmJson.text(item.get("concept"));
			if (kind.isEmpty() || concept == null || concept.length() > QuizSuggestion.CONCEPT_MAX_LENGTH
					|| !seen.add(kind.get() + ":" + concept.toLowerCase(Locale.ROOT))) {
				continue;
			}
			String reason = Texts.cut(LlmJson.text(item.get("reason")), QuizSuggestion.REASON_MAX_LENGTH);
			suggestions.add(QuizSuggestion.of(kind.get(), concept, reason));
		}
		return suggestions;
	}

	private static String required(JsonNode node, int maxLength, String what) throws InvalidOutputException {
		String text = LlmJson.text(node);
		if (text == null) {
			throw new InvalidOutputException(what + "이(가) 없다");
		}
		if (text.length() > maxLength) {
			throw new InvalidOutputException(what + "이(가) 너무 길다");
		}
		return text;
	}

	private static String optional(JsonNode node, int maxLength, String what) throws InvalidOutputException {
		String text = LlmJson.text(node);
		if (text != null && text.length() > maxLength) {
			throw new InvalidOutputException(what + "이(가) 너무 길다");
		}
		return text;
	}

	private static void rejectGuarantee(String... texts) throws InvalidOutputException {
		for (String text : texts) {
			if (text == null) {
				continue;
			}
			String compact = text.replaceAll("\\s+", " ");
			for (String phrase : GUARANTEE_PHRASES) {
				if (compact.contains(phrase)) {
					throw new InvalidOutputException("출제를 보장하는 표현이 있다");
				}
			}
		}
	}
}
