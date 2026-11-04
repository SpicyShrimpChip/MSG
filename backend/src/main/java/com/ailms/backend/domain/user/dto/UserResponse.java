package com.ailms.backend.domain.user.dto;

import com.ailms.backend.domain.user.entity.Role;
import com.ailms.backend.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

// frontend/src/types/auth.ts의 User와 같은 모양. 비밀번호 해시·토큰은 넣지 않는다.
// role은 화면에서 관리자 메뉴를 보일지 정하는 데만 쓴다 (권한은 서버가 매번 확인한다)
public record UserResponse(
		Long id,
		String email,
		String name,
		boolean aiAnalysisConsent,
		Role role,
		@Schema(description = "주된 학습 목적", allowableValues = {"exam_prep", "self_development", "work", "undecided"})
		String learningPurpose,
		@Schema(description = "관심 직무. 고르지 않았으면 null", allowableValues = {"it_consulting", "network_security",
				"software_development", "data_analysis", "system_cloud_operations", "other"})
		String jobField,
		@Schema(description = "관심 직무가 other일 때 직접 쓴 분야 이름. 아니면 null") String jobFieldOther,
		@Schema(description = "AI 퀴즈 처리 동의(선택). 퀴즈 만들기·주관식·서술형 AI 채점에 필요") boolean aiQuizConsent) {

	public static UserResponse from(User user) {
		return new UserResponse(user.getId(), user.getEmail(), user.getName(), user.isAiAnalysisConsent(),
				user.getRole(), user.getLearningPurpose().apiValue(),
				user.getJobField() == null ? null : user.getJobField().apiValue(), user.getJobFieldOther(),
				user.isAiQuizConsent());
	}
}
