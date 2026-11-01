package com.ailms.backend.domain.conversation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 서버 개인정보 재검사. 값은 모두 가상 값이고, frontend/src/utils/conversation/mask.test.ts와 같은 예를 쓴다
 * (프론트가 가리는 것은 서버도 찾고, 프론트가 두는 것은 서버도 통과시켜야 정상 대화가 거절되지 않는다).
 */
class PersonalInfoDetectorTest {

	private final PersonalInfoDetector detector = new PersonalInfoDetector();

	@ParameterizedTest
	@ValueSource(strings = {
			"메일은 test@example.com 으로 보내 주세요",
			"전화 010-1234-5678 입니다", "01012345678", "010 1234 5678", "+82 10-1234-5678",
			"02-123-4567", "031-123-4567", "070-1234-5678",
			"900101-1234567", "9001011234567",
			"카드 4111 1111 1111 1111 입니다", "4111-1111-1111-1111",
			"키: sk-proj-abcdefghijklmnop1234", "키: sk-ant-api03-abcdefghijklmnopqrstuv",
			"키: ghp_abcdefghijklmnopqrstuvwxyz0123456789", "키: AKIAIOSFODNN7EXAMPLE",
			"키: eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.abcdefghijk",
			"키: Bearer abcdefghijklmnopqrstuvwxyz123456",
			"서버 192.168.0.1 접속",
			// 한글 바로 뒤에 붙은 키도 찾는다 (JS의 \b와 같은 경계)
			"키는sk-proj-abcdefghijklmnop1234"
	})
	void 가리지_않은_개인정보를_찾는다(String text) {
		assertThat(detector.containsPersonalInfo(text)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"교착 상태는 4가지 조건이 모두 만족될 때 발생해요.",
			"2026년 10월 25일 12345 포트 8080",
			"1234 5678 9012 3456",
			"999.1.1.1",
			"메일은 [이메일] 으로, 전화는 [전화번호], 카드 [카드번호], 키 [키·토큰], 서버 [IP], 저는 [이름]이고 [가린 단어] 다녀요"
	})
	void 개인정보가_아니거나_이미_가린_글은_통과한다(String text) {
		assertThat(detector.containsPersonalInfo(text)).isFalse();
	}

	@Test
	void 긴_영문_숫자_덩어리도_금방_검사한다() {
		// 코드·붙여 넣은 글처럼 '@' 없이 긴 덩어리. 정규식이 시작 위치마다 끝까지 읽으면 몇 분이 걸린다
		String longRun = "a".repeat(20_000);
		assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
			for (int i = 0; i < 50; i++) {
				assertThat(detector.containsPersonalInfo(longRun)).isFalse();
			}
		});
		assertThat(detector.containsPersonalInfo(longRun + " 가나test@example.com")).isTrue();
	}

	@Test
	void 빈_글은_통과한다() {
		assertThat(detector.containsPersonalInfo("")).isFalse();
		assertThat(detector.containsPersonalInfo(null)).isFalse();
	}
}
