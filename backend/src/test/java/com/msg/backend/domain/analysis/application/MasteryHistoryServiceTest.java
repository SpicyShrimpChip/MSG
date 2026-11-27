package com.msg.backend.domain.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.msg.backend.domain.analysis.dto.AnalysisRunResponse;
import com.msg.backend.domain.analysis.entity.ConceptMastery;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

// 숙련도 기록을 남길지: 학습이 아니라 모델 장애 때문에 생긴 변화는 남기지 않는다 (#209)
class MasteryHistoryServiceTest {

	private static final ConceptMastery LAST = ConceptMastery.create(1L, 1L, 1L, "교착 상태", "교착 상태",
			"normal", 0.6, 3.5, 3, 0.67, 1, "understood", "1", LocalDateTime.of(2026, 11, 20, 10, 0));

	private static AnalysisRunResponse.Mastery mastery(String level, int quizGradedCount, Double quizRate,
			int conversationCount, String conversationStatus) {
		return new AnalysisRunResponse.Mastery(level, null, 0, quizGradedCount, quizRate, conversationCount,
				conversationStatus, "1", null, List.of());
	}

	@Test
	void 판단한_모델이_없거나_진행_중인_분석은_기록하지_않는다() {
		var changed = mastery("insufficient_evidence", 3, 0.67, 0, null);

		assertThat(MasteryHistoryService.shouldRecord("failed", LAST, changed)).isFalse();
		assertThat(MasteryHistoryService.shouldRecord("failed", null, changed)).isFalse();
		assertThat(MasteryHistoryService.shouldRecord("running", LAST, changed)).isFalse();
	}

	@Test
	void 모델이_모두_판단한_분석은_무엇이든_바뀌면_기록한다() {
		assertThat(MasteryHistoryService.shouldRecord("completed", null, mastery("normal", 3, 0.67, 1, "understood")))
				.isTrue();
		assertThat(MasteryHistoryService.shouldRecord("completed", LAST, mastery("normal", 3, 0.67, 1, "understood")))
				.isFalse();
		assertThat(MasteryHistoryService.shouldRecord("completed", LAST, mastery("normal", 3, 0.67, 0, null)))
				.isTrue();
	}

	@Test
	void 일부_모델이_실패한_분석은_퀴즈가_바뀌었거나_첫_기록일_때만_기록한다() {
		// 대화 쪽 근거만 바뀜: 빠진 모델 때문일 수 있다
		assertThat(MasteryHistoryService.shouldRecord("partial", LAST,
				mastery("insufficient_evidence", 3, 0.67, 0, null))).isFalse();
		// 퀴즈를 더 풀었음: 학습 변화이므로 남긴다
		assertThat(MasteryHistoryService.shouldRecord("partial", LAST, mastery("strong", 5, 0.8, 0, null))).isTrue();
		assertThat(MasteryHistoryService.shouldRecord("partial", null, mastery("normal", 3, 0.67, 1, "understood")))
				.isTrue();
	}

	@Test
	void 산식_버전만_바뀌어도_기록한다() {
		var v2 = new AnalysisRunResponse.Mastery("normal", null, 0, 3, 0.67, 1, "understood", "2", null, List.of());

		assertThat(MasteryHistoryService.shouldRecord("completed", LAST, v2)).isTrue();
		assertThat(MasteryHistoryService.shouldRecord("partial", LAST, v2)).isTrue();
	}
}
