package com.ailms.backend.domain.material.dto;

import com.ailms.backend.domain.material.entity.Material;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

// 내 학습자료 목록 한 줄 (frontend/src/types/material.ts의 MaterialSummary). 요약·개념 본문은 싣지 않는다
public record MaterialSummaryResponse(
		Long id,
		@Schema(description = "과목에 연결하지 않았으면 null") Long courseId,
		String title,
		@Schema(description = "올린 파일 이름 (화면 표시용)") String fileName,
		LocalDateTime createdAt,
		@Schema(allowableValues = {"uploaded", "processing", "ready", "failed"},
				description = "uploaded: 저장 완료·AI 분석 전 / processing: 분석 중 / ready: 완료 / failed: 처리 실패")
		String status,
		@Schema(description = "이 자료로 만든 퀴즈 수. 아직 세지 않아 항상 0 (TODO: quiz 표에서 센다)") int quizCount) {

	public static MaterialSummaryResponse from(Material material) {
		return new MaterialSummaryResponse(material.getId(), material.getCourseId(), material.getTitle(),
				material.getOriginalFileName(), material.getCreatedAt(), material.getStatus().apiValue(), 0);
	}
}
