package com.msg.backend.domain.auth.application;

import com.msg.backend.global.error.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 반복 로그인 실패를 막는다. 세 기준 중 하나라도 걸리면 막고, 가장 긴 남은 시간을 안내한다.
 * - 같은 이메일·같은 IP: 짧은 시간에 여러 번 틀리면 막는다 (한 기기에서의 비밀번호 대입)
 * - 같은 IP: 여러 이메일로 시도하는 경우를 더 큰 기준으로 막는다
 * - 같은 이메일(IP 무관): 여러 곳에서 한 계정을 노리는 경우를 더 큰 기준으로 막는다.
 *   남이 일부러 틀려 계정 주인을 막을 수 있으므로 잠금은 짧게 두고, 성공하면 기록을 지운다
 * 잠긴 동안의 시도는 호출하는 쪽이 checkAllowed에서 먼저 막으므로 세지 않는다(잠금이 늘어나지 않는다).
 * 운영은 서버 한 대(docs/deploy.md)라 메모리에 둔다. 서버를 여러 대로 늘리면 공용 저장소가 필요하다.
 */
@Component
public class LoginAttemptLimiter {

	// 오래된 기록을 정리하기 시작하는 개수
	private static final int CLEANUP_THRESHOLD = 10_000;

	private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();
	private final AuthProperties.LoginAttempt config;
	private final Clock clock;

	public LoginAttemptLimiter(AuthProperties authProperties, Clock clock) {
		this.config = authProperties.loginAttempt();
		this.clock = clock;
	}

	private record Attempts(List<Instant> failures, Instant lockedUntil) {
	}

	public void checkAllowed(String email, String ip) {
		Instant now = clock.instant();
		Duration wait = max(max(remainingLock(accountKey(email, ip), now), remainingLock(ipKey(ip), now)),
				remainingLock(emailKey(email), now));
		if (!wait.isZero()) {
			long minutes = Math.max(1, (wait.toSeconds() + 59) / 60);
			throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS,
					"로그인 시도가 너무 많아요. " + minutes + "분 뒤 다시 시도해 주세요.");
		}
	}

	public void recordFailure(String email, String ip) {
		Instant now = clock.instant();
		record(accountKey(email, ip), config.maxFailures(), config.lockDuration(), now);
		record(ipKey(ip), config.maxFailuresPerIp(), config.lockDuration(), now);
		record(emailKey(email), config.maxFailuresPerEmail(), config.emailLockDuration(), now);
		if (attempts.size() > CLEANUP_THRESHOLD) {
			attempts.entrySet().removeIf(entry -> isStale(entry.getValue(), now));
		}
	}

	public void recordSuccess(String email, String ip) {
		attempts.remove(accountKey(email, ip));
		attempts.remove(emailKey(email));
	}

	private void record(String key, int maxFailures, Duration lockDuration, Instant now) {
		attempts.compute(key, (k, current) -> {
			List<Instant> failures = new ArrayList<>();
			if (current != null) {
				current.failures().stream().filter(time -> isWithinWindow(time, now)).forEach(failures::add);
			}
			failures.add(now);
			if (failures.size() >= maxFailures) {
				return new Attempts(List.of(), now.plus(lockDuration));
			}
			return new Attempts(List.copyOf(failures), current == null ? null : current.lockedUntil());
		});
	}

	private Duration remainingLock(String key, Instant now) {
		Attempts current = attempts.get(key);
		if (current == null || current.lockedUntil() == null || !current.lockedUntil().isAfter(now)) {
			return Duration.ZERO;
		}
		return Duration.between(now, current.lockedUntil());
	}

	private boolean isWithinWindow(Instant time, Instant now) {
		return time.isAfter(now.minus(config.window()));
	}

	private boolean isStale(Attempts value, Instant now) {
		boolean unlocked = value.lockedUntil() == null || !value.lockedUntil().isAfter(now);
		return unlocked && value.failures().stream().noneMatch(time -> isWithinWindow(time, now));
	}

	private static Duration max(Duration a, Duration b) {
		return a.compareTo(b) >= 0 ? a : b;
	}

	private static String accountKey(String email, String ip) {
		return "account:" + email + "|" + ip;
	}

	private static String ipKey(String ip) {
		return "ip:" + ip;
	}

	private static String emailKey(String email) {
		return "email:" + email;
	}
}
