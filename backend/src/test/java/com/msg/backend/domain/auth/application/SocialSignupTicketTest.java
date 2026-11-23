package com.msg.backend.domain.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.msg.backend.domain.auth.entity.SocialProvider;
import com.msg.backend.global.security.jwt.JwtProperties;
import com.msg.backend.global.security.jwt.JwtTokenProvider;
import com.msg.backend.support.MutableClock;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;

// 가입 마무리 표(티켓): 카카오 프로필을 DB 대신 서명한 짧은 토큰에 담는다. 10분, 위조·용도 바꿔치기 불가
class SocialSignupTicketTest {

	private static final String SECRET = Base64.getEncoder().encodeToString(new byte[48]);
	private static final SocialProfile PROFILE =
			new SocialProfile(SocialProvider.KAKAO, "4242", "kakao@example.com", true, "카카오닉");

	private final MutableClock clock = new MutableClock();
	private final JwtTokenProvider provider = provider(SECRET);

	@Test
	void 만든_티켓을_그대로_읽는다() {
		String ticket = provider.createSocialSignupTicket(PROFILE);

		assertThat(provider.parseSocialSignupTicket(ticket)).contains(PROFILE);
	}

	@Test
	void 닉네임이_없어도_읽는다() {
		SocialProfile noNick = new SocialProfile(SocialProvider.KAKAO, "1", "a@example.com", true, null);

		assertThat(provider.parseSocialSignupTicket(provider.createSocialSignupTicket(noNick))).contains(noNick);
	}

	@Test
	void 확인되지_않은_이메일은_티켓으로_만들지_않는다() {
		SocialProfile unverified = new SocialProfile(SocialProvider.KAKAO, "1", "a@example.com", false, null);
		SocialProfile noEmail = new SocialProfile(SocialProvider.KAKAO, "1", null, false, null);

		assertThatThrownBy(() -> provider.createSocialSignupTicket(unverified))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> provider.createSocialSignupTicket(noEmail))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 십분이_지나면_읽지_못한다() {
		String ticket = provider.createSocialSignupTicket(PROFILE);

		clock.advance(Duration.ofMinutes(10).plusSeconds(1));

		assertThat(provider.parseSocialSignupTicket(ticket)).isEmpty();
	}

	@Test
	void 서명을_바꾸면_읽지_못한다() {
		String ticket = provider.createSocialSignupTicket(PROFILE);
		String other = provider(Base64.getEncoder().encodeToString("x".repeat(48).getBytes()))
				.createSocialSignupTicket(PROFILE);

		assertThat(provider.parseSocialSignupTicket(other)).isEmpty();
		assertThat(provider.parseSocialSignupTicket(ticket.substring(0, ticket.length() - 2) + "AA")).isEmpty();
		assertThat(provider.parseSocialSignupTicket(null)).isEmpty();
		assertThat(provider.parseSocialSignupTicket("not.a.jwt")).isEmpty();
	}

	@Test
	void Access_Token은_티켓으로_읽지_못하고_티켓은_Access_Token으로_읽지_못한다() {
		String accessToken = provider.createAccessToken(1L, "session-1");
		String ticket = provider.createSocialSignupTicket(PROFILE);

		assertThat(provider.parseSocialSignupTicket(accessToken)).isEmpty();
		assertThat(provider.parse(ticket)).isEmpty();
	}

	private JwtTokenProvider provider(String secret) {
		return new JwtTokenProvider(new JwtProperties(secret, false, "msg", Duration.ofMinutes(15)), clock);
	}
}
