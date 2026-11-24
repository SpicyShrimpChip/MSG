import { describe, expect, it } from 'vitest';
import {
  containsKeyword,
  filterByKeywords,
  isLatinKeyword,
  keywordTargets,
} from './filter';
import type { ParsedConversation } from './types';

// 백엔드 global/common/TermMatchTest의 표와 같다. 한쪽을 고치면 다른 쪽도 같이 고친다
const TABLE: [keyword: string, text: string, found: boolean][] = [
  ['IP', 'shipping address', false],
  ['IP', 'IP 주소를 바꿨어요', true],
  ['IP', 'TCP/IP 모델', true],
  ['IP', 'IPv4 주소', false],
  ['RSA', 'RSA를 공부했어요', true],
  ['RSA', 'office hours and', false],
  ['rsa', 'RSA 암호', true],
  ['RSA', '공개키 rsa', true],
  ['VLAN', 'VLAN 설정', true],
  ['VLAN', 'vlans are', false],
  ['TCP/IP', 'tcp/ip 4계층', true],
  ['TCP/IP', 'tcp / ip', false],
  ['C++', 'C++로 짰어요', true],
  ['machine learning', 'Machine Learning 기초', true],
  ['machine learning', 'machinelearning', false],
  ['운영체제', '운영체제론 수업', true],
  ['IP 주소', 'VIP 주소 이벤트', true],
  ['교착 상태', '교착상태', false],
];

describe('학습 키워드 찾기 규칙', () => {
  it.each(TABLE)('[%s] in "%s" → %s', (keyword, text, found) => {
    const [target] = keywordTargets([keyword]);
    expect(containsKeyword(text.toLowerCase(), target)).toBe(found);
  });

  it('영문·숫자와 공백·. / + # & - 로만 된 키워드를 영문 키워드로 본다', () => {
    expect(isLatinKeyword('TCP/IP')).toBe(true);
    expect(isLatinKeyword('Node.js')).toBe(true);
    expect(isLatinKeyword('IP 주소')).toBe(false);
    expect(isLatinKeyword('(RSA)')).toBe(false);
  });

  it('제목과 메시지 경계도 낱말 경계로 본다', () => {
    const conversation: ParsedConversation = {
      id: '1',
      source: 'claude',
      title: '네트워크 질문 IP',
      messages: [{ role: 'user', text: 'shipping은 상관없어요' }],
    };
    expect(filterByKeywords([conversation], ['IP'])).toHaveLength(1);
    expect(
      filterByKeywords([{ ...conversation, title: '배송 질문' }], ['IP']),
    ).toEqual([]);
  });

  it('긴 글에서도 느려지지 않는다', () => {
    const [target] = keywordTargets(['IP']);
    const text = `${'a'.repeat(1_000_000)} ip`;
    const started = performance.now();
    expect(containsKeyword(text, target)).toBe(true);
    expect(performance.now() - started).toBeLessThan(2000);
  });
});
