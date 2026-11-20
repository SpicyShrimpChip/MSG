package com.ailms.backend.global.llm;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자별 24시간 AI 이용 횟수(분석 시작·퀴즈 만들기·AI 채점 실행). 한도 확인과 기록은 부르는 쪽이 사용자 행을 잠근
 * 같은 트랜잭션에서 한다. 실행 기록(analysis_run 등)은 과목·제출을 지우면 함께 지워져서, 횟수는 users에만 연결한
 * 이 기록으로 센다(V16). 하루가 지난 기록은 주기 작업이 지운다.
 */
@Slf4j
@Component
public class UserAiUsage {

	public enum Kind {
		ANALYSIS_RUN, QUIZ_GENERATION, QUIZ_GRADING
	}

	// 24시간 창보다 넉넉히 남긴다
	private static final long KEEP_HOURS = 48;

	private final UserAiUsageRecordRepository repository;
	private final Clock clock;

	public UserAiUsage(UserAiUsageRecordRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	/** 최근 24시간 동안 이 종류를 몇 번 썼는지 */
	public long countLast24Hours(Long userId, Kind kind) {
		return repository.countByUserIdAndKindAndCreatedAtAfter(userId, kind, now().minusHours(24));
	}

	/** 한 번 썼다고 남긴다 (한도를 확인한 같은 트랜잭션 안) */
	public void record(Long userId, Kind kind) {
		repository.save(UserAiUsageRecord.of(userId, kind, now()));
	}

	@Scheduled(fixedDelayString = "${app.ai-daily-limit.user-usage-cleanup-interval:6h}",
			initialDelayString = "${app.ai-daily-limit.user-usage-cleanup-interval:6h}")
	@Transactional
	public int purgeOld() {
		int deleted = repository.deleteOlderThan(now().minusHours(KEEP_HOURS));
		if (deleted > 0) {
			log.info("지난 사용자별 AI 이용 기록 {}건을 지웠다", deleted);
		}
		return deleted;
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
	}
}
