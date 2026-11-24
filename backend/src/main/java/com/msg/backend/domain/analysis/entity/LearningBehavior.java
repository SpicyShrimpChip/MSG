package com.msg.backend.domain.analysis.entity;

import java.util.Optional;

/**
 * 근거 메시지에 드러난 학습 행동 (AI가 분류, 사용자 메시지만). 상태를 정하는 근거의 성격을 나눈다.
 * <ul>
 * <li>이해의 근거(POSITIVE): 정확한 자기 설명(Chi et al., 1989의 자기 설명 효과 참고), 새 상황에 적용</li>
 * <li>이해 부족의 근거(NEGATIVE): 직접적인 이해 부족 표현, 잘못된 자기 설명(오개념 후보)</li>
 * <li>판단에 쓰지 않는 신호(NEUTRAL): 재설명 요청(이해 확인이 필요한 신호일 뿐), 심화 질문(개념을 넓히려는 질문일 수 있음),
 * "알겠어"·"고마워"처럼 판단 근거가 없는 반응</li>
 * </ul>
 * 질문 횟수나 재설명 요청만으로 이해 부족을 확정하지 않고, "알겠어"만으로 이해를 확정하지 않는다(ModelOutputParser 규칙).
 */
public enum LearningBehavior {
	/** "이 개념이 뭔지 모르겠어" 같은 직접적인 이해 부족 표현 */
	CONFUSION_EXPRESSED(Signal.NEGATIVE, false),
	/** "다시 쉽게 설명해 줘" (이해 확인 필요 신호, 이해 부족 확정 아님) */
	RE_EXPLANATION_REQUEST(Signal.NEUTRAL, true),
	/** "그럼 NAT와 PAT는 어떤 차이가 있어?" 같은 심화 질문 (약점으로 보지 않음) */
	DEEPENING_QUESTION(Signal.NEUTRAL, true),
	/** 사용자가 원리·관계를 잘못 설명함 (오개념 후보) */
	INCORRECT_EXPLANATION(Signal.NEGATIVE, false),
	/** 사용자가 자기 말로 정확히 설명함 */
	CORRECT_EXPLANATION(Signal.POSITIVE, false),
	/** 사용자가 배운 개념을 다른 문제·실제 상황에 알맞게 적용함 */
	APPLICATION(Signal.POSITIVE, false),
	/** "알겠어"·"고마워"처럼 이해를 판단할 수 없는 반응 */
	NO_SIGNAL(Signal.NEUTRAL, false);

	public enum Signal {
		POSITIVE, NEGATIVE, NEUTRAL
	}

	private final Signal signal;
	// 질문 메시지(user_question)에만 붙는 행동인지
	private final boolean questionOnly;

	LearningBehavior(Signal signal, boolean questionOnly) {
		this.signal = signal;
		this.questionOnly = questionOnly;
	}

	public Signal signal() {
		return signal;
	}

	/** 근거 종류와 맞는 행동인지: 재설명 요청·심화 질문은 질문에만, 설명·적용은 질문이 아닌 사용자 글에만. 이해 부족 표현·근거 없음은 어디든 */
	public boolean fits(EvidenceKind kind) {
		if (!kind.writtenByUser()) {
			return false;
		}
		if (this == CONFUSION_EXPRESSED || this == NO_SIGNAL) {
			return true;
		}
		return questionOnly == (kind == EvidenceKind.USER_QUESTION);
	}

	public String apiValue() {
		return ApiValues.of(this);
	}

	public static Optional<LearningBehavior> fromApiValue(String value) {
		return ApiValues.parse(LearningBehavior.class, value);
	}
}
