package com.msg.backend.domain.analysis.application;

import com.msg.backend.domain.analysis.dao.ConceptMasteryRepository;
import com.msg.backend.domain.analysis.dto.AnalysisRunResponse;
import com.msg.backend.domain.analysis.entity.AnalysisRunStatus;
import com.msg.backend.domain.analysis.entity.ConceptMastery;
import com.msg.backend.domain.course.entity.Course;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 개념별 숙련도 기록(변화 추이). 대시보드가 최근 분석으로 숙련도를 계산할 때, 그 개념의 마지막 기록과 단계·근거가
 * 달라졌으면 한 줄을 남기고, 응답의 mastery.history에 기록을 붙인다. 같은 값이면 남기지 않아 열 때마다 쌓이지 않는다.
 *
 * <p>학습이 아니라 모델 장애 때문에 생긴 변화는 남기지 않는다(#209). 판단한 모델이 없는 분석(failed)은 기록하지 않고,
 * 일부 모델이 실패한 분석(partial)은 대화 쪽 근거(대화 수·상태)만 바뀌었으면 기록하지 않는다. 빠진 모델의 근거가 빠져
 * 대화 수·상태가 달라졌을 수 있어서 학습 변화와 구분할 수 없기 때문이다. 퀴즈·산식이 바뀌었거나 첫 기록이면 남긴다.
 */
@Service
@RequiredArgsConstructor
public class MasteryHistoryService {

	static final int HISTORY_LIMIT = 30;

	private final ConceptMasteryRepository repository;
	private final Clock clock;

	// 최근 분석: 바뀐 개념을 기록하고 기록을 붙인다
	@Transactional
	public AnalysisRunResponse recordAndAttach(Long userId, AnalysisRunResponse response) {
		return attach(userId, response, true);
	}

	// 지난 분석 하나: 기록하지 않고(지금 값이 아니므로) 지금까지의 기록만 붙인다
	@Transactional(readOnly = true)
	public AnalysisRunResponse attachOnly(Long userId, AnalysisRunResponse response) {
		return attach(userId, response, false);
	}

	private AnalysisRunResponse attach(Long userId, AnalysisRunResponse response, boolean record) {
		Map<String, List<ConceptMastery>> byConcept = repository
				.findAllByUserIdAndCourseIdOrderByIdAsc(userId, response.courseId()).stream()
				.collect(Collectors.groupingBy(ConceptMastery::getConceptKey));
		LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
		List<AnalysisRunResponse.ConceptResult> concepts = new ArrayList<>();
		for (AnalysisRunResponse.ConceptResult concept : response.concepts()) {
			AnalysisRunResponse.Mastery mastery = concept.mastery();
			if (mastery == null) {
				concepts.add(concept);
				continue;
			}
			String key = Course.nameKey(concept.concept());
			List<ConceptMastery> rows = new ArrayList<>(byConcept.getOrDefault(key, List.of()));
			if (record && shouldRecord(response.status(), rows.isEmpty() ? null : rows.get(rows.size() - 1), mastery)) {
				rows.add(repository.save(ConceptMastery.create(userId, response.courseId(), response.id(), key,
						concept.concept(), mastery.level(), mastery.score(), mastery.evidenceWeight(),
						mastery.quizGradedCount(), mastery.quizRate(), mastery.conversationCount(),
						mastery.conversationStatus(), mastery.formulaVersion(), now)));
			}
			List<AnalysisRunResponse.MasteryRecord> history = rows
					.subList(Math.max(0, rows.size() - HISTORY_LIMIT), rows.size()).stream()
					.map(row -> new AnalysisRunResponse.MasteryRecord(row.getRecordedAt(), row.getLevel(),
							row.getQuizGradedCount(), row.getQuizRate(), row.getConversationCount(),
							row.getConversationStatus(), row.getFormulaVersion()))
					.toList();
			concepts.add(concept.withMastery(mastery.withHistory(history)));
		}
		return response.withConcepts(concepts);
	}

	// 기록할지: 판단한 모델이 있는 분석에서 값이 바뀌었을 때. 일부 모델이 실패한 분석은 퀴즈·산식이 바뀐 때만(대화 쪽 변화는 모델 장애일 수 있다)
	static boolean shouldRecord(String runStatus, ConceptMastery last, AnalysisRunResponse.Mastery now) {
		boolean completed = AnalysisRunStatus.COMPLETED.apiValue().equals(runStatus);
		boolean partial = AnalysisRunStatus.PARTIAL.apiValue().equals(runStatus);
		if (!completed && !partial) {
			return false;
		}
		if (last == null) {
			return true;
		}
		return completed ? changed(last, now) : quizOrFormulaChanged(last, now);
	}

	// 단계나 근거(퀴즈 문제 수·득점률, 대화 수·상태, 산식 버전)가 하나라도 다르면 새 기록
	static boolean changed(ConceptMastery last, AnalysisRunResponse.Mastery now) {
		return !last.getLevel().equals(now.level())
				|| last.getQuizGradedCount() != now.quizGradedCount()
				|| !sameRate(last.getQuizRate(), now.quizRate())
				|| last.getConversationCount() != now.conversationCount()
				|| !Objects.equals(last.getConversationStatus(), now.conversationStatus())
				|| !last.getFormulaVersion().equals(now.formulaVersion());
	}

	static boolean quizOrFormulaChanged(ConceptMastery last, AnalysisRunResponse.Mastery now) {
		return last.getQuizGradedCount() != now.quizGradedCount()
				|| !sameRate(last.getQuizRate(), now.quizRate())
				|| !last.getFormulaVersion().equals(now.formulaVersion());
	}

	private static boolean sameRate(Double a, Double b) {
		if (a == null || b == null) {
			return a == null && b == null;
		}
		return Math.abs(a - b) < 1e-9;
	}
}
