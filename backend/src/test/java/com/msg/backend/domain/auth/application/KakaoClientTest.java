package com.msg.backend.domain.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.msg.backend.domain.auth.entity.SocialProvider;
import com.msg.backend.support.FakeKakaoServer;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// 카카오 토큰·사용자 정보·연결 끊기 요청. 실제 카카오 대신 로컬 가짜 서버로 확인한다
class KakaoClientTest {

	private FakeKakaoServer kakao;

	@BeforeEach
	void start() {
		kakao = FakeKakaoServer.start();
	}

	@AfterEach
	void stop() {
		kakao.close();
	}

	@Test
	void 코드로_프로필을_받는다() {
		SocialProfile profile = client("test-admin", Duration.ofSeconds(2)).fetchProfile("auth-code");

		assertThat(kakao.lastForm(FakeKakaoServer.TOKEN))
				.containsEntry("grant_type", "authorization_code")
				.containsEntry("client_id", "cid")
				.containsEntry("client_secret", "csecret")
				.containsEntry("redirect_uri", "http://localhost:5173/api/auth/kakao/callback")
				.containsEntry("code", "auth-code");
		assertThat(kakao.lastAuthorization(FakeKakaoServer.ME)).isEqualTo("Bearer fake-kakao-access");
		assertThat(profile).isEqualTo(
				new SocialProfile(SocialProvider.KAKAO, "4242", "kakao@example.com", true, "카카오닉"));
	}

	@Test
	void 이메일_확인이_안_됐으면_emailVerified가_false다() {
		kakao.user(7L, "unverified@example.com", true, false, null);

		SocialProfile profile = client("", Duration.ofSeconds(2)).fetchProfile("c");

		assertThat(profile.emailVerified()).isFalse();
		assertThat(profile.nickname()).isNull();
	}

	@Test
	void 유효하지_않은_이메일이면_emailVerified가_false다() {
		kakao.user(7L, "invalid@example.com", false, true, null);

		assertThat(client("", Duration.ofSeconds(2)).fetchProfile("c").emailVerified()).isFalse();
	}

	@Test
	void 이메일이_없으면_email이_null이다() {
		kakao.user(8L, null, false, false, "닉");

		SocialProfile profile = client("", Duration.ofSeconds(2)).fetchProfile("c");

		assertThat(profile.email()).isNull();
		assertThat(profile.emailVerified()).isFalse();
		assertThat(profile.providerUserId()).isEqualTo("8");
	}

	@Test
	void 토큰_요청이_400이면_FAILED() {
		kakao.reply(FakeKakaoServer.TOKEN, 400, "{\"error\":\"invalid_grant\"}");

		assertThatThrownBy(() -> client("", Duration.ofSeconds(2)).fetchProfile("c"))
				.isInstanceOf(SocialLoginException.class)
				.extracting("error").isEqualTo(SocialError.FAILED);
		assertThat(kakao.calls(FakeKakaoServer.ME)).isZero();
	}

	@Test
	void 사용자_정보가_시간을_넘기면_FAILED() {
		kakao.delay(FakeKakaoServer.ME, Duration.ofSeconds(2));

		assertThatThrownBy(() -> client("", Duration.ofSeconds(1)).fetchProfile("c"))
				.isInstanceOf(SocialLoginException.class)
				.extracting("error").isEqualTo(SocialError.FAILED);
	}

	@Test
	void 사용자_정보에_회원번호가_없으면_FAILED() {
		kakao.reply(FakeKakaoServer.ME, 200, "{\"kakao_account\":{}}");

		assertThatThrownBy(() -> client("", Duration.ofSeconds(2)).fetchProfile("c"))
				.isInstanceOf(SocialLoginException.class)
				.extracting("error").isEqualTo(SocialError.FAILED);
	}

	@Test
	void unlink는_어드민_키와_회원번호를_보낸다() {
		client("test-admin", Duration.ofSeconds(2)).unlink("4242");

		assertThat(kakao.lastAuthorization(FakeKakaoServer.UNLINK)).isEqualTo("KakaoAK test-admin");
		assertThat(kakao.lastForm(FakeKakaoServer.UNLINK))
				.containsEntry("target_id_type", "user_id")
				.containsEntry("target_id", "4242");
	}

	@Test
	void 어드민_키가_없으면_unlink는_부르지_않는다() {
		client("", Duration.ofSeconds(2)).unlink("4242");

		assertThat(kakao.calls(FakeKakaoServer.UNLINK)).isZero();
	}

	@Test
	void unlink가_실패하면_예외를_던진다() {
		kakao.reply(FakeKakaoServer.UNLINK, 500, "{}");

		assertThatThrownBy(() -> client("test-admin", Duration.ofSeconds(2)).unlink("4242"))
				.isInstanceOf(SocialLoginException.class);
	}

	private KakaoClient client(String adminKey, Duration timeout) {
		return new KakaoClient(new KakaoProperties("cid", "csecret", adminKey,
				"http://localhost:5173/api/auth/kakao/callback", kakao.baseUrl(), kakao.baseUrl(), timeout));
	}
}
