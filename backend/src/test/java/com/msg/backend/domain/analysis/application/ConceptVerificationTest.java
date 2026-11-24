package com.msg.backend.domain.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.msg.backend.domain.analysis.entity.ConsensusOutcome;
import com.msg.backend.domain.analysis.entity.UnderstandingStatus;
import com.msg.backend.global.common.VerificationStatus;
import org.junit.jupiter.api.Test;

// 개념 검증 상태: 모델 의견 비교와 실제 퀴즈 결과 (코드 규칙)
class ConceptVerificationTest {

	private static final ConceptVerification.Quiz HIGH = new ConceptVerification.Quiz(3, 3, 3, 0);
	private static final ConceptVerification.Quiz LOW = new ConceptVerification.Quiz(3, 1, 3, 0);

	private static ConceptVerification.Result of(ConsensusOutcome outcome, UnderstandingStatus status, int judged,
			int agree, int called, int misconceptions, ConceptVerification.Quiz quiz) {
		return ConceptVerification.evaluate(new ConceptVerification.Input(outcome, status, judged, agree, called,
				misconceptions, quiz), 2, 0.6);
	}

	@Test
	void 세_모델이_일치해도_퀴즈로_확인하기_전에는_일부_검증이다() {
		var result = of(ConsensusOutcome.UNANIMOUS, UnderstandingStatus.UNDERSTOOD, 3, 3, 3, 0, null);
		assertThat(result.status()).isEqualTo(VerificationStatus.PARTIAL);
		assertThat(result.reasons().get(0)).contains("정확도가 아니에요");
		assertThat(result.quizComparison()).isEqualTo("no_quiz");
		assertThat(result.recommendCheckQuiz()).isTrue();
	}

	@Test
	void 일치한_판단이_확인_퀴즈와_같은_방향이면_검증_완료() {
		var understood = of(ConsensusOutcome.UNANIMOUS, UnderstandingStatus.UNDERSTOOD, 3, 3, 3, 0, HIGH);
		assertThat(understood.status()).isEqualTo(VerificationStatus.VERIFIED);
		assertThat(understood.recommendCheckQuiz()).isFalse();
		var weak = of(ConsensusOutcome.MAJORITY, UnderstandingStatus.NOT_UNDERSTOOD, 3, 2, 3, 0, LOW);
		assertThat(weak.status()).isEqualTo(VerificationStatus.VERIFIED);
		assertThat(weak.reasons().get(0)).contains("3개 중 2개");
	}

	@Test
	void 퀴즈와_반대면_추가_확인_필요() {
		var result = of(ConsensusOutcome.UNANIMOUS, UnderstandingStatus.NOT_UNDERSTOOD, 3, 3, 3, 0, HIGH);
		assertThat(result.status()).isEqualTo(VerificationStatus.NEEDS_CHECK);
		assertThat(result.quizComparison()).isEqualTo("contradicts");
		assertThat(result.holdReason()).contains("퀴즈 결과");
	}

	@Test
	void 세_모델이_모두_다르면_추가_확인_필요() {
		var result = of(ConsensusOutcome.DISAGREEMENT, null, 3, 1, 3, 0, null);
		assertThat(result.status()).isEqualTo(VerificationStatus.NEEDS_CHECK);
		assertThat(result.holdReason()).contains("서로 달라요");
	}

	@Test
	void 모델_하나만_판단하거나_일부가_실패하면_검증_완료로_보지_않는다() {
		var single = of(ConsensusOutcome.SINGLE_MODEL, UnderstandingStatus.UNDERSTOOD, 1, 1, 1, 0, HIGH);
		assertThat(single.status()).isEqualTo(VerificationStatus.PARTIAL);
		var failedOne = of(ConsensusOutcome.UNANIMOUS, UnderstandingStatus.UNDERSTOOD, 2, 2, 3, 0, HIGH);
		assertThat(failedOne.status()).isEqualTo(VerificationStatus.PARTIAL);
		assertThat(failedOne.reasons()).anyMatch(reason -> reason.contains("3개 중 2개만"));
	}

	@Test
	void 근거_부족은_모델이_일치해도_판단_보류이고_퀴즈_결과는_따로_알린다() {
		var result = of(ConsensusOutcome.UNANIMOUS, UnderstandingStatus.INSUFFICIENT_EVIDENCE, 3, 3, 3, 0, HIGH);
		assertThat(result.status()).isEqualTo(VerificationStatus.INSUFFICIENT);
		assertThat(result.holdReason()).contains("근거 부족은 이해 부족이 아니에요");
		assertThat(result.reasons()).anyMatch(reason -> reason.contains("100%"));
	}

	@Test
	void 판단한_모델이_없으면_검증_실패() {
		assertThat(of(ConsensusOutcome.NO_RESULT, null, 0, 0, 3, 0, null).status()).isEqualTo(VerificationStatus.FAILED);
	}

	@Test
	void 오개념_후보는_퀴즈로_확인하기_전까지_추가_확인_필요() {
		var result = of(ConsensusOutcome.MAJORITY, UnderstandingStatus.PARTIAL, 3, 2, 3, 2, null);
		assertThat(result.status()).isEqualTo(VerificationStatus.NEEDS_CHECK);
		assertThat(result.holdReason()).contains("오개념 후보");
	}

	@Test
	void 채점_대기가_있거나_채점된_문제가_적으면_퀴즈로_비교하지_않는다() {
		var pending = of(ConsensusOutcome.UNANIMOUS, UnderstandingStatus.UNDERSTOOD, 3, 3, 3, 0,
				new ConceptVerification.Quiz(3, 3, 3, 1));
		assertThat(pending.quizComparison()).isEqualTo("no_quiz");
		var few = of(ConsensusOutcome.UNANIMOUS, UnderstandingStatus.UNDERSTOOD, 3, 3, 3, 0,
				new ConceptVerification.Quiz(1, 1, 1, 0));
		assertThat(few.status()).isEqualTo(VerificationStatus.PARTIAL);
	}
}
