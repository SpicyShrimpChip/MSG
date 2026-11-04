package com.ailms.backend.domain.conversation.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

// 학습 글(퀴즈 답·시험 범위) 검사: 사설·예시 IP는 통과, 공인 IP와 다른 개인정보는 막는다. 대화 검사는 그대로 IP를 막는다
class PersonalInfoLearningTextTest {

	private final PersonalInfoDetector detector = new PersonalInfoDetector();

	@Test
	void 사설_루프백_문서용_IP와_서브넷_마스크는_통과한다() {
		assertThat(detector.containsPersonalInfoExceptIp(
				"192.168.0.0/24, 10.0.0.1, 172.16.5.4, 127.0.0.1, 203.0.113.7, 198.51.100.1, 255.255.255.0")).isFalse();
	}

	@Test
	void 공인_IP와_이메일_전화번호는_막는다() {
		assertThat(detector.containsPersonalInfoExceptIp("우리 집 8.8.4.4")).isTrue();
		assertThat(detector.containsPersonalInfoExceptIp("172.32.0.1")).isTrue();
		assertThat(detector.containsPersonalInfoExceptIp("연락 a@b.co")).isTrue();
		assertThat(detector.containsPersonalInfoExceptIp("010-1234-5678")).isTrue();
	}

	@Test
	void 대화_검사는_예시_IP도_그대로_막는다() {
		assertThat(detector.containsPersonalInfo("192.168.0.1")).isTrue();
	}
}
