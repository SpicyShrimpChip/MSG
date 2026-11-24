package com.msg.backend.domain.material.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 학습자료 요약본 (frontend/src/types/material.ts의 MaterialSummary). content의 글(text·name·question·hint)은 AI가 쓴 것이고,
 * quote·location·check는 서버가 원문에서 확인한 값이다. 원문 인용이 있다는 것만 확인하며 요약 전체가 사실이라는 뜻이 아니다.
 */
public record StudySummaryResponse(
		Long materialId,
		@Schema(description = "exam_prep / self_development / work") String purpose,
		@Schema(description = "none: 아직 없음 / running: 만드는 중 / ready: 있음") String status,
		@Schema(description = "저장한 요약을 다시 썼는지 (모델을 부르지 않음)") boolean reused,
		@Schema(description = "자료 파일이나 요약 지시문이 바뀌어 다시 만들어야 하는지") boolean stale,
		@Schema(description = "stale일 때 이유 (사용자용 문구)") String staleReason,
		GeneratedBy generatedBy,
		String promptVersion,
		LocalDateTime createdAt,
		Verification verification,
		@Schema(description = "AI에 보낸 자료 위치 (서버가 고른 쪽·슬라이드·문단)") List<String> sourceScope,
		@Schema(description = "자료가 길어 일부만 보냈는지") boolean sourceTruncated,
		Content content,
		@Schema(description = "원문에서 확인한 인용의 위치와 개수") List<SourceRef> sources) {

	public record GeneratedBy(String provider, String model) {
	}

	@Schema(description = "검증 개수 (코드가 센 값). status: verified / partial / needs_check / insufficient")
	public record Verification(String status, int verified, int partial, int failed, int insufficient,
			@Schema(description = "자료에 없는 AI 보충 설명 수 (검증 대상 아님)") int supplement) {
	}

	@Schema(description = "요약 항목 하나")
	public record Item(
			@Schema(description = "개념 이름 (핵심 개념만)") String name,
			String text,
			@Schema(description = "material: 원문에 근거 / supplement: 자료에 없는 AI 보충 설명") String basis,
			@Schema(description = "원문에서 확인한 인용문 (확인하지 못했으면 null)") String quote,
			@Schema(description = "인용 위치 (서버가 원문에서 찾은 값)") String location,
			@Schema(description = "verified / partial / failed(원문에서 찾지 못함) / insufficient(인용 없음). supplement는 null") String check) {
	}

	public record SelfCheck(String question, String hint) {
	}

	@Schema(description = "요약 구성: 개요·핵심 개념(정의)·중요 원리·개념 간 관계·예시·혼동하기 쉬운 점·핵심 정리·스스로 확인할 질문")
	public record Content(Item overview, List<Item> concepts, List<Item> principles, List<Item> relations,
			List<Item> examples, List<Item> confusions, List<Item> keyPoints, List<SelfCheck> selfCheck) {
	}

	public record SourceRef(String location, int count) {
	}

	public static StudySummaryResponse none(Long materialId, String purpose) {
		return new StudySummaryResponse(materialId, purpose, "none", false, false, null, null, null, null, null,
				List.of(), false, null, List.of());
	}
}
