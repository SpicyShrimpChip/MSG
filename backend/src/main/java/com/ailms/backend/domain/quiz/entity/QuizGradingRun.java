package com.ailms.backend.domain.quiz.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 제출 한 번의 AI 채점 실행 (quiz_grading_run). 모델을 부르기 전에 만들어(예약) 24시간 한도를 세고,
 * 같은 제출의 채점이 진행 중이면 다른 채점 요청을 막는다. 마감이 지나면 결과를 저장하지 않는다.
 */
@Entity
@Table(name = "quiz_grading_run")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizGradingRun {

	public enum Kind {
		INITIAL, REGRADE
	}

	public enum Status {
		RUNNING, SUCCEEDED, FAILED, TIMED_OUT
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long userId;

	@Column(nullable = false)
	private Long submissionId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private Kind kind;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Status status;

	@Column(nullable = false)
	private int answerCount;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	@Column(nullable = false)
	private LocalDateTime deadlineAt;

	private LocalDateTime finishedAt;

	public static QuizGradingRun start(Long userId, Long submissionId, Kind kind, int answerCount, LocalDateTime now,
			LocalDateTime deadlineAt) {
		QuizGradingRun run = new QuizGradingRun();
		run.userId = userId;
		run.submissionId = submissionId;
		run.kind = kind;
		run.status = Status.RUNNING;
		run.answerCount = answerCount;
		run.createdAt = now;
		run.deadlineAt = deadlineAt;
		return run;
	}

	public boolean isActiveAt(LocalDateTime now) {
		return status == Status.RUNNING && now.isBefore(deadlineAt);
	}

	// 진행 중일 때만 끝낸다 (이미 끝난 실행을 다시 바꾸지 않는다)
	public boolean finish(Status result, LocalDateTime now) {
		if (status != Status.RUNNING) {
			return false;
		}
		this.status = result;
		this.finishedAt = now;
		return true;
	}
}
