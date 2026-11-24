package com.msg.backend.domain.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.msg.backend.domain.analysis.entity.EvidenceKind;
import com.msg.backend.domain.analysis.entity.LearningBehavior;
import com.msg.backend.domain.analysis.entity.UnderstandingStatus;
import com.msg.backend.domain.conversation.entity.MessageRole;
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
		// 남은 근거가 질문 하나뿐: 질문만으로는 이해 부족을 확정하지 않는다 (v2 규칙)
		assertThat(output.judgments().get(0).reportedStatus()).isEqualTo(UnderstandingStatus.NOT_UNDERSTOOD);
		assertThat(output.judgments().get(0).status()).isEqualTo(UnderstandingStatus.INSUFFICIENT_EVIDENCE);
		assertThat(output.judgments().get(0).adjustedReason()).isEqualTo(ModelOutputParser.QUESTION_ONLY_REASON);
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

	// ---------- 학습 행동 (concept-analysis-v2) ----------

	private ModelOutputParser.ParsedJudgment judge(String status, String evidence) throws Exception {
		return judge(status, evidence, "");
	}

	private ModelOutputParser.ParsedJudgment judge(String status, String evidence, String extra) throws Exception {
		return parser.parse("{\"concepts\":[{\"concept\":\"교착 상태\",\"status\":\"" + status + "\",\"evidence\":["
				+ evidence + "]" + extra + "}]}", input).judgments().get(0);
	}

	@Test
	void 직접적인_이해_부족_표현은_이해_부족의_근거다() throws Exception {
		var judgment = judge("not_understood",
				"{\"messageId\":10,\"kind\":\"user_question\",\"behavior\":\"confusion_expressed\"}");
		assertThat(judgment.status()).isEqualTo(UnderstandingStatus.NOT_UNDERSTOOD);
		assertThat(judgment.evidence().get(0).behavior()).isEqualTo(LearningBehavior.CONFUSION_EXPRESSED);
	}

	@Test
	void 재설명_요청만으로는_이해_부족을_확정하지_않는다() throws Exception {
		var judgment = judge("not_understood",
				"{\"messageId\":10,\"kind\":\"user_question\",\"behavior\":\"re_explanation_request\"},"
						+ "{\"messageId\":12,\"kind\":\"user_question\",\"behavior\":\"re_explanation_request\"}");
		assertThat(judgment.status()).isEqualTo(UnderstandingStatus.INSUFFICIENT_EVIDENCE);
		assertThat(judgment.adjustedReason()).isEqualTo(ModelOutputParser.QUESTION_ONLY_REASON);
	}

	@Test
	void 심화_질문은_약점으로_보지_않는다() throws Exception {
		var judgment = judge("partial",
				"{\"messageId\":10,\"kind\":\"user_question\",\"behavior\":\"deepening_question\"}");
		assertThat(judgment.status()).isEqualTo(UnderstandingStatus.INSUFFICIENT_EVIDENCE);
		assertThat(judgment.adjustedReason()).isEqualTo(ModelOutputParser.DEEPENING_ONLY_REASON);
	}

	@Test
	void 잘못된_자기_설명은_오개념_후보로_남기고_근거가_없으면_버린다() throws Exception {
		var judgment = judge("not_understood",
				"{\"messageId\":10,\"kind\":\"user_explanation\",\"behavior\":\"incorrect_explanation\"}",
				",\"misconception\":\"NAT의 주소 변환과 방화벽의 접근 통제를 혼동\"");
		assertThat(judgment.status()).isEqualTo(UnderstandingStatus.NOT_UNDERSTOOD);
		assertThat(judgment.misconception()).contains("방화벽");

		var withoutIncorrect = judge("partial",
				"{\"messageId\":10,\"kind\":\"user_explanation\",\"behavior\":\"correct_explanation\"}",
				",\"misconception\":\"근거 없는 오개념\"");
		assertThat(withoutIncorrect.misconception()).isNull();
	}

	@Test
	void 정확한_자기_설명과_새_상황_적용은_이해의_근거다() throws Exception {
		assertThat(judge("understood",
				"{\"messageId\":10,\"kind\":\"user_explanation\",\"behavior\":\"correct_explanation\"}").status())
				.isEqualTo(UnderstandingStatus.UNDERSTOOD);
		assertThat(judge("understood",
				"{\"messageId\":12,\"kind\":\"user_solution\",\"behavior\":\"application\"}").status())
				.isEqualTo(UnderstandingStatus.UNDERSTOOD);
	}

	@Test
	void 알겠어만으로는_이해를_확정하지_않는다() throws Exception {
		var judgment = judge("understood",
				"{\"messageId\":10,\"kind\":\"user_explanation\",\"behavior\":\"no_signal\"},"
						+ "{\"messageId\":11,\"kind\":\"ai_explanation\"}");
		assertThat(judgment.status()).isEqualTo(UnderstandingStatus.INSUFFICIENT_EVIDENCE);
		assertThat(judgment.adjustedReason()).isEqualTo(ModelOutputParser.NO_SELF_EXPLANATION_REASON);
	}

	@Test
	void 근거_종류와_맞지_않는_행동과_AI_메시지의_행동은_버린다() throws Exception {
		// 질문에 정확한 설명, AI 설명에 적용: 행동만 버리고 근거는 남긴다 → 이해를 받쳐 줄 근거가 없다
		var judgment = judge("understood",
				"{\"messageId\":10,\"kind\":\"user_question\",\"behavior\":\"correct_explanation\"},"
						+ "{\"messageId\":11,\"kind\":\"ai_explanation\",\"behavior\":\"application\"}");
		assertThat(judgment.evidence()).hasSize(2);
		assertThat(judgment.evidence()).extracting(ModelOutputParser.ParsedEvidence::behavior).containsOnlyNulls();
		assertThat(judgment.status()).isEqualTo(UnderstandingStatus.INSUFFICIENT_EVIDENCE);
	}

	@Test
	void 행동이_없는_예전_답변은_사용자_설명이면_그대로_받는다() throws Exception {
		assertThat(judge("not_understood", "{\"messageId\":10,\"kind\":\"user_explanation\"}").status())
				.isEqualTo(UnderstandingStatus.NOT_UNDERSTOOD);
	}
}
