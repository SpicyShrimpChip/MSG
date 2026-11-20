import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '@/api/request';
import { createRequestKey, isNoResponseError } from './requestKey';

const UUID_V4 =
  /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

describe('createRequestKey', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('randomUUID가 있으면 그대로 쓴다', () => {
    expect(createRequestKey()).toMatch(UUID_V4);
  });

  it('HTTPS가 아니라 randomUUID가 없어도 UUID v4 모양의 키를 만든다', () => {
    vi.stubGlobal('crypto', {
      getRandomValues: (array: Uint8Array) => {
        for (let i = 0; i < array.length; i += 1)
          array[i] = (i * 37 + 11) % 256;
        return array;
      },
    });
    const key = createRequestKey();
    expect(key).toMatch(UUID_V4);
  });
});

describe('isNoResponseError', () => {
  it('상태 코드가 없는 API 오류(시간 초과·연결 끊김)만 참이다', () => {
    expect(isNoResponseError(new ApiError('시간 초과'))).toBe(true);
    expect(isNoResponseError(new ApiError('이미 있음', 409))).toBe(false);
    expect(isNoResponseError(new Error('다른 오류'))).toBe(false);
  });
});
