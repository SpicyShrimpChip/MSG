package com.ailms.backend.domain.quiz.application;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 주관식 답을 허용 답과 코드로 비교한다. 표기 차이(전각·반각, 대소문자, 띄어쓰기, 일부 문장 부호)만 맞추고 의미는 판단하지 않는다.
 * 일치하지 않는 답은 오답으로 단정하지 않고 AI 의미 판단으로 넘긴다(AnswerGradingService).
 * '+', '#'처럼 뜻이 바뀌는 기호는 지우지 않는다 (C++와 C를 같게 보지 않게).
 */
public final class ShortAnswerMatcher {

	// 공백, 하이픈·밑줄, 마침표·쉼표·따옴표·괄호·가운뎃점·슬래시
	private static final Pattern IGNORED = Pattern.compile("[\\s\\-_.,;:!?'\"`()\\[\\]{}·/]");

	private ShortAnswerMatcher() {
	}

	public static String normalize(String text) {
		if (text == null) {
			return "";
		}
		String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
		return IGNORED.matcher(normalized).replaceAll("");
	}

	public static boolean matches(String answer, List<String> acceptedAnswers) {
		String normalized = normalize(answer);
		if (normalized.isEmpty()) {
			return false;
		}
		return acceptedAnswers.stream().map(ShortAnswerMatcher::normalize).anyMatch(normalized::equals);
	}
}
