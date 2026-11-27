package com.msg.backend.domain.analysis.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 숙련도 점수 설정 (app.mastery). 모든 값은 근거 없는 초기 가정이라 사용자 테스트 뒤 조정한다.
 * 식 모양을 바꾸면 formulaVersion을 올린다. 숫자만 바꾸면 effectiveFormulaVersion의 지문이 저절로 바뀐다.
 * 설계: docs/frontend-requests.md 5-10절.
 *
 * <p>생성자는 이 하나만 둔다(둘 이상이면 설정 바인딩이 깨진다, docs/wiki/backend.md).</p>
 *
 * @param formulaVersion       산식 앞 번호 (식 모양을 바꿀 때만 올린다. '-'를 쓰지 않는다)
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

	// concept_mastery.formula_version VARCHAR(20)
	public static final int MAX_FORMULA_VERSION_LENGTH = 20;
	private static final int FINGERPRINT_LENGTH = 8;

	// 잘못된 설정이면 서버가 시작하지 않는다 (기록 저장 때 길이 초과로 실패하지 않게)
	public MasteryProperties {
		if (formulaVersion == null || formulaVersion.isBlank() || formulaVersion.contains("-")) {
			throw new IllegalArgumentException("app.mastery.formula-version은 비어 있지 않고 '-'가 없어야 합니다");
		}
		if (formulaVersion.length() + 1 + FINGERPRINT_LENGTH > MAX_FORMULA_VERSION_LENGTH) {
			throw new IllegalArgumentException("app.mastery.formula-version이 너무 깁니다 (지문을 붙여 "
					+ MAX_FORMULA_VERSION_LENGTH + "자 이하)");
		}
	}

	/**
	 * 결과·기록·응답에 쓰는 산식 버전: 앞 번호 + "-" + 숫자 설정 전부를 정해진 순서로 이은 문자열의 SHA-256 앞 8자리.
	 * 예: "2-a1b3c9d2". 숫자만 바꿔도 버전이 달라져 앞 기록과 단계를 바로 비교하지 않는다.
	 */
	public String effectiveFormulaVersion() {
		String settings = "quiz-weight=" + quizWeight
				+ ";conversation-weight=" + conversationWeight
				+ ";understood-value=" + understoodValue
				+ ";partial-value=" + partialValue
				+ ";not-understood-value=" + notUnderstoodValue
				+ ";min-evidence-weight=" + minEvidenceWeight
				+ ";needs-work-below=" + needsWorkBelow
				+ ";strong-from=" + strongFrom;
		return formulaVersion + "-" + sha256Hex(settings).substring(0, FINGERPRINT_LENGTH);
	}

	private static String sha256Hex(String text) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256을 쓸 수 없습니다", e);
		}
	}
}
