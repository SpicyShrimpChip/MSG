import { describe, expect, it } from 'vitest';
import { matchesQuery, squashForSearch } from './search';

describe('matchesQuery', () => {
  it('띄어쓰기와 대소문자를 무시한다', () => {
    expect(matchesQuery('정보보호개론', '정보 보호')).toBe(true);
    expect(matchesQuery('정보 보호 개론', '정보보호개론')).toBe(true);
    expect(matchesQuery('인증서(PKI)', 'pki')).toBe(true);
  });

  it('들어 있지 않거나 검색어가 비면 맞지 않는다', () => {
    expect(matchesQuery('운영체제', '정보보호')).toBe(false);
    expect(matchesQuery('운영체제', '   ')).toBe(false);
    expect(matchesQuery(null, '정보')).toBe(false);
  });

  it('비교용 값은 공백을 모두 지운 소문자다', () => {
    expect(squashForSearch(' Hash  함수 ')).toBe('hash함수');
  });
});
