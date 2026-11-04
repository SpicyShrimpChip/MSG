package com.ailms.backend.domain.study.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.ailms.backend.domain.study.application.ConceptStudyRules.CheckResult;
import com.ailms.backend.domain.study.application.ConceptStudyRules.Status;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * 개념 학습 상태 판정 규칙. 기준은 퀴즈·커리큘럼과 같은 개념 기준 정답률 0.6, 판단에 필요한 채점 문제 2개.
 */
class ConceptStudyRulesTest {

	private static final double MIN_RATE = 0.6;
	private static final int MIN_GRADED = 2;
	private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 5, 10, 0);

	private static CheckResult result(LocalDateTime at, int graded, int score, int max, int waiting) {
		return new CheckResult(at, graded, score, max, waiting);
	}

	private static Status resolve(LocalDateTime studied, CheckResult latest) {
		return ConceptStudyRules.resolve(studied, latest, MIN_RATE, MIN_GRADED);
	}

	@Test
	void 기록이_없으면_학습_전_완료만_있으면_학습_완료() {
		assertThat(resolve(null, null)).isEqualTo(Status.NOT_STUDIED);
		assertThat(resolve(T0, null)).isEqualTo(Status.STUDIED);
	}

	@Test
	void 학습_뒤_확인_퀴즈가_기준_이상이면_이해_확인_미만이면_복습_필요() {
		assertThat(resolve(T0, result(T0.plusMinutes(5), 3, 2, 3, 0))).isEqualTo(Status.UNDERSTOOD);
		assertThat(resolve(T0, result(T0.plusMinutes(5), 3, 3, 5, 0))).isEqualTo(Status.UNDERSTOOD);
		assertThat(resolve(T0, result(T0.plusMinutes(5), 3, 1, 3, 0))).isEqualTo(Status.REVIEW_NEEDED);
	}

	@Test
	void 채점_대기나_문항_부족은_틀린_답으로_세지_않고_추가_확인_필요() {
		assertThat(resolve(T0, result(T0.plusMinutes(5), 2, 0, 2, 1))).isEqualTo(Status.NEEDS_CHECK);
		assertThat(resolve(T0, result(T0.plusMinutes(5), 1, 0, 1, 0))).isEqualTo(Status.NEEDS_CHECK);
		assertThat(resolve(T0, result(T0.plusMinutes(5), 0, 0, 0, 2))).isEqualTo(Status.NEEDS_CHECK);
	}

	@Test
	void 복습_뒤_다시_완료하면_예전_결과보다_새_학습이_앞서_학습_완료로_돌아간다() {
		CheckResult failed = result(T0.plusMinutes(5), 3, 0, 3, 0);
		assertThat(resolve(T0, failed)).isEqualTo(Status.REVIEW_NEEDED);
		assertThat(resolve(T0.plusMinutes(30), failed)).isEqualTo(Status.STUDIED);
	}

	@Test
	void 학습_기록_없이_푼_확인_퀴즈도_결과로_판정한다() {
		assertThat(resolve(null, result(T0, 2, 2, 2, 0))).isEqualTo(Status.UNDERSTOOD);
	}

	@Test
	void 이유_문장은_코드가_센_숫자를_쓴다() {
		CheckResult failed = result(T0, 3, 1, 3, 0);
		assertThat(ConceptStudyRules.reason(Status.REVIEW_NEEDED, failed, MIN_RATE, MIN_GRADED, 1))
				.contains("1/3점(33%)", "기준(60%)");
		assertThat(ConceptStudyRules.reason(Status.NEEDS_CHECK, result(T0, 2, 0, 2, 1), MIN_RATE, MIN_GRADED, 1))
				.contains("확인이 필요한 답이 1개");
	}
}
