import { describe, expect, it } from 'vitest';
import { documentTitle, pageTitle } from './routeTitle';

describe('pageTitle', () => {
  it('가장 안쪽 라우트의 제목을 고른다', () => {
    expect(
      pageTitle([
        { handle: undefined },
        { handle: { title: '퀴즈' } },
        { handle: { title: '퀴즈 결과' } },
      ]),
    ).toBe('퀴즈 결과');
  });
  it('안쪽에 제목이 없으면 바깥 제목을 쓴다', () => {
    expect(
      pageTitle([{ handle: { title: '공지 관리' } }, { handle: undefined }]),
    ).toBe('공지 관리');
  });
  it('제목이 없거나 문자열이 아니면 null', () => {
    expect(
      pageTitle([
        { handle: undefined },
        { handle: { title: 3 } },
        { handle: null },
      ]),
    ).toBeNull();
  });
});

describe('documentTitle', () => {
  it('페이지 제목 뒤에 서비스 이름을 붙인다', () => {
    expect(documentTitle('내 학습자료')).toBe('내 학습자료 · MSG');
  });
  it('제목이 없으면(메인) 사이트 제목', () => {
    expect(documentTitle(null)).toBe('MSG · My Study Guide');
  });
});
