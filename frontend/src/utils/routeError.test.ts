import { describe, expect, it } from 'vitest';
import {
  CHUNK_RELOAD_WINDOW_MS,
  isChunkLoadError,
  shouldAutoReload,
} from './routeError';

describe('isChunkLoadError', () => {
  it('브라우저별 화면 코드 불러오기 실패 문구를 알아본다', () => {
    expect(
      isChunkLoadError(
        new TypeError(
          'Failed to fetch dynamically imported module: https://x/assets/QuizSolvePage-abc.js',
        ),
      ),
    ).toBe(true);
    expect(
      isChunkLoadError(
        new TypeError('error loading dynamically imported module'),
      ),
    ).toBe(true);
    expect(
      isChunkLoadError(new TypeError('Importing a module script failed.')),
    ).toBe(true);
    const named = new Error('x');
    named.name = 'ChunkLoadError';
    expect(isChunkLoadError(named)).toBe(true);
  });

  it('다른 오류와 Error가 아닌 값은 아니라고 본다', () => {
    expect(isChunkLoadError(new TypeError('a is undefined'))).toBe(false);
    expect(
      isChunkLoadError('Failed to fetch dynamically imported module'),
    ).toBe(false);
    expect(isChunkLoadError(null)).toBe(false);
  });
});

describe('shouldAutoReload', () => {
  it('처음이거나 오래전에 새로고침했으면 다시 새로고침한다', () => {
    expect(shouldAutoReload(null, 1_000)).toBe(true);
    expect(shouldAutoReload(0, CHUNK_RELOAD_WINDOW_MS + 1)).toBe(true);
  });

  it('방금 새로고침했으면 다시 하지 않는다 (무한 새로고침 방지)', () => {
    expect(shouldAutoReload(10_000, 10_000 + CHUNK_RELOAD_WINDOW_MS)).toBe(
      false,
    );
  });
});
