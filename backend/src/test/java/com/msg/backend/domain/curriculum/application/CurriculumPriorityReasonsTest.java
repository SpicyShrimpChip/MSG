package com.msg.backend.domain.curriculum.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.msg.backend.domain.analysis.entity.ConceptConsensus;
import com.msg.backend.domain.analysis.entity.ConsensusOutcome;
import com.msg.backend.domain.analysis.entity.UnderstandingStatus;
import org.junit.jupiter.api.Test;

// 커리큘럼 개념을 먼저 공부하는 이유: 저장된 결과(사전평가·퀴즈·대화 분석)로 만든 문장
class CurriculumPriorityReasonsTest {

	@Test
	void 사전평가_결과를_먼저_쓰고_대화_분석은_AI_추정으로_따로_밝힌다() {
		ConceptConsensus agreed = ConceptConsensus.of(1L, "rsa", "RSA", ConsensusOutcome.MAJORITY,
				UnderstandingStatus.NOT_UNDERSTOOD, 3, 2, 3);
		var reasons = CurriculumFlowBuilder.priorityReasons(true, 40, "weak", 60, "weak", null, agreed);

		assertThat(reasons).containsExactly("사전평가 정답률 40%로 기준(60%)보다 낮아요.",
				"사전평가 뒤 푼 퀴즈의 득점률이 기준보다 낮아요.",
				"대화 분석(AI 추정, 퀴즈 결과와 따로): 모델 3개 중 2개가 '이해 부족'으로 봤어요.");
	}

	@Test
	void 사전평가_전이거나_의견이_갈리면_그렇다고_알린다() {
		ConceptConsensus split = ConceptConsensus.of(1L, "rsa", "RSA", ConsensusOutcome.DISAGREEMENT, null, 3, 1, 3);
		var reasons = CurriculumFlowBuilder.priorityReasons(false, null, "not_taken", 60, "not_started", null, split);

		assertThat(reasons.get(0)).contains("아직 사전평가를 보지 않아");
		assertThat(reasons.get(1)).contains("의견이 서로 달라");
		assertThat(CurriculumFlowBuilder.priorityReasons(true, 90, "ok", 60, "ok", null, null))
				.containsExactly("사전평가 정답률 90%로 기준(60%) 이상이에요.");
	}
}
