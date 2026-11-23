package com.msg.backend.domain.user.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 탈퇴 뒤 재가입 제한 (app.rejoin). 비밀 키는 환경변수 REJOIN_HASH_SECRET으로만 받는다.
 * 비밀 키가 비어 있으면 꺼진다: 탈퇴 기록을 남기지 않고 가입도 막지 않는다(로컬 개발·기본 테스트, 키를 빠뜨린 배포).
 * 키를 바꾸면 이전 기록과 맞지 않으므로 운영 중에는 바꾸지 않는다(바꾸면 그때까지의 제한이 풀릴 뿐 다른 문제는 없다).
 *
 * @param restrictDays    탈퇴 뒤 같은 이메일로 다시 가입할 수 없는 날 수
 * @param hashSecret      이메일 해시(HMAC-SHA256) 비밀 키. 비어 있으면 꺼짐
 * @param cleanupInterval 기간이 지난 기록을 지우는 주기
 */
@ConfigurationProperties(prefix = "app.rejoin")
public record RejoinProperties(int restrictDays, String hashSecret, Duration cleanupInterval) {

	public boolean enabled() {
		return hashSecret != null && !hashSecret.isBlank() && restrictDays > 0;
	}
}
