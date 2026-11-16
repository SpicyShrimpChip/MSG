import { describe, expect, it } from 'vitest';
import { isReadable, validateMaterialFile } from './material';

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
