package com.ailms.backend.domain.user.application;

import com.ailms.backend.domain.user.dao.WithdrawnAccountRepository;
import com.ailms.backend.domain.user.entity.WithdrawnAccount;
import com.ailms.backend.global.error.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴 뒤 재가입 제한. 탈퇴할 때 이메일의 키 해시와 재가입 가능 시각을 남기고(WithdrawalService와 같은 트랜잭션),
 * 가입할 때 기간 안이면 409로 막는다(AuthService). 기간이 지난 기록은 주기 작업이 지운다.
 *
 * <p>목적은 탈퇴·재가입을 반복해 사용자별 AI 이용 한도를 우회하는 것을 막는 것이다. 다른 이메일로 가입하는 것은 막지 못하고,
 * 그쪽은 서비스 전체 하루 상한(app.ai-daily-limit)과 가입 자동 방지(Turnstile)가 맡는다.
 * 보관 항목·기간은 개인정보 처리방침 4번에 적었다. 비밀 키가 없으면 아무것도 하지 않는다(RejoinProperties).
 */
@Slf4j
@Component
public class RejoinRestriction {

	private static final String ALGORITHM = "HmacSHA256";
	private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Seoul");
	private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("M월 d일 HH:mm");

	private final WithdrawnAccountRepository repository;
	private final RejoinProperties properties;
	private final Clock clock;

	public RejoinRestriction(WithdrawnAccountRepository repository, RejoinProperties properties, Clock clock) {
		this.repository = repository;
		this.properties = properties;
		this.clock = clock;
	}

	/** 탈퇴할 때 부른다(탈퇴 트랜잭션 안). email은 저장된 그대로(정규화된 값)다. */
	public void recordWithdrawal(String email) {
		if (!properties.enabled()) {
			return;
		}
		LocalDateTime now = now();
		LocalDateTime allowedAt = now.plusDays(properties.restrictDays());
		String hash = hash(email);
		repository.findById(hash).ifPresentOrElse(account -> account.renew(now, allowedAt),
				() -> repository.save(WithdrawnAccount.of(hash, now, allowedAt)));
	}

	/** 가입할 때 부른다. 기간 안에 탈퇴한 이메일이면 409. email은 정규화한 값이다. */
	public void ensureCanJoin(String email) {
		if (!properties.enabled()) {
			return;
		}
		repository.findById(hash(email))
				.filter(account -> account.getRejoinAllowedAt().isAfter(now()))
				.ifPresent(account -> {
					throw new BusinessException(HttpStatus.CONFLICT, "탈퇴한 이메일은 " + properties.restrictDays()
							+ "일 동안 다시 가입할 수 없어요. " + displayTime(account.getRejoinAllowedAt())
							+ " 이후에 가입할 수 있어요.");
				});
	}

	// 저장 시각은 서버 시간대(운영 컨테이너는 UTC일 수 있다) 기준이라, 화면 문구는 한국 시간으로 바꿔 보여 준다
	private String displayTime(LocalDateTime allowedAt) {
		ZonedDateTime kst = allowedAt.atZone(clock.getZone()).withZoneSameInstant(DISPLAY_ZONE);
		return DISPLAY_FORMAT.format(kst);
	}

	/** 재가입 가능 시각이 지난 기록을 지운다. 지운 수를 돌려준다. 키가 없어도 남은 기록은 지운다. */
	@Scheduled(fixedDelayString = "${app.rejoin.cleanup-interval}", initialDelayString = "${app.rejoin.cleanup-interval}")
	@Transactional
	public int purgeExpired() {
		int deleted = repository.deleteExpired(now());
		if (deleted > 0) {
			log.info("재가입 제한 기간이 지난 탈퇴 기록 {}건을 지웠다", deleted);
		}
		return deleted;
	}

	private LocalDateTime now() {
		// DB 열(TIMESTAMP(6))과 같은 정밀도로 맞춘다 (docs/wiki/backend.md "저장 직후 응답의 시각")
		return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
	}

	String hash(String email) {
		try {
			Mac mac = Mac.getInstance(ALGORITHM);
			mac.init(new SecretKeySpec(properties.hashSecret().getBytes(StandardCharsets.UTF_8), ALGORITHM));
			return HexFormat.of().formatHex(mac.doFinal(email.getBytes(StandardCharsets.UTF_8)));
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("이메일 해시를 만들지 못했다", e);
		}
	}
}
