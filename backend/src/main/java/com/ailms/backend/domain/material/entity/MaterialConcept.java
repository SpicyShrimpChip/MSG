package com.ailms.backend.domain.material.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 학습자료에서 뽑은 핵심 개념 (material_concept, V10). AI가 자료 본문에서 고르고, 서버가 근거 문장이 실제 본문에 있는지 확인한 것만 저장한다.
 * 근거 위치(쪽·슬라이드)는 AI 말이 아니라 서버가 본문에서 찾은 값이다. 자료·회원을 지우면 함께 지워진다.
 */
@Entity
@Table(name = "material_concept")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MaterialConcept {

	public static final int NAME_MAX_LENGTH = 20;
	public static final int DESCRIPTION_MAX_LENGTH = 300;
	public static final int QUOTE_MAX_LENGTH = 300;
	public static final int LOCATION_MAX_LENGTH = 40;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long materialId;

	@Column(nullable = false)
	private Long userId;

	@Column(nullable = false)
	private int sortOrder;

	@Column(nullable = false, length = NAME_MAX_LENGTH)
	private String conceptKey;

	@Column(nullable = false, length = NAME_MAX_LENGTH)
	private String name;

	@Column(length = DESCRIPTION_MAX_LENGTH)
	private String description;

	@Column(length = LOCATION_MAX_LENGTH)
	private String evidenceLocation;

	@Column(nullable = false, length = QUOTE_MAX_LENGTH)
	private String evidenceQuote;

	@Column(nullable = false)
	private boolean courseKeyword;

	@Column(nullable = false, length = 10)
	private String provider;

	@Column(length = 100)
	private String model;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	public static MaterialConcept of(Long materialId, Long userId, int sortOrder, String conceptKey, String name,
			String description, String evidenceLocation, String evidenceQuote, boolean courseKeyword, String provider,
			String model, LocalDateTime now) {
		MaterialConcept concept = new MaterialConcept();
		concept.materialId = materialId;
		concept.userId = userId;
		concept.sortOrder = sortOrder;
		concept.conceptKey = conceptKey;
		concept.name = name;
		concept.description = description;
		concept.evidenceLocation = evidenceLocation;
		concept.evidenceQuote = evidenceQuote;
		concept.courseKeyword = courseKeyword;
		concept.provider = provider;
		concept.model = model;
		concept.createdAt = now;
		return concept;
	}
}
