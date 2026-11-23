import { describe, expect, it } from 'vitest';
import { nextHeaderHidden } from './useHideOnScroll';

describe('nextHeaderHidden', () => {
  it('아래로 내리면 숨긴다', () => {
    expect(nextHeaderHidden(false, 200, 260)).toBe(true);
  });

  it('조금이라도 위로 올리면 다시 보인다', () => {
    expect(nextHeaderHidden(true, 600, 590)).toBe(false);
  });

  it('맨 위 근처에서는 내려도 보인다', () => {
    expect(nextHeaderHidden(false, 0, 60)).toBe(false);
    expect(nextHeaderHidden(true, 300, 40)).toBe(false);
  });

  it('작은 움직임은 지금 상태를 유지한다', () => {
    expect(nextHeaderHidden(true, 500, 497)).toBe(true);
    expect(nextHeaderHidden(false, 500, 503)).toBe(false);
  });
});
