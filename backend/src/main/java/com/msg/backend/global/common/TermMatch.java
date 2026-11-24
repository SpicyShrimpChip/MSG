package com.msg.backend.global.common;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 글에서 용어(학습 키워드·개념 이름)를 찾는 규칙. 대소문자는 무시한다.
 * <ul>
 * <li><b>영문 용어:</b> 앞뒤 공백을 지운 용어가 ASCII 영문·숫자로 시작하고 ASCII 영문·숫자와 공백·{@code . / + # & -}로만 되어 있으면
 * (RSA, VLAN, TCP/IP, C++, Node.js) 낱말 경계에서만 찾는다. 찾은 곳 바로 앞과 바로 뒤 글자가 ASCII 영문·숫자가 아니어야 한다
 * (글의 처음·끝, 공백, 기호, 한글은 모두 경계다). 그래서 "ship"의 "ip"는 안 걸리고 "RSA를"·"TCP/IP"의 "IP"는 걸린다.</li>
 * <li><b>그 밖(한글 등이 든 용어):</b> 한글은 조사가 붙으므로 부분 문자열로 찾는다.</li>
 * </ul>
 * 정규식은 앞뒤로 한 글자씩만 보는 단순한 모양이라 긴 글에서도 글 길이에 비례해서만 돈다.
 * 학습 키워드로 대화를 고르는 규칙({@link #keyword})은 프론트 {@code frontend/src/utils/conversation/filter.ts}와 같다.
 * 둘이 다르면 화면에서 고른 대화가 분석 때 "키워드가 없다"로 빠지므로 한쪽을 고치면 다른 쪽도 같이 고친다.
 */
public final class TermMatch {

	private static final Pattern LATIN_TERM = Pattern.compile("[A-Za-z0-9][A-Za-z0-9 ./+#&-]*");

	private TermMatch() {
	}

	/** 영문·숫자로만 된 용어인지 (앞뒤 공백은 무시) */
	public static boolean isLatin(String term) {
		return term != null && LATIN_TERM.matcher(term.strip()).matches();
	}

	/**
	 * 자료 본문에서 영문 용어를 세는 정규식(SourceText, 소문자 글에 쓴다). 낱말 사이 공백은 없어도 되고 여러 칸이어도 된다
	 * (PDF 줄바꿈 때문). 앞 경계는 앞 글자 확인이라 붙어 나온 용어("c++c++")도 하나씩 센다.
	 */
	public static Pattern latinCountPattern(String term) {
		String[] words = term.strip().toLowerCase(Locale.ROOT).split("\\s+");
		StringBuilder regex = new StringBuilder("(?<![a-z0-9])");
		for (int i = 0; i < words.length; i++) {
			regex.append(i == 0 ? "" : "\\s*").append(Pattern.quote(words[i]));
		}
		regex.append("(?![a-z0-9])");
		return Pattern.compile(regex.toString());
	}

	/** 학습 키워드 하나를 찾을 준비를 한다. 정규식은 여기서 한 번만 만들고 메시지마다 다시 만들지 않는다 */
	public static Keyword keyword(String keyword) {
		String lower = keyword == null ? "" : keyword.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
		if (lower.isEmpty() || !isLatin(lower)) {
			return new Keyword(lower, null);
		}
		// 앞 경계는 앞 글자 확인 대신 "글 처음이거나 영문·숫자가 아닌 글자"로 쓴다 (프론트 JS 정규식과 같은 모양)
		return new Keyword(lower, Pattern.compile("(?:^|[^a-z0-9])" + Pattern.quote(lower) + "(?![a-z0-9])"));
	}

	/**
	 * 찾을 학습 키워드. lower는 공백을 한 칸으로 줄인 소문자 키워드, latin은 영문 키워드면 낱말 경계 정규식(아니면 null).
	 * 키워드 안의 공백은 넓히지 않는다(글에도 한 칸 공백으로 있어야 걸린다).
	 */
	public record Keyword(String lower, Pattern latin) {

		/** 소문자로 바꾼 글(lowerText)에 이 키워드가 있는지. 빈 키워드는 어디에도 없다 */
		public boolean foundIn(String lowerText) {
			if (lower.isEmpty() || lowerText == null) {
				return false;
			}
			return latin != null ? latin.matcher(lowerText).find() : lowerText.contains(lower);
		}
	}
}
