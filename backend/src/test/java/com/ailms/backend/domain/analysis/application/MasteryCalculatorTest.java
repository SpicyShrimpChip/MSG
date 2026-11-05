package com.ailms.backend.domain.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.ailms.backend.domain.analysis.application.MasteryCalculator.Level;
import com.ailms.backend.domain.analysis.entity.UnderstandingStatus;
import org.junit.jupiter.api.Test;

// docs/frontend-requests.md 5-10절의 계산 예시와 같은 값으로 확인한다 (초기값: 퀴즈 1.0·대화 0.5, 기준 3.0, 경계 0.4·0.7)
class MasteryCalculatorTest {

	private final MasteryCalculator calculator = new MasteryCalculator(
			new MasteryProperties("1", 1.0, 0.5, 1.0, 0.3, 0.0, 3.0, 0.4, 0.7));

	@Test
	void 퀴즈_3문제_중_2개_정답은_보통이다() {
		var result = calculator.calculate(3, 2, 3, 0, null);

		assertThat(result.level()).isEqualTo(Level.NORMAL);
		assertThat(result.score()).isCloseTo(0.6, within(1e-9)); // (2+1)/(3+2)
		assertThat(result.evidenceWeight()).isEqualTo(3.0);
		assertThat(result.quizGradedCount()).isEqualTo(3);
		assertThat(result.conversationCount()).isZero();
		assertThat(result.formulaVersion()).isEqualTo("1");
	}

	@Test
	void 퀴즈_4문제_모두_정답은_강점이다() {
		var result = calculator.calculate(4, 4, 4, 0, null);

		assertThat(result.level()).isEqualTo(Level.STRONG);
		assertThat(result.score()).isCloseTo(5.0 / 6.0, within(1e-9));
	}

	@Test
	void 퀴즈_4문제_중_1개_정답과_대화_이해_못_함_2건은_보완_필요다() {
		var result = calculator.calculate(4, 1, 4, 2, UnderstandingStatus.NOT_UNDERSTOOD);

		assertThat(result.level()).isEqualTo(Level.NEEDS_WORK);
		assertThat(result.score()).isCloseTo(2.0 / 7.0, within(1e-9)); // (1+0+1)/(4+1+2)
		assertThat(result.evidenceWeight()).isEqualTo(5.0);
		assertThat(result.conversationStatus()).isEqualTo(UnderstandingStatus.NOT_UNDERSTOOD);
	}

	@Test
	void 대화만_두_건이면_근거_부족이고_점수가_없다() {
		var result = calculator.calculate(0, 0, 0, 2, UnderstandingStatus.NOT_UNDERSTOOD);

		assertThat(result.level()).isEqualTo(Level.INSUFFICIENT_EVIDENCE);
		assertThat(result.score()).isNull();
		assertThat(result.evidenceWeight()).isEqualTo(1.0);
		assertThat(result.quizNeeded()).isEqualTo(2); // 기준 3.0까지 2.0 남음 ÷ 퀴즈 1.0
	}

	@Test
	void 퀴즈_1문제_정답만으로는_근거_부족이다() {
		var result = calculator.calculate(1, 1, 1, 0, null);

		assertThat(result.level()).isEqualTo(Level.INSUFFICIENT_EVIDENCE);
		assertThat(result.score()).isNull();
		assertThat(result.quizNeeded()).isEqualTo(2);
	}

	@Test
	void 남은_무게가_소수면_퀴즈_문제_수를_올림한다() {
		// 퀴즈 1문제 + 대화 1건 = 1.5, 기준까지 1.5 남음 → 2문제
		var result = calculator.calculate(1, 1, 1, 1, UnderstandingStatus.UNDERSTOOD);

		assertThat(result.level()).isEqualTo(Level.INSUFFICIENT_EVIDENCE);
		assertThat(result.quizNeeded()).isEqualTo(2);
	}

	@Test
	void 근거가_충분하면_더_풀_퀴즈_수가_없다() {
		var result = calculator.calculate(3, 3, 3, 0, null);

		assertThat(result.level()).isNotEqualTo(Level.INSUFFICIENT_EVIDENCE);
		assertThat(result.quizNeeded()).isNull();
	}

	@Test
	void 근거가_하나도_없으면_근거_부족이다() {
		var result = calculator.calculate(0, 0, 0, 0, null);

		assertThat(result.level()).isEqualTo(Level.INSUFFICIENT_EVIDENCE);
		assertThat(result.evidenceWeight()).isZero();
		assertThat(result.quizRate()).isNull();
		assertThat(result.quizNeeded()).isEqualTo(3);
	}

	@Test
	void 유형_A는_대화만으로도_계산된다() {
		// 대화 6건 이해 → 무게 3.0, (3+1)/(3+2) = 0.8
		var result = calculator.calculate(0, 0, 0, 6, UnderstandingStatus.UNDERSTOOD);

		assertThat(result.level()).isEqualTo(Level.STRONG);
		assertThat(result.score()).isCloseTo(0.8, within(1e-9));
	}

	@Test
	void 의견이_갈리거나_근거_부족인_대화는_세지_않는다() {
		var disagreement = calculator.calculate(3, 3, 3, 4, null);
		var insufficient = calculator.calculate(3, 3, 3, 4, UnderstandingStatus.INSUFFICIENT_EVIDENCE);

		assertThat(disagreement.conversationCount()).isZero();
		assertThat(disagreement.evidenceWeight()).isEqualTo(3.0);
		assertThat(insufficient.conversationCount()).isZero();
		assertThat(insufficient.score()).isEqualTo(disagreement.score());
	}

	@Test
	void 부분_이해_대화는_재설명_값으로_센다() {
		// 퀴즈 없이 대화 6건 부분 이해 → 무게 3.0, (6*0.5*0.3+1)/(3+2) = 0.38
		var result = calculator.calculate(0, 0, 0, 6, UnderstandingStatus.PARTIAL);

		assertThat(result.score()).isCloseTo(0.38, within(1e-9));
		assertThat(result.level()).isEqualTo(Level.NEEDS_WORK);
	}

	@Test
	void 근거_부족_기준과_단계_경계는_그_값부터_넘어간다() {
		// 기준 3.0 딱 맞으면 점수를 낸다
		assertThat(calculator.calculate(3, 0, 3, 0, null).level()).isEqualTo(Level.NEEDS_WORK);
		// 점수 0.4는 보통 (3문제 중 1개: (1+1)/(3+2))
		assertThat(calculator.calculate(3, 1, 3, 0, null).level()).isEqualTo(Level.NORMAL);
		// 점수 0.7은 강점 (8문제 중 6개: (6+1)/(8+2))
		assertThat(calculator.calculate(8, 6, 8, 0, null).level()).isEqualTo(Level.STRONG);
		// 기준 바로 아래 (2문제)는 근거 부족
		assertThat(calculator.calculate(2, 2, 2, 0, null).level()).isEqualTo(Level.INSUFFICIENT_EVIDENCE);
	}

	@Test
	void 주관식_부분_점수는_득점률로_센다() {
		// 3문제, 만점 합 6 중 3점 → 득점률 0.5, (3*0.5+1)/(3+2) = 0.5
		var result = calculator.calculate(3, 3, 6, 0, null);

		assertThat(result.quizRate()).isCloseTo(0.5, within(1e-9));
		assertThat(result.score()).isCloseTo(0.5, within(1e-9));
		assertThat(result.level()).isEqualTo(Level.NORMAL);
	}
}
