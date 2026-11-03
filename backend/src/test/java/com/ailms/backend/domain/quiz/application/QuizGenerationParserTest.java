package com.ailms.backend.domain.quiz.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ailms.backend.domain.quiz.entity.QuestionType;
import com.ailms.backend.domain.quiz.entity.QuizMode;
import com.ailms.backend.domain.quiz.entity.SuggestionKind;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QuizGenerationParserTest {

	private final QuizGenerationParser parser = new QuizGenerationParser(new ObjectMapper());
	private static final List<String> CONCEPTS = List.of("NAT", "VLAN");

	@Test
	void 세_유형을_검증해_읽는다() throws Exception {
		var parsed = parser.parse("```json\n" + QuizFixtures.threeTypes(false) + "\n```", QuizMode.EXAM_PREP,
				plan(1, 1, 1), CONCEPTS);

		assertThat(parsed.questions()).extracting(QuizGenerationParser.ParsedQuestion::type)
				.containsExactly(QuestionType.MULTIPLE_CHOICE, QuestionType.SHORT_ANSWER, QuestionType.ESSAY);
		assertThat(parsed.questions().get(0).answerIndex()).isEqualTo(1);
		assertThat(parsed.questions().get(1).acceptedAnswers()).containsExactly("PAT", "NAPT");
		assertThat(parsed.questions().get(2).rubric()).hasSize(2);
		assertThat(parsed.questions().get(0).concepts().get(0).getConceptKey()).isEqualTo("nat");
		assertThat(parsed.suggestions()).isEmpty();
	}

	@Test
	void 유형별_문제_수가_다르면_거절() {
		assertThatThrownBy(() -> parser.parse(QuizFixtures.threeTypes(false), QuizMode.EXAM_PREP, plan(2, 0, 1),
				CONCEPTS)).isInstanceOf(QuizGenerationParser.InvalidOutputException.class);
	}

	@Test
	void 정답_번호가_범위_밖이거나_여러_개면_거절() {
		String outOfRange = QuizFixtures.threeTypes(false).replace("\"answerIndex\":1", "\"answerIndex\":7");
		String multiple = QuizFixtures.threeTypes(false).replace("\"answerIndex\":1", "\"answerIndex\":[0,1]");

		assertThatThrownBy(() -> parser.parse(outOfRange, QuizMode.EXAM_PREP, plan(1, 1, 1), CONCEPTS))
				.isInstanceOf(QuizGenerationParser.InvalidOutputException.class);
		assertThatThrownBy(() -> parser.parse(multiple, QuizMode.EXAM_PREP, plan(1, 1, 1), CONCEPTS))
				.isInstanceOf(QuizGenerationParser.InvalidOutputException.class);
	}

	@Test
	void 같은_보기가_있거나_서술형_기준이_하나뿐이면_거절() {
		String sameChoice = QuizFixtures.threeTypes(false).replace("\"내부 주소를 숨긴다\"", "\"주소를 바꾼다\"");
		String oneRubric = QuizFixtures.threeTypes(false)
				.replace(",{\"criterion\":\"활용 사례\",\"points\":2}", "");

		assertThatThrownBy(() -> parser.parse(sameChoice, QuizMode.EXAM_PREP, plan(1, 1, 1), CONCEPTS))
				.isInstanceOf(QuizGenerationParser.InvalidOutputException.class);
		assertThatThrownBy(() -> parser.parse(oneRubric, QuizMode.EXAM_PREP, plan(1, 1, 1), CONCEPTS))
				.isInstanceOf(QuizGenerationParser.InvalidOutputException.class);
	}

	@Test
	void 요청하지_않은_개념이나_출제_보장_표현이_있으면_거절() {
		String unknown = QuizFixtures.threeTypes(false).replaceFirst("\"NAT\"", "\"OSPF\"");
		String guarantee = QuizFixtures.threeTypes(false).replace("NAT의 주요 기능", "시험에 반드시 출제되는 NAT의 주요 기능");

		assertThatThrownBy(() -> parser.parse(unknown, QuizMode.EXAM_PREP, plan(1, 1, 1), CONCEPTS))
				.isInstanceOf(QuizGenerationParser.InvalidOutputException.class);
		assertThatThrownBy(() -> parser.parse(guarantee, QuizMode.EXAM_PREP, plan(1, 1, 1), CONCEPTS))
				.isInstanceOf(QuizGenerationParser.InvalidOutputException.class);
	}

	@Test
	void 업무_활용은_상황과_역할이_필수() throws Exception {
		assertThatThrownBy(() -> parser.parse(QuizFixtures.threeTypes(false), QuizMode.WORK, plan(1, 1, 1), CONCEPTS))
				.isInstanceOf(QuizGenerationParser.InvalidOutputException.class);

		var parsed = parser.parse(QuizFixtures.threeTypes(true), QuizMode.WORK, plan(1, 1, 1), CONCEPTS);
		assertThat(parsed.questions()).allMatch(q -> q.scenario() != null && q.role() != null);
	}

	@Test
	void 자기개발은_다음_학습_제안을_읽고_틀린_제안만_버린다() throws Exception {
		String withSuggestions = QuizFixtures.threeTypes(false).replaceFirst("\\}$",
				",\"suggestions\":[{\"kind\":\"next\",\"concept\":\"인터 VLAN 라우팅\",\"reason\":\"다음 단계\"},"
						+ "{\"kind\":\"wrong\",\"concept\":\"x\"},{\"kind\":\"prerequisite\",\"concept\":\"서브넷\"}]}");

		var parsed = parser.parse(withSuggestions, QuizMode.SELF_DEVELOPMENT, plan(1, 1, 1), CONCEPTS);

		assertThat(parsed.suggestions()).extracting(s -> s.getKind())
				.containsExactly(SuggestionKind.NEXT, SuggestionKind.PREREQUISITE);
	}

	@Test
	void JSON이_아니면_거절() {
		assertThatThrownBy(() -> parser.parse("문제를 만들 수 없습니다", QuizMode.EXAM_PREP, plan(1, 1, 1), CONCEPTS))
				.isInstanceOf(QuizGenerationParser.InvalidOutputException.class);
	}

	private static Map<QuestionType, Integer> plan(int mc, int shortAnswer, int essay) {
		java.util.LinkedHashMap<QuestionType, Integer> plan = new java.util.LinkedHashMap<>();
		if (mc > 0) {
			plan.put(QuestionType.MULTIPLE_CHOICE, mc);
		}
		if (shortAnswer > 0) {
			plan.put(QuestionType.SHORT_ANSWER, shortAnswer);
		}
		if (essay > 0) {
			plan.put(QuestionType.ESSAY, essay);
		}
		return plan;
	}
}
