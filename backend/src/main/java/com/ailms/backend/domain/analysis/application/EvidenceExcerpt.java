package com.ailms.backend.domain.analysis.application;

import java.util.regex.Pattern;

/**
 * 판단 근거로 보여 줄 메시지 앞부분. 저장된 대화는 바꾸지 않고 화면에 보낼 글만 만든다.
 * AI 답변의 마크다운 표시(제목·목록·인용 기호, 굵게, 코드 표시, 링크)를 걷어 내고 단어 경계에서 자른다.
 * 별표·밑줄 하나짜리 기울임은 건드리지 않는다: 수식(C_i)이나 곱셈(3*5)이 깨지는 쪽이 더 나쁘다.
 * 긴 메시지에 정규식을 돌리지 않도록 앞부분(HEAD_LENGTH)만 떼어 처리한다.
 */
final class EvidenceExcerpt {

	static final int MAX_LENGTH = 120;
	static final String ELLIPSIS = "…";
	// 마크다운을 걷으면 글이 줄어드니 보여 줄 길이보다 넉넉히 뗀다
	private static final int HEAD_LENGTH = 400;
	// 단어 경계를 찾느라 이보다 많이 버리면 그냥 MAX_LENGTH에서 자른다
	private static final int MAX_WORD_BACKTRACK = 20;

	private static final Pattern FENCE = Pattern.compile("^\\s*(```|~~~).*$");
	private static final Pattern RULE = Pattern.compile("^\\s*([-*_])(\\s*\\1){2,}\\s*$");
	private static final Pattern LINE_MARK = Pattern.compile("^\\s{0,3}(#{1,6}\\s+|>\\s?|[-*+]\\s+|\\d{1,3}[.)]\\s+)");
	private static final Pattern LINK = Pattern.compile("!?\\[([^\\]\\n]{0,200})]\\([^)\\s]{0,500}\\)");
	private static final Pattern BOLD_STAR = Pattern.compile("\\*\\*(?=\\S)(.+?)(?<=\\S)\\*\\*");
	private static final Pattern BOLD_UNDERSCORE = Pattern.compile("(?<!\\w)__(?=\\S)(.+?)(?<=\\S)__(?!\\w)");
	private static final Pattern INLINE_CODE = Pattern.compile("`([^`\\n]+)`");
	private static final Pattern SPACES = Pattern.compile("\\s+");

	private EvidenceExcerpt() {
	}

	static String of(String content) {
		if (content == null || content.isEmpty()) {
			return "";
		}
		boolean truncated = content.length() > HEAD_LENGTH;
		String text = plain(truncated ? safeCut(content, HEAD_LENGTH) : content);
		if (text.length() <= MAX_LENGTH) {
			return truncated && !text.isEmpty() ? text + ELLIPSIS : text;
		}
		String cut = safeCut(text, MAX_LENGTH);
		int space = cut.lastIndexOf(' ');
		if (space >= MAX_LENGTH - MAX_WORD_BACKTRACK) {
			cut = cut.substring(0, space);
		}
		return cut.stripTrailing() + ELLIPSIS;
	}

	private static String plain(String text) {
		StringBuilder lines = new StringBuilder();
		for (String line : text.split("\\R", -1)) {
			if (FENCE.matcher(line).matches() || RULE.matcher(line).matches()) {
				continue;
			}
			// "> - 항목"처럼 겹친 표시도 걷는다
			for (int i = 0; i < 3; i++) {
				String stripped = LINE_MARK.matcher(line).replaceFirst("");
				if (stripped.equals(line)) {
					break;
				}
				line = stripped;
			}
			lines.append(line).append(' ');
		}
		String result = LINK.matcher(lines).replaceAll("$1");
		result = BOLD_STAR.matcher(result).replaceAll("$1");
		// 앞부분만 떼어 짝이 잘린 굵게 표시
		result = result.replace("**", "");
		result = BOLD_UNDERSCORE.matcher(result).replaceAll("$1");
		result = INLINE_CODE.matcher(result).replaceAll("$1");
		return SPACES.matcher(result).replaceAll(" ").strip();
	}

	// 이모지처럼 두 글자로 된 문자를 반으로 자르지 않는다
	private static String safeCut(String text, int length) {
		int end = Character.isHighSurrogate(text.charAt(length - 1)) ? length - 1 : length;
		return text.substring(0, end);
	}
}
