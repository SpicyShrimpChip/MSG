package com.msg.backend.domain.user.entity;

public enum Role {
	// 관리자는 가입으로 만들 수 없다. 지금은 DB의 users.role을 직접 ADMIN으로 바꿔 지정한다 (역할 변경 API 없음)
	USER,
	ADMIN
}
