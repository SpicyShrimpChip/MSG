package com.ailms.backend.domain.quiz.application;

import com.ailms.backend.domain.course.entity.Course;
import com.ailms.backend.domain.quiz.entity.AssessmentKind;
import com.ailms.backend.domain.quiz.entity.QuestionType;
import com.ailms.backend.domain.quiz.entity.QuizDifficulty;
import com.ailms.backend.domain.quiz.entity.QuizMode;
import com.ailms.backend.domain.user.entity.JobField;
import java.util.List;
import java.util.Map;

/**
 * 검사를 마친 퀴즈 만들기 요청 (QuizSpecResolver가 만든다). 사후평가는 사전평가에서 가져온 값이 들어 있다.
 */
record QuizSpec(Course course, Long materialId, String materialTitle, QuizMode mode, List<QuestionType> types,
		Map<QuestionType, Integer> plan, QuizDifficulty difficulty, List<String> concepts, String examScope,
		JobField jobField, String jobFieldOther, boolean mockExam, AssessmentKind assessmentKind, Long preAssessmentId,
		List<String> avoidQuestions, String materialExcerpt, String materialContentNote, String conceptSource,
		String checkConceptKey, String checkConceptName) {

	String jobLabel() {
		if (jobField == null) {
			return null;
		}
		return jobField == JobField.OTHER ? jobFieldOther : jobField.label();
	}
}
