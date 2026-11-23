package com.msg.backend.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 탈퇴한 이메일의 재가입 제한 기록. 이메일 원문 없이 키를 건 해시만 둔다 (V15, RejoinRestriction)
@Entity
@Table(name = "withdrawn_account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WithdrawnAccount {

	@Id
	@Column(name = "email_hash", length = 64)
	private String emailHash;

	@Column(name = "withdrawn_at", nullable = false)
	private LocalDateTime withdrawnAt;

	@Column(name = "rejoin_allowed_at", nullable = false)
	private LocalDateTime rejoinAllowedAt;

	public static WithdrawnAccount of(String emailHash, LocalDateTime withdrawnAt, LocalDateTime rejoinAllowedAt) {
		WithdrawnAccount account = new WithdrawnAccount();
		account.emailHash = emailHash;
		account.withdrawnAt = withdrawnAt;
		account.rejoinAllowedAt = rejoinAllowedAt;
		return account;
	}

	// 같은 이메일로 다시 탈퇴한 경우 (기간이 지나 가입했다가 다시 탈퇴) 새 시각으로 바꾼다
	public void renew(LocalDateTime withdrawnAt, LocalDateTime rejoinAllowedAt) {
		this.withdrawnAt = withdrawnAt;
		this.rejoinAllowedAt = rejoinAllowedAt;
	}
}
