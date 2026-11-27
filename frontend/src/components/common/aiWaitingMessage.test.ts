import { describe, expect, it } from 'vitest';
import { nextDoneMessage } from './aiWaitingMessage';

describe('nextDoneMessage', () => {
  const idle = { waiting: false, message: '' };
  it('대기가 성공으로 끝나면 완료 문구', () => {
    expect(
      nextDoneMessage(
        { waiting: true, message: '' },
        { waiting: false, failed: false },
        '요약을 다 만들었어요.',
      ),
    ).toBe('요약을 다 만들었어요.');
  });
  it('실패로 끝나면 비운다 (오류는 ErrorText가 읽는다)', () => {
    expect(
      nextDoneMessage(
        { waiting: true, message: '' },
        { waiting: false, failed: true },
        '요약을 다 만들었어요.',
      ),
    ).toBe('');
  });
  it('완료 문구가 없으면(끝나면 다른 화면으로 감) 비운다', () => {
    expect(
      nextDoneMessage(
        { waiting: true, message: '' },
        { waiting: false, failed: false },
      ),
    ).toBe('');
  });
  it('처음부터 대기 중이 아니면 비운다', () => {
    expect(
      nextDoneMessage(
        idle,
        { waiting: false, failed: false },
        '다 만들었어요.',
      ),
    ).toBe('');
  });
  it('다시 기다리기 시작하면 이전 완료 문구를 지운다', () => {
    expect(
      nextDoneMessage(
        { waiting: false, message: '다 만들었어요.' },
        { waiting: true, failed: false },
        '다 만들었어요.',
      ),
    ).toBe('');
  });
  it('대기 상태가 그대로면 문구를 유지한다', () => {
    expect(
      nextDoneMessage(
        { waiting: false, message: '다 만들었어요.' },
        { waiting: false, failed: false },
        '다 만들었어요.',
      ),
    ).toBe('다 만들었어요.');
  });
});
