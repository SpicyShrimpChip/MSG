package com.msg.backend.domain.analysis.application;

import com.msg.backend.domain.analysis.entity.UnderstandingStatus;
import com.msg.backend.domain.quiz.dto.ConceptQuizStatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 개념 하나의 숙련도를 규칙대로 계산한다. LLM은 점수를 정하지 않고 이 값을 설명만 한다.
 *
 * <p>근거마다 값 s(0~1)와 무게 w를 두고, 점수 = (Σw·s + 1) ÷ (Σw + 2)다. 더하기 1·2는 근거가 한두 개일 때
 * 점수가 0이나 1로 튀지 않게 하는 보정이다. 근거 무게 합이 기준보다 작으면 점수를 내지 않고
 * 근거 부족으로 둔다 (이해 부족으로 바꾸지 않는다). 설계: docs/frontend-requests.md 5-10절.</p>
 *
 * <ul>
 * <li>퀴즈: 채점이 끝난 문제마다 무게 quizWeight, 값은 득점률(점수 ÷ 만점). 대기·확인 필요 답은 세지 않는다.</li>
 * <li>대화: 근거가 된 대화 한 건마다 무게 conversationWeight, 값은 모델 비교가 일치한 상태에 대응한 값.
 * 의견 불일치·근거 부족·결과 없음은 대화 근거로 세지 않는다.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class MasteryCalculator {

	public enum Level {
		INSUFFICIENT_EVIDENCE("insufficient_evidence"), NEEDS_WORK("needs_work"), NORMAL("normal"), STRONG("strong");

		private final String apiValue;

		Level(String apiValue) {
			this.apiValue = apiValue;
		}

		public String apiValue() {
			return apiValue;
		}
	}

	/**
	 * @param level               단계
	 * @param score               점수 (0~1). 근거 부족이면 null. 화면은 숫자 대신 단계와 근거를 보여 준다
	 * @param evidenceWeight      모은 근거 무게 합
	 * @param quizGradedCount     세어 넣은 퀴즈 문제 수
	 * @param quizRate            문제별 평균 득점률 (0~1, 문제마다 점수 ÷ 만점의 평균). 퀴즈 근거가 없으면 null
	 * @param conversationCount   세어 넣은 대화 수
	 * @param conversationStatus  대화에 적용한 상태. 대화 근거를 세지 않았으면 null
	 * @param formulaVersion      산식 버전 (앞 번호 + 숫자 설정 지문, MasteryProperties.effectiveFormulaVersion)
	 * @param quizNeeded          근거 부족일 때 단계가 나오려면 더 풀어야 하는 퀴즈 문제 수(모두 채점된다고 보고 셈).
	 *                            근거가 충분하면 null
	 */
	public record Result(Level level, Double score, double evidenceWeight, int quizGradedCount, Double quizRate,
			int conversationCount, UnderstandingStatus conversationStatus, String formulaVersion,
			Integer quizNeeded) {
	}

	private final MasteryProperties properties;

	/**
	 * 개념별 퀴즈 결과로 계산한다. 퀴즈 근거는 문제마다 센 득점률 합(ratedCount·rateSum)만 쓰고, 점수 합 비율
	 * (score ÷ maxScore)은 쓰지 않는다. 점수 합 비율을 쓰면 만점이 큰 서술형 하나가 결과를 좌우한다.
	 *
	 * @param quiz                개념별 퀴즈 결과. 없으면 null
	 * @param conversationCount   이 개념의 근거가 된 대화 수 (사용자가 쓴 근거 메시지가 있는 대화)
	 * @param conversationStatus  모델 비교가 일치한 상태. 의견 불일치·결과 없음이면 null
	 */
	public Result calculate(ConceptQuizStatsResponse quiz, int conversationCount,
			UnderstandingStatus conversationStatus) {
		return quiz == null ? calculate(0, 0.0, conversationCount, conversationStatus)
				: calculate(quiz.ratedCount(), quiz.rateSum(), conversationCount, conversationStatus);
	}

	/**
	 * @param quizRatedCount      채점이 끝나고 만점이 있는 퀴즈 문제 수
	 * @param quizRateSum         그 문제마다 (점수 ÷ 그 문제 만점)을 더한 값
	 * @param conversationCount   이 개념의 근거가 된 대화 수 (사용자가 쓴 근거 메시지가 있는 대화)
	 * @param conversationStatus  모델 비교가 일치한 상태. 의견 불일치·결과 없음이면 null
	 */
	public Result calculate(int quizRatedCount, double quizRateSum, int conversationCount,
			UnderstandingStatus conversationStatus) {
		double weightSum = 0;
		double weightedValueSum = 0;

		int quizCount = 0;
		Double quizRate = null;
		if (quizRatedCount > 0) {
			quizCount = quizRatedCount;
			double rateSum = Math.max(0, Math.min(quizRatedCount, quizRateSum));
			quizRate = rateSum / quizRatedCount;
			weightSum += quizCount * properties.quizWeight();
			weightedValueSum += properties.quizWeight() * rateSum;
		}

		int usedConversationCount = 0;
		UnderstandingStatus usedStatus = null;
		Double conversationValue = conversationValue(conversationStatus);
		if (conversationCount > 0 && conversationValue != null) {
			usedConversationCount = conversationCount;
			usedStatus = conversationStatus;
			weightSum += usedConversationCount * properties.conversationWeight();
			weightedValueSum += usedConversationCount * properties.conversationWeight() * conversationValue;
		}

		if (weightSum < properties.minEvidenceWeight()) {
			return new Result(Level.INSUFFICIENT_EVIDENCE, null, weightSum, quizCount, quizRate,
					usedConversationCount, usedStatus, properties.effectiveFormulaVersion(), quizNeeded(weightSum));
		}
		double score = (weightedValueSum + 1) / (weightSum + 2);
		Level level = score < properties.needsWorkBelow() ? Level.NEEDS_WORK
				: score >= properties.strongFrom() ? Level.STRONG : Level.NORMAL;
		return new Result(level, score, weightSum, quizCount, quizRate, usedConversationCount, usedStatus,
				properties.effectiveFormulaVersion(), null);
	}

	// 기준까지 남은 무게를 퀴즈 한 문제 무게로 나눠 올림한다. 0.1+0.2 같은 소수 오차로 한 문제 더 세지 않게 아주 작은 값을 뺀다
	private Integer quizNeeded(double weightSum) {
		if (properties.quizWeight() <= 0) {
			return null;
		}
		double remaining = properties.minEvidenceWeight() - weightSum;
		return Math.max(1, (int) Math.ceil(remaining / properties.quizWeight() - 1e-9));
	}

	private Double conversationValue(UnderstandingStatus status) {
		if (status == null) {
			return null;
		}
		return switch (status) {
			case UNDERSTOOD -> clamp(properties.understoodValue());
			case PARTIAL -> clamp(properties.partialValue());
			case NOT_UNDERSTOOD -> clamp(properties.notUnderstoodValue());
			default -> null;
		};
	}

	private static double clamp(double value) {
		return Math.max(0, Math.min(1, value));
	}
}
