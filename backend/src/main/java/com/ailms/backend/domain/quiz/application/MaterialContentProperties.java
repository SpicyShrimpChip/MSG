package com.ailms.backend.domain.quiz.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 학습자료 본문을 외부 AI로 보내는 기능 (app.material-content).
 *
 * <p><b>enabled 기본값은 false다.</b> 자료 본문을 외부 생성형 AI로 보내려면 제공사·이전 국가·보관 기간을 개인정보 처리방침에
 * 적고 적법한 처리 근거(동의 문구 등)를 팀·법 검토로 확정해야 한다(루트 CLAUDE.md "학습 자료 저작권", 2026-11-03 보고).
 * 확정 전에는 운영에서 켜지 않는다. 꺼져 있으면 자료 본문 읽기·핵심 개념 뽑기·본문 기반 문제 만들기 요청을 서버가 거절하고,
 * 과목 개념으로만 문제를 만든다. 본문 추출 자체(서버 안)는 이 값과 관계없이 외부로 아무것도 보내지 않는다.
 *
 * @param enabled     자료 본문(발췌)을 외부 AI로 보낼 수 있는지
 * @param maxChars    한 번에 보내는 본문 발췌 글자 수 상한
 * @param maxPdfPages PDF에서 읽는 쪽 수 상한 (넘으면 앞쪽만)
 */
@ConfigurationProperties(prefix = "app.material-content")
public record MaterialContentProperties(boolean enabled, int maxChars, int maxPdfPages) {
}
