package com.msg.backend.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

// 테스트에서 시간을 앞으로 돌려 토큰 만료를 확인하기 위한 시계
public class MutableClock extends Clock {

	private final ZoneId zone;
	private volatile Instant instant;

	public MutableClock() {
		this(Instant.now(), ZoneId.systemDefault());
	}

	private MutableClock(Instant instant, ZoneId zone) {
		this.instant = instant;
		this.zone = zone;
	}

	public void advance(Duration duration) {
		instant = instant.plus(duration);
	}

	public void reset() {
		instant = Instant.now();
	}

	@Override
	public ZoneId getZone() {
		return zone;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return new MutableClock(instant, zone);
	}

	@Override
	public Instant instant() {
		return instant;
	}
}
