package com.ailms.backend.domain.quiz.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.ailms.backend.domain.quiz.entity.GradingStatus;
import com.ailms.backend.domain.quiz.entity.QuizConcept;
import com.ailms.backend.domain.quiz.entity.QuizQuestion;
import com.ailms.backend.domain.quiz.entity.QuizRubricItem;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuizAssessorTest {

	private static final QuizProperties.Assessment RULE = new QuizProperties.Assessment(3, 0.8, 0.6);

	@Test
	void 전체_득점률과_모든_개념_득점률이_기준_이상이면_다음_단계_준비() {
		var result = QuizAssessor.assess(List.of(mc("vlan", true), mc("vlan", true), mc("acl", true), mc("acl", true)),
				RULE);

		assertThat(result.status()).isEqualTo(QuizAssessor.Status.READY);
		assertThat(result.score()).isEqualTo(4);
		assertThat(result.byConcept()).noneMatch(QuizAssessor.ConceptStat::weak);
	}

	@Test
	void 득점률이_낮은_개념이_있으면_보완_필요와_보완_개념() {
		var result = QuizAssessor.assess(List.of(mc("vlan", true), mc("vlan", true), mc("acl", false), mc("acl", false)),
				RULE);

		assertThat(result.status()).isEqualTo(QuizAssessor.Status.NOT_READY);
		assertThat(result.byConcept()).filteredOn(QuizAssessor.ConceptStat::weak)
				.extracting(QuizAssessor.ConceptStat::conceptKey).containsExactly("acl");
	}

	@Test
	void 문항이_적으면_다_맞아도_추가_확인_필요() {
		var result = QuizAssessor.assess(List.of(mc("vlan", true), mc("vlan", true)), RULE);

		assertThat(result.status()).isEqualTo(QuizAssessor.Status.NEEDS_CHECK);
		assertThat(result.reasons()).anyMatch(reason -> reason.contains("판단하기에 적어요"));
	}

	@Test
	void 채점_대기나_한_모델만_채점한_서술형이_있으면_추가_확인_필요_점수에는_넣지_않는다() {
		QuizQuestion essay = QuizQuestion.essay(1L, 3, "서술", List.of(QuizRubricItem.of("기준", 4),
				QuizRubricItem.of("기준2", 2)), "모범", null, null, List.of(QuizConcept.of("vlan", "VLAN")));
		var pending = QuizAssessor.assess(List.of(mc("vlan", true), mc("vlan", true), mc("vlan", true),
				new QuizAssessor.Graded(essay, GradingStatus.PENDING, 0, 6, GradingNotes.NOT_CONFIGURED)), RULE);
		var single = QuizAssessor.assess(List.of(mc("vlan", true), mc("vlan", true), mc("vlan", true),
				new QuizAssessor.Graded(essay, GradingStatus.GRADED, 6, 6, GradingNotes.SINGLE_MODEL)), RULE);

		assertThat(pending.status()).isEqualTo(QuizAssessor.Status.NEEDS_CHECK);
		assertThat(pending.maxScore()).isEqualTo(3);
		assertThat(pending.byConcept().get(0).pendingCount()).isEqualTo(1);
		assertThat(single.status()).isEqualTo(QuizAssessor.Status.NEEDS_CHECK);
		assertThat(single.reasons()).anyMatch(reason -> reason.contains("하나만"));
	}

	private static QuizAssessor.Graded mc(String concept, boolean correct) {
		QuizQuestion question = QuizQuestion.create(1L, 0, "문제", List.of("A", "B", "C"), 0, null,
				List.of(QuizConcept.of(concept, concept.toUpperCase())));
		return new QuizAssessor.Graded(question, GradingStatus.GRADED, correct ? 1 : 0, 1, null);
	}
}
