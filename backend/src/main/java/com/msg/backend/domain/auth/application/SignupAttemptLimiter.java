package com.msg.backend.domain.auth.application;

import com.msg.backend.global.error.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 한 IP에서 짧은 시간에 가입을 너무 많이 시도하는 것을 막는다 (app.auth.signup-attempt). 계정을 많이 만들어 하루 AI 몫을
 * 여러 번 받거나, 가입 응답으로 이미 가입한 이메일을 대량으로 확인하는 것을 느리게 한다. 이메일 가입과 카카오 가입 마무리를 함께 센다.
 * 학교·회사처럼 여러 사람이 같은 IP로 가입할 수 있어 기준을 넉넉히 둔다. 성공·실패와 상관없이 컨트롤러에 닿은 시도를 센다.
 * 프록시 뒤의 실제 IP는 server.forward-headers-strategy(운영)로 읽는다.
 * TODO: 서버를 여러 대 띄우면 메모리 대신 공용 저장소가 필요하다 (LoginAttemptLimiter와 같음)
 */
@Component
public class SignupAttemptLimiter {

	static final String TOO_MANY_MESSAGE_FORMAT = "같은 곳에서 가입 시도가 너무 많아요. %d분 뒤 다시 시도해 주세요.";

	// 오래된 기록을 정리하기 시작하는 개수
	private static final int CLEANUP_THRESHOLD = 10_000;

	private final Map<String, List<Instant>> attempts = new ConcurrentHashMap<>();
	private final AuthProperties.SignupAttempt config;
	private final Clock clock;

	public SignupAttemptLimiter(AuthProperties authProperties, Clock clock) {
		this.config = authProperties.signupAttempt();
		this.clock = clock;
	}

	/** 시도 한 번을 센다. 이 IP가 기간 안에 이미 상한만큼 시도했으면 세지 않고 429 */
	public void acquire(String ip) {
		if (config.maxPerIp() <= 0) {
			return;
		}
		Instant now = clock.instant();
		AtomicReference<Duration> wait = new AtomicReference<>(Duration.ZERO);
		attempts.compute(ip, (key, current) -> {
			List<Instant> recent = new ArrayList<>();
			if (current != null) {
				current.stream().filter(time -> isWithinWindow(time, now)).forEach(recent::add);
			}
			if (recent.size() >= config.maxPerIp()) {
				// 가장 오래된 시도가 기간 밖으로 나가면 다시 할 수 있다
				wait.set(Duration.between(now, recent.get(0).plus(config.window())));
				return List.copyOf(recent);
			}
			recent.add(now);
			return List.copyOf(recent);
		});
		if (attempts.size() > CLEANUP_THRESHOLD) {
			attempts.entrySet().removeIf(entry -> entry.getValue().stream().noneMatch(t -> isWithinWindow(t, now)));
		}
		if (!wait.get().isZero() && !wait.get().isNegative()) {
			long minutes = Math.max(1, (wait.get().toSeconds() + 59) / 60);
			throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, TOO_MANY_MESSAGE_FORMAT.formatted(minutes));
		}
	}

	private boolean isWithinWindow(Instant time, Instant now) {
		return time.isAfter(now.minus(config.window()));
	}
}
