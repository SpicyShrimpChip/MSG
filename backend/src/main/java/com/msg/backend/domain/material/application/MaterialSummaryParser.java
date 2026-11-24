package com.msg.backend.domain.material.application;

import com.msg.backend.domain.material.dto.StudySummaryResponse;
import com.msg.backend.global.common.Texts;
import com.msg.backend.global.common.VerificationStatus;
import com.msg.backend.global.llm.LlmJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 모델이 쓴 요약(JSON)을 검증한다 (FActScore처럼 요약을 항목 단위 주장으로 나눠 원문 근거와 잇는 방식을 참고했고, 그 방법 전체를 구현한 것은 아니다).
 * <ul>
 * <li>항목마다 basis를 받는다: material(원문에 근거)이면 quote를 실제로 보낸 글에서 확인한다(SourceText.check). 위치는 서버가 찾는다.
 * 인용이 없으면 insufficient(근거 부족), 찾지 못하면 failed(검증 실패), 일부만 맞으면 partial.</li>
 * <li>supplement(자료에 없는 AI 보충 설명)는 검증하지 않고 따로 표시한다. basis가 없으면 원문 근거라고 주장한 것으로 본다.</li>
 * <li>개요는 꼭 있어야 한다. 목록은 개수 상한까지만, 글은 길이 상한까지만 남긴다.</li>
 * <li>전체 상태: 원문 근거 항목이 없음 → insufficient, 모두 verified → verified, 확인(verified·partial)이 절반 이상 → partial,
 * 하나라도 확인 → needs_check, 하나도 확인하지 못함 → 결과를 버린다(다음 모델로 넘어간다).</li>
 * </ul>
 * 인용이 원문에 있다는 것만 확인한다. 그 인용을 바탕으로 쓴 설명이 사실인지는 확정하지 않는다.
 */
@Component
public class MaterialSummaryParser {

	static final int TEXT_MAX = 500;
	static final int NAME_MAX = 40;
	static final int MAX_CONCEPTS = 10;
	static final int MAX_ITEMS = 6;
	static final int MAX_SELF_CHECK = 5;

	private final ObjectMapper objectMapper;

	public MaterialSummaryParser(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public record Parsed(StudySummaryResponse.Content content, StudySummaryResponse.Verification verification,
			List<StudySummaryResponse.SourceRef> sources) {
	}

	public Optional<Parsed> parse(String raw, String sentText) {
		Optional<JsonNode> root = LlmJson.readObject(objectMapper, raw);
		if (root.isEmpty()) {
			return Optional.empty();
		}
		Counter counter = new Counter();
		JsonNode node = root.get();
		StudySummaryResponse.Item overview = item(node.get("overview"), sentText, false, counter);
		if (overview == null) {
			return Optional.empty();
		}
		StudySummaryResponse.Content content = new StudySummaryResponse.Content(overview,
				items(node.get("concepts"), sentText, true, MAX_CONCEPTS, counter),
				items(node.get("principles"), sentText, false, MAX_ITEMS, counter),
				items(node.get("relations"), sentText, false, MAX_ITEMS, counter),
				items(node.get("examples"), sentText, false, MAX_ITEMS, counter),
				items(node.get("confusions"), sentText, false, MAX_ITEMS, counter),
				items(node.get("keyPoints"), sentText, false, MAX_ITEMS, counter),
				selfCheck(node.get("selfCheck")));
		int material = counter.verified + counter.partial + counter.failed + counter.insufficient;
		int confirmed = counter.verified + counter.partial;
		VerificationStatus status;
		if (material == 0) {
			status = VerificationStatus.INSUFFICIENT;
		} else if (counter.verified == material) {
			status = VerificationStatus.VERIFIED;
		} else if (confirmed * 2 >= material) {
			status = VerificationStatus.PARTIAL;
		} else if (confirmed > 0) {
			status = VerificationStatus.NEEDS_CHECK;
		} else {
			// 원문 근거라고 한 항목을 하나도 확인하지 못했다: 지어낸 요약일 수 있어 저장하지 않는다
			return Optional.empty();
		}
		List<StudySummaryResponse.SourceRef> sources = counter.locations.entrySet().stream()
				.map(entry -> new StudySummaryResponse.SourceRef(entry.getKey(), entry.getValue())).toList();
		return Optional.of(new Parsed(content, new StudySummaryResponse.Verification(status.apiValue(),
				counter.verified, counter.partial, counter.failed, counter.insufficient, counter.supplement), sources));
	}

	private static final class Counter {
		int verified;
		int partial;
		int failed;
		int insufficient;
		int supplement;
		final Map<String, Integer> locations = new LinkedHashMap<>();
	}

	private static List<StudySummaryResponse.Item> items(JsonNode node, String sentText, boolean named, int max,
			Counter counter) {
		List<StudySummaryResponse.Item> items = new ArrayList<>();
		if (node == null || !node.isArray()) {
			return items;
		}
		for (JsonNode element : node) {
			if (items.size() >= max) {
				break;
			}
			StudySummaryResponse.Item item = item(element, sentText, named, counter);
			if (item != null) {
				items.add(item);
			}
		}
		return items;
	}

	private static StudySummaryResponse.Item item(JsonNode node, String sentText, boolean named, Counter counter) {
		if (node == null || !node.isObject()) {
			return null;
		}
		String text = Texts.clip(LlmJson.text(node.get(named ? "definition" : "text")), TEXT_MAX);
		if (text == null && named) {
			text = Texts.clip(LlmJson.text(node.get("text")), TEXT_MAX);
		}
		String name = named ? Texts.clip(LlmJson.text(node.get("name")), NAME_MAX) : null;
		if (text == null || (named && name == null)) {
			return null;
		}
		boolean supplement = "supplement".equalsIgnoreCase(LlmJson.text(node.get("basis")));
		if (supplement) {
			counter.supplement++;
			return new StudySummaryResponse.Item(name, text, "supplement", null, null, null);
		}
		SourceText.QuoteCheck check = SourceText.check(sentText, LlmJson.text(node.get("quote")));
		switch (check.status()) {
			case VERIFIED -> counter.verified++;
			case PARTIAL -> counter.partial++;
			case FAILED -> counter.failed++;
			default -> counter.insufficient++;
		}
		if (check.location() != null) {
			counter.locations.merge(check.location(), 1, Integer::sum);
		}
		return new StudySummaryResponse.Item(name, text, "material", check.quote(), check.location(),
				check.status().apiValue());
	}

	private static List<StudySummaryResponse.SelfCheck> selfCheck(JsonNode node) {
		List<StudySummaryResponse.SelfCheck> items = new ArrayList<>();
		if (node == null || !node.isArray()) {
			return items;
		}
		for (JsonNode element : node) {
			if (items.size() >= MAX_SELF_CHECK) {
				break;
			}
			String question = Texts.clip(LlmJson.text(element.get("question")), TEXT_MAX);
			if (question != null) {
				items.add(new StudySummaryResponse.SelfCheck(question, Texts.clip(LlmJson.text(element.get("hint")), TEXT_MAX)));
			}
		}
		return items;
	}
}
