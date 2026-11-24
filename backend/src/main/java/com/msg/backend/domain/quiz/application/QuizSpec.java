package com.msg.backend.domain.quiz.application;

import com.msg.backend.domain.course.entity.Course;
import com.msg.backend.domain.quiz.entity.AssessmentKind;
import com.msg.backend.domain.quiz.entity.BloomLevel;
import com.msg.backend.domain.quiz.entity.QuestionType;
import com.msg.backend.domain.quiz.entity.QuizDifficulty;
import com.msg.backend.domain.quiz.entity.QuizMode;
import com.msg.backend.domain.user.entity.JobField;
import java.util.List;
import java.util.Map;

/**
 * 검사를 마친 퀴즈 만들기 요청 (QuizSpecResolver가 만든다). 사후평가는 사전평가에서 가져온 값이 들어 있다.
 * materialExcerpt는 서버가 고른 자료 부분(위치 표시 포함)이고 materialScope는 그 위치, missingConcepts는 자료에서 찾지 못한 개념이다.
 */
record QuizSpec(Course course, Long materialId, String materialTitle, QuizMode mode, List<QuestionType> types,
		Map<QuestionType, Integer> plan, QuizDifficulty difficulty, List<String> concepts, String examScope,
		JobField jobField, String jobFieldOther, boolean mockExam, AssessmentKind assessmentKind, Long preAssessmentId,
		List<String> avoidQuestions, String materialExcerpt, String materialContentNote, String conceptSource,
		String checkConceptKey, String checkConceptName, List<BloomLevel> bloomTargets, List<String> materialScope,
		List<String> missingConcepts) {

	String jobLabel() {
		return JobField.labelOf(jobField, jobFieldOther);
	}
}
