package com.msg.backend.global.security;

import com.msg.backend.domain.user.entity.Role;
import java.util.Optional;

/**
 * Access Token에 담긴 로그인 세션이 아직 살아 있는지 확인하고, 살아 있으면 그 사용자의 현재 역할을 돌려준다
 * (로그아웃·재사용 감지로 폐기된 세션의 토큰을 바로 막고, 역할을 바꾸면 토큰이 끝나기 전에도 바로 적용되게 하기 위해).
 * 구현은 domain/auth에 있다.
 */
public interface SessionValidator {

	// 세션이 없거나 만료·폐기됐거나 사용자가 지워졌으면 빈 값
	Optional<Role> findActiveRole(Long userId, String sessionId);
}
