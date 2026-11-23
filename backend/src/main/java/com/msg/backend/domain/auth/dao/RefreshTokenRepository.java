package com.msg.backend.domain.auth.dao;

import com.msg.backend.domain.auth.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import com.msg.backend.domain.user.entity.Role;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

	Optional<RefreshToken> findByTokenHash(String tokenHash);

	// 같은 토큰으로 동시에 갱신해도 한 번만 회전되도록 행을 잠근다
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select t from RefreshToken t where t.tokenHash = :tokenHash")
	Optional<RefreshToken> findByTokenHashForUpdate(String tokenHash);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update RefreshToken t set t.revokedAt = :now where t.sessionId = :sessionId and t.revokedAt is null")
	int revokeSession(String sessionId, LocalDateTime now);

	// 살아 있는 세션이면 그 세션 주인의 현재 역할을 한 번에 읽는다 (토큰 주인과 세션 주인이 같아야 한다)
	@Query("select distinct u.role from RefreshToken t, User u where u.id = t.userId and t.userId = :userId"
			+ " and t.sessionId = :sessionId and t.revokedAt is null and t.expiresAt > :now")
	List<Role> findActiveRoles(Long userId, String sessionId, LocalDateTime now);
}
