//! 개인정보 가리기. frontend `utils/conversation/mask.ts`에 있던 JS 정규식 규칙을 그대로 옮겼다
//! (서버 `PersonalInfoDetector`도 같은 규칙을 쓴다. 규칙을 바꾸면 두 곳을 같이 고친다).
//!
//! JS와 같은 결과를 내려고:
//! - `\d`는 ASCII 숫자 `[0-9]`, `\b`는 ASCII 단어 글자(`[A-Za-z0-9_]`) 기준으로 직접 확인한다
//!   (러스트 정규식의 기본 `\d`·`\b`는 유니코드라 한글 옆에서 결과가 달라진다)
//! - `\s`는 JS가 공백으로 보는 문자 집합(`js::JS_SPACE_CLASS`)을 쓴다
//! - 러스트 `regex`에는 앞뒤 확인(lookaround)이 없어서, 앞 글자 확인(`(?<!…)`)은 찾은 뒤 코드로 하고
//!   (안 맞으면 JS처럼 다음 글자부터 다시 찾는다), 뒤 글자 확인(`(?!…)`)은 그 글자까지 함께 찾은 뒤 뺀다.
//!   `regex`는 JS와 같은 "앞선 선택지 우선(leftmost-first)" 순서로 찾아서 되돌아가며 찾은 결과와 같다.
//!   (되돌아가며 찾는 `fancy_regex`는 같은 결과를 내지만 40배 넘게 느렸다)
//! - 위치는 UTF-16 단위로 센다

use crate::js::{JS_SPACE_CLASS, js_trim, utf16_len};
use regex::{Regex, RegexBuilder};
use serde::Serialize;
use std::collections::HashSet;
use std::sync::OnceLock;

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize)]
#[serde(rename_all = "lowercase")]
pub enum MaskKind {
    Secret,
    Email,
    Rrn,
    Card,
    Phone,
    Ip,
    Name,
    Custom,
}

impl MaskKind {
    pub fn label(self) -> &'static str {
        match self {
            MaskKind::Secret => "[키·토큰]",
            MaskKind::Email => "[이메일]",
            MaskKind::Rrn => "[주민등록번호]",
            MaskKind::Card => "[카드번호]",
            MaskKind::Phone => "[전화번호]",
            MaskKind::Ip => "[IP]",
            MaskKind::Name => "[이름]",
            MaskKind::Custom => "[가린 단어]",
        }
    }
}

#[derive(Debug, Serialize, PartialEq)]
pub struct MaskMatch {
    pub kind: MaskKind,
    /// 가리기 전의 글자. 미리보기 화면에서만 쓰고 서버로 보내지 않는다
    pub original: String,
    /// 가린 결과 글 안에서 대체 표시가 있는 자리 (UTF-16 단위)
    pub start: usize,
    pub end: usize,
}

#[derive(Debug, Serialize, PartialEq)]
pub struct MaskResult {
    pub text: String,
    pub matches: Vec<MaskMatch>,
}

// 바이트 위치 기준 범위 (마지막에 UTF-16 위치로 바꾼다)
#[derive(Clone, Copy)]
struct Range {
    kind: MaskKind,
    start: usize,
    end: usize,
}

fn ascii_digits(matched: &str) -> Vec<u32> {
    matched.bytes().filter(u8::is_ascii_digit).map(|b| u32::from(b - b'0')).collect()
}

fn is_valid_rrn(matched: &str) -> bool {
    let digits = ascii_digits(matched);
    if digits.len() != 13 {
        return false;
    }
    let month = digits[2] * 10 + digits[3];
    let day = digits[4] * 10 + digits[5];
    (1..=12).contains(&month) && (1..=31).contains(&day)
}

// 카드번호는 숫자 자릿수만으로는 오탐이 많아서 Luhn 검사를 통과한 것만 가린다
fn passes_luhn(matched: &str) -> bool {
    let digits = ascii_digits(matched);
    if digits.len() < 13 || digits.len() > 19 {
        return false;
    }
    let mut sum = 0;
    let mut double = false;
    for &digit in digits.iter().rev() {
        let mut n = digit;
        if double {
            n *= 2;
            if n > 9 {
                n -= 9;
            }
        }
        sum += n;
        double = !double;
    }
    sum % 10 == 0
}

fn is_word(c: char) -> bool {
    c.is_ascii_alphanumeric() || c == '_'
}

fn char_before(text: &str, at: usize) -> Option<char> {
    text[..at].chars().next_back()
}

fn char_after(text: &str, at: usize) -> Option<char> {
    text[at..].chars().next()
}

// 다음 글자 위치 (찾은 자리가 안 맞을 때 JS처럼 한 글자 뒤부터 다시 찾는다)
fn next_char(text: &str, at: usize) -> usize {
    at + char_after(text, at).map_or(1, char::len_utf8)
}

/// `(?<!앞)본체(?!뒤)` 모양의 규칙. 정규식은 `본체(?:뒤가 아닌 글자|\z)`로 만들어 뒤 글자까지 함께 찾고 뺀다
struct GuardedRule {
    kind: MaskKind,
    pattern: Regex,
    // 바로 앞에 오면 안 되는 글자
    forbidden_before: fn(char) -> bool,
    validate: Option<fn(&str) -> bool>,
}

impl GuardedRule {
    fn new(
        kind: MaskKind,
        body: &str,
        not_after_class: &str,
        forbidden_before: fn(char) -> bool,
        validate: Option<fn(&str) -> bool>,
    ) -> Self {
        let pattern = Regex::new(&format!("{body}(?:[^{not_after_class}]|\\z)")).expect("가리기 정규식");
        GuardedRule { kind, pattern, forbidden_before, validate }
    }

    fn find(&self, text: &str, out: &mut Vec<Range>) {
        let mut position = 0;
        while position <= text.len() {
            let Some(found) = self.pattern.find_at(text, position) else { break };
            let start = found.start();
            if char_before(text, start).is_some_and(self.forbidden_before) {
                position = next_char(text, start);
                continue;
            }
            // 본체는 늘 숫자로 끝나고 뒤 글자는 숫자가 아니다. 마지막 글자가 숫자가 아니면 함께 찾은 뒤 글자라 뺀다
            let mut end = found.end();
            if let Some(last) = char_before(text, end).filter(|c| !c.is_ascii_digit()) {
                end -= last.len_utf8();
            }
            // JS matchAll처럼, 검사에 떨어져도 찾은 자리는 지나간다
            if self.validate.is_none_or(|validate| validate(&text[start..end])) {
                out.push(Range { kind: self.kind, start, end });
            }
            position = end;
        }
    }
}

/// 키·토큰: `\b(?:여러 접두어+글자)\b|Bearer\s+…`. 끝의 `\b`는 JS처럼 긴 것부터 줄여 가며 맞는 자리를 찾는다
struct SecretRule {
    pattern: Regex,
}

// 접두어마다 (접두어, 고정 길이 앞부분을 뺀 반복 최소 길이). 접두어는 서로 겹치지 않아 한 자리에 하나만 맞는다
fn secret_min_end(text: &str, start: usize, end: usize) -> usize {
    let rest = &text[start..end];
    if rest.starts_with("sk-") {
        start + 3 + 16
    } else if rest.starts_with("github_pat_") {
        start + 11 + 30
    } else if rest.starts_with("gh") {
        start + 4 + 30
    } else if rest.starts_with("AKIA") {
        start + 4 + 16
    } else if rest.starts_with("AIza") {
        start + 4 + 35
    } else if rest.starts_with("xox") {
        start + 5 + 10
    } else {
        // eyJ….eyJ….마지막: 마지막 점 뒤 8글자 이상
        start + rest.rfind('.').map_or(rest.len(), |dot| dot + 1 + 8)
    }
}

impl SecretRule {
    fn new() -> Self {
        let s = JS_SPACE_CLASS;
        let tokens = [
            "sk-(?:ant-)?[A-Za-z0-9_-]{16,}",
            "gh[pousr]_[A-Za-z0-9]{30,}",
            "github_pat_[A-Za-z0-9_]{30,}",
            "AKIA[0-9A-Z]{16}",
            "AIza[0-9A-Za-z_-]{35}",
            "xox[baprs]-[A-Za-z0-9-]{10,}",
            r"eyJ[A-Za-z0-9_-]{8,}\.eyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}",
        ]
        .join("|");
        let pattern = Regex::new(&format!("(?:{tokens})|Bearer[{s}]+[A-Za-z0-9._~+/-]{{20,}}=*")).expect("가리기 정규식");
        SecretRule { pattern }
    }

    fn is_boundary(text: &str, at: usize) -> bool {
        let before = char_before(text, at).is_some_and(is_word);
        let after = char_after(text, at).is_some_and(is_word);
        before != after
    }

    fn find(&self, text: &str, out: &mut Vec<Range>) {
        let mut position = 0;
        while position <= text.len() {
            let Some(found) = self.pattern.find_at(text, position) else { break };
            let start = found.start();
            // Bearer 쪽은 앞뒤 확인이 없다 (다른 접두어는 B로 시작하지 않는다)
            if text[start..].starts_with("Bearer") {
                out.push(Range { kind: MaskKind::Secret, start, end: found.end() });
                position = found.end();
                continue;
            }
            // 앞의 \b: 뒤 글자가 늘 단어 글자라서 "앞 글자가 단어 글자가 아님"과 같다
            if char_before(text, start).is_some_and(is_word) {
                position = next_char(text, start);
                continue;
            }
            // 끝의 \b: 가장 긴 것부터 한 글자씩 줄여 처음 맞는 자리 (JS가 되돌아가며 찾는 순서). 이 글자들은 모두 ASCII다
            let min_end = secret_min_end(text, start, found.end());
            let end = (min_end..=found.end()).rev().find(|&end| Self::is_boundary(text, end));
            match end {
                Some(end) => {
                    out.push(Range { kind: MaskKind::Secret, start, end });
                    position = end;
                }
                None => position = next_char(text, start),
            }
        }
    }
}

// 키·토큰 규칙이 맞으려면 글에 하나는 있어야 하는 접두어
const SECRET_PREFIXES: [&str; 7] = ["sk-", "gh", "AKIA", "AIza", "xox", "eyJ", "Bearer"];

struct Rules {
    secret: SecretRule,
    email: Regex,
    guarded: Vec<GuardedRule>,
}

const OCTET: &str = "(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])";

fn rules() -> &'static Rules {
    static RULES: OnceLock<Rules> = OnceLock::new();
    RULES.get_or_init(|| {
        let s = JS_SPACE_CLASS;
        Rules {
            // OpenAI·Anthropic 키, GitHub 토큰, AWS 액세스 키, Google API 키, Slack 토큰, JWT, Bearer 토큰
            secret: SecretRule::new(),
            email: Regex::new(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}")
                .expect("가리기 정규식"),
            guarded: vec![
                GuardedRule::new(
                    MaskKind::Rrn,
                    &format!("[0-9]{{6}}[-{s}]?[1-4][0-9]{{6}}"),
                    "0-9",
                    |c| c.is_ascii_digit(),
                    Some(is_valid_rrn),
                ),
                GuardedRule::new(
                    MaskKind::Card,
                    "[0-9](?:[ -]?[0-9]){12,18}",
                    "0-9",
                    |c| c.is_ascii_digit(),
                    Some(passes_luhn),
                ),
                // 휴대폰(010 등)·지역번호·070, 국가번호 +82 형태 포함
                GuardedRule::new(
                    MaskKind::Phone,
                    &format!(r"(?:\+82[-.{s}]?0?|0)(?:10|1[16789]|2|[3-6][1-5]|70)[-.{s}]?[0-9]{{3,4}}[-.{s}]?[0-9]{{4}}"),
                    "0-9-",
                    |c| c.is_ascii_digit() || c == '-',
                    None,
                ),
                // 버전 번호(1.2.3.4)처럼 생긴 것도 가려질 수 있다. 개인정보 쪽으로 치우친 선택
                GuardedRule::new(
                    MaskKind::Ip,
                    &format!(r"(?:{OCTET}\.){{3}}{OCTET}"),
                    "0-9.",
                    |c| c.is_ascii_digit() || c == '.',
                    None,
                ),
            ],
        }
    })
}

/// 이름·직접 추가한 단어를 미리 정규식으로 만들어 두고, 글 여러 개에 같이 쓴다
pub struct Masker {
    literals: Vec<(MaskKind, Regex)>,
}

fn literal_patterns(words: &[String], kind: MaskKind) -> Vec<(MaskKind, Regex)> {
    // 앞뒤 공백을 빼고 빈 단어·같은 단어는 한 번만 (처음 나온 순서)
    let mut seen = HashSet::new();
    let mut unique: Vec<&str> = Vec::new();
    for word in words {
        let word = js_trim(word);
        if !word.is_empty() && seen.insert(word) {
            unique.push(word);
        }
    }
    // 긴 단어를 먼저 찾아야 "김주미"가 "주미"에 쪼개지지 않는다 (JS처럼 UTF-16 길이, 같은 길이는 원래 순서)
    unique.sort_by_key(|word| std::cmp::Reverse(utf16_len(word)));
    unique
        .into_iter()
        .filter_map(|word| {
            RegexBuilder::new(&regex::escape(word))
                .case_insensitive(true)
                .build()
                .ok()
                .map(|pattern| (kind, pattern))
        })
        .collect()
}

impl Masker {
    pub fn new(names: &[String], terms: &[String]) -> Self {
        let mut literals = literal_patterns(names, MaskKind::Name);
        literals.extend(literal_patterns(terms, MaskKind::Custom));
        Masker { literals }
    }

    pub fn mask(&self, text: &str) -> MaskResult {
        let rules = rules();
        // 우선순위 순서로 쌓는다 (겹치면 앞선 것이 이긴다): 키 → 이메일 → 주민번호 → 카드 → 전화 → IP → 이름 → 단어
        let mut candidates: Vec<Range> = Vec::new();
        // 키·토큰은 정해진 접두어가 있어야 한다
        if SECRET_PREFIXES.iter().any(|prefix| memchr::memmem::find(text.as_bytes(), prefix.as_bytes()).is_some()) {
            rules.secret.find(text, &mut candidates);
        }
        // 글 대부분은 한글 문장이라, 꼭 있어야 하는 글자가 없으면 그 규칙은 훑지 않는다 (결과는 같고 빠르다)
        if text.as_bytes().contains(&b'@') {
            for found in rules.email.find_iter(text) {
                candidates.push(Range { kind: MaskKind::Email, start: found.start(), end: found.end() });
            }
        }
        // 주민번호·카드·전화·IP는 모두 ASCII 숫자가 있어야 한다
        if text.bytes().any(|b| b.is_ascii_digit()) {
            for rule in &rules.guarded {
                rule.find(text, &mut candidates);
            }
        }
        for (kind, pattern) in &self.literals {
            for found in pattern.find_iter(text) {
                candidates.push(Range { kind: *kind, start: found.start(), end: found.end() });
            }
        }

        let mut accepted: Vec<Range> = Vec::new();
        for candidate in candidates {
            let overlaps =
                accepted.iter().any(|range| range.start < candidate.end && candidate.start < range.end);
            if !overlaps {
                accepted.push(candidate);
            }
        }
        accepted.sort_by_key(|range| range.start);

        let mut result = String::with_capacity(text.len());
        let mut result_len16 = 0;
        let mut cursor = 0;
        let mut matches = Vec::with_capacity(accepted.len());
        for range in accepted {
            let before = &text[cursor..range.start];
            result.push_str(before);
            result_len16 += utf16_len(before);
            let label = range.kind.label();
            let label_len16 = utf16_len(label);
            matches.push(MaskMatch {
                kind: range.kind,
                original: text[range.start..range.end].to_string(),
                start: result_len16,
                end: result_len16 + label_len16,
            });
            result.push_str(label);
            result_len16 += label_len16;
            cursor = range.end;
        }
        result.push_str(&text[cursor..]);
        MaskResult { text: result, matches }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn mask(text: &str) -> String {
        Masker::new(&[], &[]).mask(text).text
    }

    #[test]
    fn 이메일_전화번호를_가린다() {
        assert_eq!(mask("메일 kim@example.com 전화 010-1234-5678"), "메일 [이메일] 전화 [전화번호]");
    }

    #[test]
    fn 한글_바로_옆의_키도_가린다() {
        // 러스트 기본 \b(유니코드)였다면 '키'와 's' 사이를 경계로 보지 않아 놓친다
        assert_eq!(mask("키sk-abcdefghijklmnopqrstuvwxyz입니다"), "키[키·토큰]입니다");
    }

    #[test]
    fn 키_끝의_하이픈은_js처럼_빼고_가린다() {
        // JS는 끝의 \b를 맞추려고 되돌아가 마지막 '-'를 빼고 찾는다
        assert_eq!(mask("sk-abcdefghijklmnopqrstu- 끝"), "[키·토큰]- 끝");
    }

    #[test]
    fn 앞에_숫자가_붙으면_한_글자_뒤부터_다시_찾는다() {
        // (?<!\d): 앞에 숫자가 있는 자리는 건너뛰고 다음 자리부터 찾는다
        assert_eq!(mask("1010-1234-5678"), "1010-1234-5678");
        assert_eq!(mask("x010-1234-5678"), "x[전화번호]");
    }

    #[test]
    fn 위치는_utf16_단위다() {
        let result = Masker::new(&[], &[]).mask("😀 a@b.co");
        assert_eq!(result.matches[0].start, 3);
        assert_eq!(result.matches[0].end, 3 + 5);
    }

    #[test]
    fn 긴_이름을_먼저_찾는다() {
        let masker = Masker::new(&["주미".into(), " 김주미 ".into(), String::new()], &[]);
        assert_eq!(masker.mask("김주미와 주미").text, "[이름]와 [이름]");
    }

    #[test]
    fn 카드번호는_luhn을_통과한_것만() {
        assert_eq!(mask("4111 1111 1111 1111"), "[카드번호]");
        assert_eq!(mask("4111 1111 1111 1112"), "4111 1111 1111 1112");
    }

    #[test]
    fn 전각_공백도_js처럼_공백으로_본다() {
        assert_eq!(mask("Bearer\u{3000}abcdefghijklmnopqrstuvwxyz"), "[키·토큰]");
    }
}
