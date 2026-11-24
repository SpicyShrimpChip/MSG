package com.msg.backend.domain.auth.application;

import com.msg.backend.domain.auth.entity.SocialProvider;

/**
 * 소셜 로그인 제공사에서 받은 사용자 정보. DB에 저장하지 않는다(가입을 마치면 회원번호만 user_social_account에 남는다).
 *
 * @param providerUserId 제공사 회원번호
 * @param email          제공사 계정 이메일. 동의하지 않았거나 없으면 null
 * @param emailVerified  제공사가 확인한 이메일인지 (카카오: is_email_valid && is_email_verified)
 * @param nickname       닉네임(선택 동의). 없으면 null
 */
public record SocialProfile(SocialProvider provider, String providerUserId, String email, boolean emailVerified,
		String nickname) {
}
