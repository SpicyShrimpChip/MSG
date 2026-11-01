package com.ailms.backend.domain.analysis.application;

import static com.ailms.backend.domain.analysis.entity.UnderstandingStatus.INSUFFICIENT_EVIDENCE;
import static com.ailms.backend.domain.analysis.entity.UnderstandingStatus.NOT_UNDERSTOOD;
import static com.ailms.backend.domain.analysis.entity.UnderstandingStatus.PARTIAL;
import static com.ailms.backend.domain.analysis.entity.UnderstandingStatus.UNDERSTOOD;
import static org.assertj.core.api.Assertions.assertThat;

import com.ailms.backend.domain.analysis.entity.ConsensusOutcome;
import java.util.List;
import org.junit.jupiter.api.Test;

// 개념별 모델 의견 비교 규칙
class ConsensusCalculatorTest {

	@Test
	void 세_모델이_같으면_전원_일치() {
		assertThat(ConsensusCalculator.compare(List.of(PARTIAL, PARTIAL, PARTIAL)))
				.isEqualTo(new ConsensusCalculator.Result(ConsensusOutcome.UNANIMOUS, PARTIAL, 3, 3));
	}

	@Test
	void 세_모델_중_둘이_같으면_다수_의견() {
		assertThat(ConsensusCalculator.compare(List.of(UNDERSTOOD, PARTIAL, UNDERSTOOD)))
				.isEqualTo(new ConsensusCalculator.Result(ConsensusOutcome.MAJORITY, UNDERSTOOD, 3, 2));
	}

	@Test
	void 셋_모두_다르면_의견_불일치이고_일치한_상태가_없다() {
		assertThat(ConsensusCalculator.compare(List.of(UNDERSTOOD, PARTIAL, NOT_UNDERSTOOD)))
				.isEqualTo(new ConsensusCalculator.Result(ConsensusOutcome.DISAGREEMENT, null, 3, 1));
	}

	@Test
	void 한_모델이_실패해_둘만_판단하면_판단한_수로_비교한다() {
		assertThat(ConsensusCalculator.compare(List.of(PARTIAL, PARTIAL)))
				.isEqualTo(new ConsensusCalculator.Result(ConsensusOutcome.UNANIMOUS, PARTIAL, 2, 2));
		assertThat(ConsensusCalculator.compare(List.of(PARTIAL, UNDERSTOOD)))
				.isEqualTo(new ConsensusCalculator.Result(ConsensusOutcome.DISAGREEMENT, null, 2, 1));
	}

	@Test
	void 하나만_판단하면_비교하지_않는다() {
		assertThat(ConsensusCalculator.compare(List.of(NOT_UNDERSTOOD)))
				.isEqualTo(new ConsensusCalculator.Result(ConsensusOutcome.SINGLE_MODEL, NOT_UNDERSTOOD, 1, 1));
	}

	@Test
	void 판단한_모델이_없으면_결과_없음() {
		assertThat(ConsensusCalculator.compare(List.of()))
				.isEqualTo(new ConsensusCalculator.Result(ConsensusOutcome.NO_RESULT, null, 0, 0));
	}

	@Test
	void 근거_부족은_근거_부족끼리만_같은_의견이다() {
		// 근거 부족 둘 + 이해 부족 하나 → 다수 의견은 근거 부족 (이해 부족으로 바꾸지 않는다)
		assertThat(ConsensusCalculator.compare(List.of(INSUFFICIENT_EVIDENCE, NOT_UNDERSTOOD, INSUFFICIENT_EVIDENCE)))
				.isEqualTo(new ConsensusCalculator.Result(ConsensusOutcome.MAJORITY, INSUFFICIENT_EVIDENCE, 3, 2));
	}
}
