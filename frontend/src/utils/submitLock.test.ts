import { describe, expect, it, vi } from 'vitest';
import { createSubmitLock, sendOnce } from './submitLock';

describe('createSubmitLock', () => {
  it('한 번 잠그면 풀기 전까지 다시 잠글 수 없다', () => {
    const lock = createSubmitLock();
    expect(lock.isHeld()).toBe(false);
    expect(lock.tryAcquire()).toBe(true);
    expect(lock.isHeld()).toBe(true);
    expect(lock.tryAcquire()).toBe(false);
    lock.release();
    expect(lock.isHeld()).toBe(false);
    expect(lock.tryAcquire()).toBe(true);
  });

  it('잠금마다 따로 잠긴다', () => {
    const first = createSubmitLock();
    const second = createSubmitLock();
    expect(first.tryAcquire()).toBe(true);
    expect(second.tryAcquire()).toBe(true);
  });
});

describe('sendOnce', () => {
  it('끝나기 전에 연달아 눌러도 한 번만 보낸다', () => {
    const lock = createSubmitLock();
    const send = vi.fn();
    const results = Array.from({ length: 10 }, (_, i) =>
      sendOnce(lock, send, i),
    );
    expect(send).toHaveBeenCalledTimes(1);
    expect(send).toHaveBeenCalledWith(0);
    expect(results.filter(Boolean)).toHaveLength(1);
  });

  it('요청이 끝나 잠금을 풀면 다시 보낼 수 있다', () => {
    const lock = createSubmitLock();
    const send = vi.fn();
    sendOnce(lock, send, 'a');
    lock.release();
    expect(sendOnce(lock, send, 'b')).toBe(true);
    expect(send).toHaveBeenNthCalledWith(2, 'b');
  });

  it('보내다 바로 예외가 나면 잠금을 풀고 예외를 넘긴다', () => {
    const lock = createSubmitLock();
    expect(() =>
      sendOnce(lock, () => {
        throw new Error('보내기 실패');
      }),
    ).toThrow('보내기 실패');
    expect(lock.isHeld()).toBe(false);
  });
});
