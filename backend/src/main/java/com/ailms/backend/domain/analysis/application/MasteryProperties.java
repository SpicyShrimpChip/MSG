package com.ailms.backend.domain.analysis.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 숙련도 점수 설정 (app.mastery). 모든 값은 근거 없는 초기 가정이라 사용자 테스트 뒤 조정한다.
 * 산식을 바꾸면 formulaVersion을 올린다. 설계: docs/frontend-requests.md 5-10절.
 *
 * @param formulaVersion       산식 버전 (결과에 함께 내려 이전 결과와 구분한다)
 * @param quizWeight           퀴즈 문제 하나의 근거 무게
 * @param conversationWeight   대화 한 건(대화 하나)의 근거 무게. 퀴즈보다 간접 신호라 더 작게 둔다
 * @param understoodValue      대화에서 스스로 활용한 것으로 판단됐을 때의 값 (0~1)
 * @param partialValue         대화에서 부분 이해(재설명 요청)로 판단됐을 때의 값 (0~1)
 * @param notUnderstoodValue   대화에서 이해 못 함으로 판단됐을 때의 값 (0~1)
 * @param minEvidenceWeight    근거 무게 합이 이보다 작으면 점수를 내지 않고 "근거 부족"으로 둔다
 * @param needsWorkBelow       이 점수 미만이면 보완 필요
 * @param strongFrom           이 점수 이상이면 강점 (그 사이는 보통)
 */
@ConfigurationProperties(prefix = "app.mastery")
public record MasteryProperties(String formulaVersion, double quizWeight, double conversationWeight,
		double understoodValue, double partialValue, double notUnderstoodValue, double minEvidenceWeight,
		double needsWorkBelow, double strongFrom) {
}
