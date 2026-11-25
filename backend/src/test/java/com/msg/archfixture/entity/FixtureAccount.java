package com.msg.archfixture.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * 구조 규칙 자체 검사용 Entity. 서비스 패키지(com.msg.backend) 밖이라 JPA가 읽지 않는다.
 */
@Entity
public class FixtureAccount {

	@Id
	private Long id;

	private String passwordHash;
}
