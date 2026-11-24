package com.msg.backend.domain.material.application;

import com.msg.backend.global.common.TermMatch;
import com.msg.backend.global.common.VerificationStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 자료 본문(MaterialTextExtractor 결과)을 출처 위치가 붙은 부분으로 나누고, 관련 부분만 고르고, AI가 든 인용문을 원문에서 확인한다.
 * 모두 코드 규칙이고 외부로 아무것도 보내지 않는다.
 *
 * <ul>
 * <li>부분: PDF는 "[p. N]", PPTX는 "[슬라이드 N]" 표시마다 한 부분. 표시가 없는 글(TXT·MD)은 빈 줄로 나눈 문단마다 "[문단 N]"을 붙인다.</li>
 * <li>고르기(select): 개념 이름이 많이 나오는 부분부터 글자 수 예산 안에서 고른다. 개념이 나오지 않는 부분은 예산이 남아도 보내지 않는다.
 * 어느 부분에도 개념이 나오지 않으면 앞에서부터 채운다(filtered=false, 화면에 "관련 부분을 찾지 못해 앞부분으로 만들었어요"를 보여 줄 수 있게). 고른 부분은 원래 순서로, 위치 표시를 붙여 돌려준다.</li>
 * <li>인용 확인(check): 실제로 보낸 글 안에서 찾는다. 공백·대소문자 차이는 무시한다(PDF 줄바꿈 때문). 그대로 있으면 VERIFIED,
 * 한 부분 안에 인용 낱말이 60% 이상 있으면 PARTIAL, 아니면 FAILED, 인용이 없거나 너무 짧으면(10자 미만, 지시문은 10~200자) INSUFFICIENT.
 * PARTIAL의 인용은 원문이 아니라 AI가 쓴 문장이므로 돌려주지 않는다(위치만 준다). 화면이 원문 인용으로 보여 주지 않게.
 * 위치는 AI가 적은 값이 아니라 인용을 찾은 자리 앞의 마지막 위치 표시다.</li>
 * </ul>
 * 인용이 원문에 있다는 것만 확인한다. 그 인용을 바탕으로 쓴 설명·정답이 사실인지는 확정하지 않는다.
 */
public final class SourceText {

	public static final int MIN_QUOTE_LENGTH = 10;
	public static final int MAX_QUOTE_LENGTH = 300;
	static final double PARTIAL_TOKEN_RATE = 0.6;
	static final int PARTIAL_MIN_TOKENS = 3;
	private static final Pattern MARKER_LINE = Pattern.compile("(?m)^\\[(p\\. \\d+|슬라이드 \\d+|문단 \\d+)]\\s*$");
	// 공백을 지운 글에서 찾는 위치 표시
	private static final Pattern COMPACT_MARKER = Pattern.compile("\\[(p\\.(\\d+)|슬라이드(\\d+)|문단(\\d+))]");
	private static final Pattern TOKEN_SPLIT = Pattern.compile("[\\s\\p{Punct}·“”‘’「」『』()（）]+");

	/** 위치가 붙은 본문 한 부분. location이 null이면 첫 위치 표시 앞의 글 */
	public record Section(int index, String location, String text) {

		String render() {
			return location == null ? text : "[" + location + "]\n" + text;
		}
	}

	/**
	 * 고른 본문. text는 위치 표시를 붙인 글(AI에 보내는 값), locations는 고른 위치(원래 순서),
	 * filtered는 개념이 나오는 부분을 골랐는지(false면 앞에서부터 채움), truncated는 예산 때문에 빠진 부분이 있는지
	 */
	public record Selection(String text, List<String> locations, boolean filtered, boolean truncated) {
	}

	/** 인용 확인 결과. quote는 원문에 그대로 있는 인용(VERIFIED일 때만, 나머지는 null), location은 서버가 찾은 위치 */
	public record QuoteCheck(VerificationStatus status, String quote, String location, String sectionText) {
	}

	private final List<Section> sections;

	private SourceText(List<Section> sections) {
		this.sections = sections;
	}

	public static SourceText parse(String text) {
		List<Section> sections = new ArrayList<>();
		if (text == null || text.isBlank()) {
			return new SourceText(sections);
		}
		Matcher matcher = MARKER_LINE.matcher(text);
		List<int[]> marks = new ArrayList<>();
		List<String> labels = new ArrayList<>();
		while (matcher.find()) {
			marks.add(new int[] {matcher.start(), matcher.end()});
			labels.add(matcher.group(1));
		}
		if (marks.isEmpty()) {
			// 위치 표시가 없는 글: 빈 줄로 나눈 문단마다 번호를 붙인다
			int number = 0;
			for (String paragraph : text.split("\\n\\s*\\n")) {
				if (!paragraph.isBlank()) {
					number++;
					sections.add(new Section(sections.size(), "문단 " + number, paragraph.strip()));
				}
			}
			return new SourceText(sections);
		}
		String head = text.substring(0, marks.get(0)[0]).strip();
		if (!head.isEmpty()) {
			sections.add(new Section(sections.size(), null, head));
		}
		for (int i = 0; i < marks.size(); i++) {
			int end = i + 1 < marks.size() ? marks.get(i + 1)[0] : text.length();
			String body = text.substring(marks.get(i)[1], end).strip();
			if (!body.isEmpty()) {
				sections.add(new Section(sections.size(), labels.get(i), body));
			}
		}
		return new SourceText(sections);
	}

	public List<Section> sections() {
		return sections;
	}

	public boolean isEmpty() {
		return sections.isEmpty();
	}

	/** 개념(용어) 중 본문 어디에도 나오지 않는 것 (공백·대소문자 무시) */
	public List<String> missingTerms(List<String> terms) {
		String all = String.join("\n", sections.stream().map(Section::text).toList());
		return terms.stream().filter(term -> term != null && !term.isBlank() && occurrences(all, term) == 0).toList();
	}

	/** 위치 표시를 붙인 전체 글의 길이 */
	public int renderedLength() {
		return sections.stream().mapToInt(section -> section.render().length() + 2).sum();
	}

	/** 개념이 나오는 부분을 예산(maxChars, 위치 표시 포함) 안에서 고른다 */
	public Selection select(List<String> focusTerms, int maxChars) {
		List<String> terms = focusTerms == null ? List.of()
				: focusTerms.stream().filter(term -> term != null && !term.isBlank()).toList();
		int[] scores = new int[sections.size()];
		boolean any = false;
		for (Section section : sections) {
			int score = 0;
			for (String term : terms) {
				score += occurrences(section.text(), term);
			}
			scores[section.index()] = score;
			any |= score > 0;
		}
		Set<Integer> chosen = new TreeSet<>();
		int[] used = {0};
		if (any) {
			List<Section> ranked = sections.stream().filter(section -> scores[section.index()] > 0)
					.sorted(Comparator.comparingInt((Section section) -> -scores[section.index()])
							.thenComparingInt(Section::index))
					.toList();
			// 개념이 나오는 부분만 보낸다 (예산이 남아도 개념이 안 나오는 부분은 더하지 않는다)
			for (Section section : ranked) {
				tryAdd(section, chosen, used, maxChars);
			}
		} else {
			for (Section section : sections) {
				if (!tryAdd(section, chosen, used, maxChars)) {
					break;
				}
			}
		}
		StringBuilder text = new StringBuilder();
		List<String> locations = new ArrayList<>();
		if (chosen.isEmpty() && !sections.isEmpty()) {
			// 첫 부분 하나도 예산보다 길면 그 부분을 잘라서 보낸다
			Section first = any ? sections.stream().filter(s -> scores[s.index()] > 0).findFirst().orElseThrow()
					: sections.get(0);
			String rendered = first.render();
			text.append(rendered, 0, Math.min(rendered.length(), Math.max(0, maxChars)));
			if (first.location() != null) {
				locations.add(first.location());
			}
			return new Selection(text.toString().strip(), locations, any, true);
		}
		for (Integer index : chosen) {
			Section section = sections.get(index);
			text.append(section.render()).append("\n\n");
			if (section.location() != null) {
				locations.add(section.location());
			}
		}
		return new Selection(text.toString().strip(), locations, any, chosen.size() < sections.size());
	}

	private static boolean tryAdd(Section section, Set<Integer> chosen, int[] used, int maxChars) {
		if (chosen.contains(section.index())) {
			return true;
		}
		int length = section.render().length() + 2;
		if (used[0] + length > maxChars) {
			return false;
		}
		chosen.add(section.index());
		used[0] += length;
		return true;
	}

	/**
	 * AI가 든 인용을 실제로 보낸 글(위치 표시 포함)에서 확인한다.
	 */
	public static QuoteCheck check(String sentText, String quote) {
		if (quote == null || quote.strip().length() < MIN_QUOTE_LENGTH || sentText == null) {
			return new QuoteCheck(VerificationStatus.INSUFFICIENT, null, null, null);
		}
		String trimmed = quote.strip();
		if (trimmed.length() > MAX_QUOTE_LENGTH) {
			trimmed = trimmed.substring(0, MAX_QUOTE_LENGTH);
		}
		String haystack = compact(sentText);
		String needle = compact(trimmed);
		int at = needle.isEmpty() ? -1 : haystack.indexOf(needle);
		SourceText parsed = parse(sentText);
		if (at >= 0) {
			String location = compactLocation(haystack, at);
			return new QuoteCheck(VerificationStatus.VERIFIED, trimmed, location, sectionText(parsed, location));
		}
		// 일부 일치: 한 부분 안에 인용 낱말이 충분히 있으면
		Set<String> tokens = tokens(trimmed);
		if (tokens.size() >= PARTIAL_MIN_TOKENS) {
			Section best = null;
			double bestRate = 0;
			for (Section section : parsed.sections()) {
				String body = compact(section.text());
				long hit = tokens.stream().filter(token -> body.contains(compact(token))).count();
				double rate = (double) hit / tokens.size();
				if (rate > bestRate) {
					bestRate = rate;
					best = section;
				}
			}
			if (best != null && bestRate >= PARTIAL_TOKEN_RATE) {
				return new QuoteCheck(VerificationStatus.PARTIAL, null, best.location(), best.text());
			}
		}
		return new QuoteCheck(VerificationStatus.FAILED, null, null, null);
	}

	/** 짧은 표현(정답 보기·허용 답)이 글 안에 나오는지 (공백·대소문자 무시). 참고 신호이고 사실성 검증이 아니다 */
	public static boolean mentions(String text, String phrase) {
		if (text == null || phrase == null) {
			return false;
		}
		String needle = compact(phrase);
		return needle.length() >= 2 && compact(text).contains(needle);
	}

	/** 공백·대소문자 차이를 무시하고 비교하기 위해 공백을 모두 지운다 (검색 SearchService도 쓴다) */
	public static String compact(String text) {
		return text.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
	}

	// 영문·숫자로만 된 용어(RSA, VLAN, TCP/IP)는 낱말 경계에서만 센다("hours and"의 "rsa"를 세지 않게, 판단·경계는 TermMatch).
	// 한글이 든 용어는 조사가 붙으므로 공백을 지운 글에서 부분 문자열로 센다
	static int occurrences(String text, String term) {
		String trimmed = term.strip();
		if (trimmed.isEmpty() || text == null) {
			return 0;
		}
		if (TermMatch.isLatin(trimmed)) {
			Matcher matcher = TermMatch.latinCountPattern(trimmed).matcher(text.toLowerCase(Locale.ROOT));
			int count = 0;
			while (matcher.find()) {
				count++;
			}
			return count;
		}
		String haystack = compact(text);
		String needle = compact(trimmed);
		int count = 0;
		int from = 0;
		while ((from = haystack.indexOf(needle, from)) >= 0) {
			count++;
			from += needle.length();
		}
		return count;
	}

	private static Set<String> tokens(String text) {
		Set<String> tokens = new LinkedHashSet<>();
		for (String token : TOKEN_SPLIT.split(text)) {
			if (token.length() >= 2) {
				tokens.add(token.toLowerCase(Locale.ROOT));
			}
		}
		return tokens;
	}

	// 공백을 지운 글에서 at 앞의 마지막 위치 표시를 원래 모양("p. 3", "슬라이드 2", "문단 4")으로
	private static String compactLocation(String haystack, int at) {
		Matcher matcher = COMPACT_MARKER.matcher(haystack.substring(0, at));
		String last = null;
		while (matcher.find()) {
			if (matcher.group(2) != null) {
				last = "p. " + matcher.group(2);
			} else if (matcher.group(3) != null) {
				last = "슬라이드 " + matcher.group(3);
			} else {
				last = "문단 " + matcher.group(4);
			}
		}
		return last;
	}

	private static String sectionText(SourceText parsed, String location) {
		return parsed.sections().stream()
				.filter(section -> location == null ? section.location() == null : location.equals(section.location()))
				.map(Section::text).findFirst().orElse(null);
	}
}
