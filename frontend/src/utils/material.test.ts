import { describe, expect, it } from 'vitest';
import { CONTENT_DISABLED_MESSAGE } from '@/constants';
import {
  aiQuizBlockedReason,
  isReadable,
  materialContentBlockedReason,
  validateMaterialFile,
} from './material';

const MB = 1024 * 1024;

describe('validateMaterialFile', () => {
  it('지원하는 형식은 대소문자 구분 없이 통과한다', () => {
    expect(
      validateMaterialFile({ name: '노트.PDF', size: MB }),
    ).toBeUndefined();
    expect(validateMaterialFile({ name: 'a.b.md', size: 10 })).toBeUndefined();
  });

  it('지원하지 않는 형식과 확장자 없는 파일은 거절한다', () => {
    expect(validateMaterialFile({ name: 'a.exe', size: 10 })).toMatch(/파일만/);
    expect(validateMaterialFile({ name: 'pdf', size: 10 })).toMatch(/파일만/);
  });

  it('빈 파일과 너무 큰 파일은 거절한다', () => {
    expect(validateMaterialFile({ name: 'a.txt', size: 0 })).toMatch(/빈 파일/);
    expect(validateMaterialFile({ name: 'a.txt', size: 21 * MB })).toMatch(
      /MB/,
    );
  });
});

describe('isReadable', () => {
  it('올릴 수 있는 형식은 대소문자와 상관없이 본문을 읽는다', () => {
    expect(isReadable('강의노트.PDF')).toBe(true);
    expect(isReadable('정리.md')).toBe(true);
    expect(isReadable('발표.pptx')).toBe(true);
  });

  it('지원하지 않는 형식이나 확장자가 없으면 읽지 않는다', () => {
    expect(isReadable('과제.hwp')).toBe(false);
    expect(isReadable('README')).toBe(false);
  });
});

const on = { available: true, message: '', materialContentEnabled: true };

describe('materialContentBlockedReason', () => {
  it('전송이 꺼져 있으면 꺼짐 안내가 형식보다 먼저다', () => {
    expect(
      materialContentBlockedReason({ materialContentEnabled: false }, 'a.exe'),
    ).toBe(CONTENT_DISABLED_MESSAGE);
  });

  it('읽지 못하는 형식이면 형식 안내, 아니면 null', () => {
    expect(materialContentBlockedReason(on, 'a.exe')).toBe(
      '이 형식은 본문을 읽지 못해요. TXT·MD·PPTX·글이 들어 있는 PDF만 읽어요.',
    );
    expect(materialContentBlockedReason(on, '노트.PDF')).toBeNull();
  });
});

describe('aiQuizBlockedReason', () => {
  it('AI가 없으면 서버 문구가 동의보다 먼저다', () => {
    expect(
      aiQuizBlockedReason(
        { available: false, message: '연결된 모델이 없어요' },
        false,
        '요약을 만들',
      ),
    ).toBe('연결된 모델이 없어요');
  });

  it('동의가 false일 때만 막고 기능 이름을 넣는다', () => {
    expect(aiQuizBlockedReason(on, false, '개념을 뽑거나 설명을 만들')).toBe(
      'AI로 개념을 뽑거나 설명을 만들려면 AI 퀴즈 처리 동의가 필요해요. 마이페이지에서 동의할 수 있어요.',
    );
    expect(aiQuizBlockedReason(on, false, '요약을 만들')).toBe(
      'AI로 요약을 만들려면 AI 퀴즈 처리 동의가 필요해요. 마이페이지에서 동의할 수 있어요.',
    );
    expect(aiQuizBlockedReason(on, false, '평가를 만들')).toBe(
      'AI로 평가를 만들려면 AI 퀴즈 처리 동의가 필요해요. 마이페이지에서 동의할 수 있어요.',
    );
    expect(aiQuizBlockedReason(on, undefined, '요약을 만들')).toBeNull();
    expect(aiQuizBlockedReason(on, true, '요약을 만들')).toBeNull();
  });
});
