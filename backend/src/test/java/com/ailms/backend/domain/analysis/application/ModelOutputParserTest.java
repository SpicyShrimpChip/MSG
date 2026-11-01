package com.ailms.backend.domain.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ailms.backend.domain.analysis.entity.EvidenceKind;
import com.ailms.backend.domain.analysis.entity.UnderstandingStatus;
import com.ailms.backend.domain.conversation.entity.MessageRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

// 모델 답변 검증: 개념 목록, 상태 값, 근거 메시지 id·말한 사람, 사용자 근거가 없는 판단
class ModelOutputParserTest {

	private final ModelOutputParser parser = new ModelOutputParser(new ObjectMapper());

	// 보낸 메시지: 10(사용자), 11(AI), 12(사용자)
	private final AnalysisInput input = new AnalysisInput(
			List.of(new AnalysisInput.Concept("교착 상태", "교착 상태"), new AnalysisInput.Concept("paging", "Paging")),
			List.of(1L), Map.of(10L, MessageRole.USER, 11L, MessageRole.ASSISTANT, 12L, MessageRole.USER), 0, "");

	@Test
	void 약속한_형식이면_개념별_판단과_근거를_읽는다() throws Exception {
		var output = parser.parse("""
				```json
				{"concepts":[{"concept":"교착 상태","status":"partial",
				  "evidence":[{"messageId":10,"kind":"user_explanation","note":"조건 둘만 말함"},
				              {"messageId":"m11","kind":"ai_explanation","note":"AI가 나머지를 설명"}],
				  "reason":"일부만 설명했다","needsCheck":"순환 대기","recommendedActivity":"네 조건 정리"}]}
				```""", input);

		assertThat(output.judgments()).hasSize(1);
		var judgment = output.judgments().get(0);
		assertThat(judgment.concept().name()).isEqualTo("교착 상태");
		assertThat(judgment.status()).isEqualTo(UnderstandingStatus.PARTIAL);
		assertThat(judgment.adjustedReason()).isNull();
		assertThat(judgment.evidence()).extracting(ModelOutputParser.ParsedEvidence::messageId).containsExactly(10L, 11L);
		assertThat(judgment.reason()).isEqualTo("일부만 설명했다");
		assertThat(output.droppedEvidenceCount()).isZero();
	}

	@Test
	void 보내지_않은_메시지와_말한_사람이_맞지_않는_근거는_버린다() throws Exception {
		var output = parser.parse("""
				{"concepts":[{"concept":"교착 상태","status":"not_understood","evidence":[
				  {"messageId":999,"kind":"user_question"},
				  {"messageId":11,"kind":"user_explanation"},
				  {"messageId":10,"kind":"ai_explanation"},
				  {"messageId":12,"kind":"made_up"},
				  {"messageId":12,"kind":"user_question"}]}]}""", input);

		assertThat(output.droppedEvidenceCount()).isEqualTo(4);
		assertThat(output.judgments().get(0).evidence()).extracting(ModelOutputParser.ParsedEvidence::kind)
				.containsExactly(EvidenceKind.USER_QUESTION);
		assertThat(output.judgments().get(0).status()).isEqualTo(UnderstandingStatus.NOT_UNDERSTOOD);
	}

	@Test
	void AI_설명만_근거로_이해했다고_하면_근거_부족으로_보고_원래_판단은_남긴다() throws Exception {
		var output = parser.parse("""
				{"concepts":[{"concept":"교착 상태","status":"understood",
				  "evidence":[{"messageId":11,"kind":"ai_explanation"}]}]}""", input);

		var judgment = output.judgments().get(0);
		assertThat(judgment.reportedStatus()).isEqualTo(UnderstandingStatus.UNDERSTOOD);
		assertThat(judgment.status()).isEqualTo(UnderstandingStatus.INSUFFICIENT_EVIDENCE);
		assertThat(judgment.adjustedReason()).isEqualTo(ModelOutputParser.NO_USER_EVIDENCE_REASON);
	}

	@Test
	void 근거_부족은_근거가_없어도_그대로_둔다() throws Exception {
		var output = parser.parse("""
				{"concepts":[{"concept":"교착 상태","status":"insufficient_evidence","evidence":[]}]}""", input);

		assertThat(output.judgments().get(0).status()).isEqualTo(UnderstandingStatus.INSUFFICIENT_EVIDENCE);
		assertThat(output.judgments().get(0).adjustedReason()).isNull();
	}

	@Test
	void 목록에_없는_개념_틀린_상태_같은_개념_두번은_뺀다() throws Exception {
		var output = parser.parse("""
				{"concepts":[
				  {"concept":"세마포어","status":"understood","evidence":[{"messageId":10,"kind":"user_explanation"}]},
				  {"concept":"PAGING","status":"great","evidence":[]},
				  {"concept":"paging","status":"insufficient_evidence","evidence":[]},
				  {"concept":"Paging","status":"understood","evidence":[{"messageId":10,"kind":"user_explanation"}]}]}""",
				input);

		assertThat(output.judgments()).hasSize(1);
		assertThat(output.judgments().get(0).concept().name()).isEqualTo("Paging");
		assertThat(output.judgments().get(0).status()).isEqualTo(UnderstandingStatus.INSUFFICIENT_EVIDENCE);
	}

	@Test
	void JSON이_아니거나_판단이_하나도_없으면_형식_오류() {
		assertThatThrownBy(() -> parser.parse("죄송하지만 분석할 수 없어요", input))
				.isInstanceOf(ModelOutputParser.InvalidOutputException.class);
		assertThatThrownBy(() -> parser.parse("{\"result\":[]}", input))
				.isInstanceOf(ModelOutputParser.InvalidOutputException.class);
		assertThatThrownBy(() -> parser.parse("{\"concepts\":[{\"concept\":\"없는 개념\",\"status\":\"partial\"}]}", input))
				.isInstanceOf(ModelOutputParser.InvalidOutputException.class);
	}

	@Test
	void 너무_긴_글은_자른다() throws Exception {
		String longReason = "가".repeat(2000);
		var output = parser.parse("{\"concepts\":[{\"concept\":\"교착 상태\",\"status\":\"insufficient_evidence\","
				+ "\"reason\":\"" + longReason + "\"}]}", input);

		assertThat(output.judgments().get(0).reason()).hasSize(1000);
	}
}
