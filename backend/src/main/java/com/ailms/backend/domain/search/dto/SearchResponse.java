package com.ailms.backend.domain.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/**
 * 통합 검색 결과 (frontend/src/types/search.ts의 SearchResult). 내 과목·자료·자료 핵심 개념과 공지사항 제목에서 찾는다.
 * 대소문자와 띄어쓰기는 무시한다("정보보호 개론"도 "정보보호개론"을 찾는다). 자료 본문은 검색하지 않는다.
 */
public record SearchResponse(
		@Schema(description = "검색어 (앞뒤 공백을 지운 값)") String query,
		List<CourseHit> courses,
		List<MaterialHit> materials,
		List<ConceptHit> concepts,
		List<NoticeHit> notices) {

	public record CourseHit(
			Long id,
			String name,
			String semester,
			@Schema(description = "과목 이름이 검색어와 맞는지") boolean nameMatched,
			@Schema(description = "검색어와 맞는 과목 키워드") List<String> matchedKeywords,
			@Schema(description = "이 과목에 올린 내 자료 수") int materialCount) {
	}

	public record MaterialHit(
			Long id,
			String title,
			String fileName,
			Long courseId,
			String courseName,
			@Schema(allowableValues = {"title", "course"},
					description = "title: 자료 제목·파일 이름이 맞음 / course: 검색어와 맞는 과목에 올린 자료") String matchedBy) {
	}

	public record ConceptHit(
			Long materialId,
			String materialTitle,
			String conceptId,
			String name,
			@Schema(description = "AI가 쓴 짧은 설명 (없으면 null)") String aiDescription) {
	}

	public record NoticeHit(Long id, String title, LocalDate createdAt) {
	}
}
