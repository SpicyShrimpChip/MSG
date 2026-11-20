package com.ailms.backend.domain.user.dao;

import com.ailms.backend.domain.user.entity.WithdrawnAccount;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WithdrawnAccountRepository extends JpaRepository<WithdrawnAccount, String> {

	// 재가입 가능 시각이 지난 기록을 지운다. 지운 수를 돌려준다
	@Modifying
	@Query("delete from WithdrawnAccount w where w.rejoinAllowedAt <= :now")
	int deleteExpired(@Param("now") LocalDateTime now);
}
