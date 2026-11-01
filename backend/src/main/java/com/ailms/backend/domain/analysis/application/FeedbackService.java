package com.ailms.backend.domain.analysis.application;

import com.ailms.backend.domain.analysis.dao.AnalysisRunRepository;
import com.ailms.backend.domain.analysis.dao.ConceptFeedbackRepository;
import com.ailms.backend.domain.analysis.dto.FeedbackRequest;
import com.ailms.backend.domain.analysis.dto.FeedbackResponse;
import com.ailms.backend.domain.analysis.entity.ConceptFeedback;
import com.ailms.backend.domain.analysis.entity.FeedbackType;
import com.ailms.backend.domain.course.dao.CourseRepository;
import com.ailms.backend.domain.course.entity.Course;
import com.ailms.backend.global.error.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 학습 모드 피드백 (이미 알아요 / 더 공부할래요). 과목·개념마다 하나이고, 다시 보내면 바꾸고, 지우면 취소된다.
 * AI 판단·숙련도는 바꾸지 않는다: 이미 알아요는 확인 퀴즈를 권하고, 더 공부할래요는 추천 순서만 앞당긴다.
 * 사용자 본인이 쓴 기록이라 AI 대화 분석 동의와 상관없이 남길 수 있고, 동의를 철회해도 지우지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedbackService {

	static final String NOT_FOUND_MESSAGE = "피드백을 찾을 수 없어요.";
	static final String UNKNOWN_CONCEPT_MESSAGE = "과목 키워드에 있는 개념만 고를 수 있어요.";

	private final ConceptFeedbackRepository feedbackRepository;
	private final CourseRepository courseRepository;
	private final AnalysisRunRepository runRepository;
	private final Clock clock;

	public List<FeedbackResponse> getFeedback(Long userId, Long courseId) {
		findCourse(userId, courseId);
		return feedbackRepository.findAllByUserIdAndCourseIdOrderByIdAsc(userId, courseId).stream()
				.map(FeedbackResponse::from)
				.toList();
	}

	@Transactional
	public FeedbackResponse save(Long userId, Long courseId, FeedbackRequest request) {
		Course course = findCourse(userId, courseId);
		String concept = request.concept().strip().replaceAll("\\s+", " ");
		String key = Course.nameKey(concept);
		String name = course.getKeywords().stream()
				.filter(keyword -> Course.nameKey(keyword).equals(key))
				.findFirst()
				.orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, UNKNOWN_CONCEPT_MESSAGE));
		FeedbackType type = FeedbackType.fromApiValue(request.feedback())
				.orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "피드백을 골라 주세요."));
		if (request.analysisRunId() != null && runRepository.findByIdAndUserId(request.analysisRunId(), userId)
				.filter(run -> run.getCourseId().equals(courseId)).isEmpty()) {
			throw new BusinessException(HttpStatus.NOT_FOUND, AnalysisQueryService.RUN_NOT_FOUND_MESSAGE);
		}
		String comment = request.comment() == null || request.comment().isBlank() ? null : request.comment().strip();
		LocalDateTime now = LocalDateTime.now(clock);
		ConceptFeedback feedback = feedbackRepository.findByUserIdAndCourseIdAndConceptKey(userId, courseId, key)
				.orElse(null);
		if (feedback == null) {
			feedback = ConceptFeedback.create(userId, courseId, key, name, type, comment, request.analysisRunId(), now);
		} else {
			feedback.change(type, comment, request.analysisRunId(), now);
		}
		try {
			return FeedbackResponse.from(feedbackRepository.saveAndFlush(feedback));
		} catch (DataIntegrityViolationException e) {
			// 같은 개념 피드백을 거의 동시에 두 번 만든 경우
			throw new BusinessException(HttpStatus.CONFLICT, "다른 요청과 겹쳐 저장하지 못했어요. 다시 시도해 주세요.");
		}
	}

	// 피드백 취소
	@Transactional
	public void delete(Long userId, Long id) {
		feedbackRepository.delete(feedbackRepository.findByIdAndUserId(id, userId)
				.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, NOT_FOUND_MESSAGE)));
	}

	private Course findCourse(Long userId, Long courseId) {
		return courseRepository.findByIdAndUserId(courseId, userId)
				.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, AnalysisService.COURSE_NOT_FOUND_MESSAGE));
	}
}
