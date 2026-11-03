package com.ailms.backend.domain.user.application;

import com.ailms.backend.domain.conversation.application.PersonalInfoDetector;
import com.ailms.backend.domain.user.entity.JobField;
import com.ailms.backend.domain.user.entity.LearningPurpose;
import com.ailms.backend.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 학습 목적·관심 직무 값을 검사해 Enum으로 바꾼다 (가입과 마이페이지가 같은 규칙을 쓴다).
 * 직무는 분야만 고르게 하고, 기타일 때만 짧은 이름을 받는다. 그 이름은 퀴즈를 만들 때 외부 AI로 가므로 개인정보 패턴을 막는다.
 */
@Component
@RequiredArgsConstructor
public class LearningProfileValidator {

	static final String PURPOSE_MESSAGE = "학습 목적을 다시 골라 주세요.";
	static final String JOB_FIELD_MESSAGE = "관심 직무를 다시 골라 주세요.";
	static final String OTHER_REQUIRED_MESSAGE = "기타 직무 이름을 적어 주세요.";
	static final String OTHER_TOO_LONG_MESSAGE = "기타 직무 이름은 " + JobField.OTHER_MAX_LENGTH + "자까지 적을 수 있어요.";
	static final String OTHER_PERSONAL_INFO_MESSAGE = "기타 직무 이름에 연락처 같은 개인정보는 적지 말아 주세요.";

	private final PersonalInfoDetector personalInfoDetector;

	public record Profile(LearningPurpose purpose, JobField jobField, String jobFieldOther) {
	}

	// purpose가 비어 있으면 '아직 정하지 않음' (예전 가입 요청과 호환)
	public Profile validate(String purpose, String jobField, String jobFieldOther) {
		LearningPurpose parsedPurpose = StringUtils.hasText(purpose)
				? LearningPurpose.fromApiValue(purpose).orElseThrow(() -> badRequest(PURPOSE_MESSAGE))
				: LearningPurpose.UNDECIDED;
		JobField parsedJob = StringUtils.hasText(jobField)
				? JobField.fromApiValue(jobField).orElseThrow(() -> badRequest(JOB_FIELD_MESSAGE))
				: null;
		String other = null;
		if (parsedJob == JobField.OTHER) {
			other = jobFieldOther == null ? "" : jobFieldOther.strip();
			if (other.isEmpty()) {
				throw badRequest(OTHER_REQUIRED_MESSAGE);
			}
			if (other.length() > JobField.OTHER_MAX_LENGTH) {
				throw badRequest(OTHER_TOO_LONG_MESSAGE);
			}
			if (personalInfoDetector.containsPersonalInfo(other)) {
				throw badRequest(OTHER_PERSONAL_INFO_MESSAGE);
			}
		}
		return new Profile(parsedPurpose, parsedJob, other);
	}

	private static BusinessException badRequest(String message) {
		return new BusinessException(HttpStatus.BAD_REQUEST, message);
	}
}
