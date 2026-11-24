package com.msg.backend.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 카카오 가입 마무리 요청. 이메일은 가입 마무리 쿠키(카카오가 확인한 이메일)에서 오므로 받지 않고, 비밀번호도 없다.
 * 나머지는 일반 가입(SignupRequest)과 같은 항목·문구다.
 */
public record SocialSignupRequest(
		@NotBlank(message = "이름을 입력해 주세요.")
		@Size(max = 30, message = "이름은 30자까지 입력할 수 있어요.")
		String name,

		@AssertTrue(message = "서비스 이용약관에 동의해 주세요.")
		boolean agreedTerms,

		@AssertTrue(message = "개인정보 수집·이용에 동의해 주세요.")
		boolean agreedPrivacy,

		boolean agreedAiAnalysis,
		boolean agreedAiQuiz,
		@Schema(description = "exam_prep / self_development / work / undecided. 없으면 undecided") String learningPurpose,
		@Schema(description = "관심 직무. 없으면 null") String jobField,
		@Schema(description = "jobField가 other일 때 분야 이름 (30자까지)") String jobFieldOther) {
}
