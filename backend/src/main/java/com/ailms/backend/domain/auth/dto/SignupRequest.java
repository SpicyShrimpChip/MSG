package com.ailms.backend.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 회원가입 요청 (frontend/src/types/auth.ts의 SignupInput과 같은 모양).
 * 비밀번호 규칙은 AuthService에서 PasswordPolicy로 따로 검사한다.
 * 동의 항목은 boolean이라 빠지면 false가 되어 필수 동의 검사에 걸린다.
 */
public record SignupRequest(
		@NotBlank(message = "이메일을 입력해 주세요.")
		@Size(max = 254, message = "이메일이 너무 길어요.")
		@Email(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", message = "이메일 형식을 확인해 주세요. (예: name@example.com)")
		String email,

		@NotBlank(message = "이름을 입력해 주세요.")
		@Size(max = 30, message = "이름은 30자까지 입력할 수 있어요.")
		String name,

		@NotBlank(message = "비밀번호를 입력해 주세요.")
		String password,

		@NotBlank(message = "비밀번호 확인을 입력해 주세요.")
		String passwordConfirm,

		@AssertTrue(message = "서비스 이용약관에 동의해 주세요.")
		boolean agreedTerms,

		@AssertTrue(message = "개인정보 수집·이용에 동의해 주세요.")
		boolean agreedPrivacy,

		// 선택 동의. 동의하지 않아도 자료로 공부할 수 있다
		boolean agreedAiAnalysis,
		// 선택 동의. 퀴즈 만들기·주관식·서술형 AI 채점에 필요하다. 빠지면 false
		boolean agreedAiQuiz,
		// 아래 셋은 선택. 빠지면 '아직 정하지 않음'·직무 없음 (예전 가입 요청과 호환). 마이페이지에서 바꿀 수 있다
		@Schema(description = "exam_prep / self_development / work / undecided. 없으면 undecided") String learningPurpose,
		@Schema(description = "관심 직무. 없으면 null") String jobField,
		@Schema(description = "jobField가 other일 때 분야 이름 (30자까지)") String jobFieldOther) {
}
