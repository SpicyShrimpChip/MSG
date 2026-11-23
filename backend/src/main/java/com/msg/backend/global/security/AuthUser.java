package com.msg.backend.global.security;

/**
 * 인증된 요청의 주체. 컨트롤러에서 {@code @AuthenticationPrincipal AuthUser}로 받는다.
 * 사용자 데이터를 조회·수정할 때는 요청 본문이나 경로의 사용자 id가 아니라 항상 이 userId를 쓴다.
 */
public record AuthUser(Long userId, String sessionId) {
}
