import { describe, expect, it } from 'vitest';
import { demoStartVisible } from './demoStartVisible';

describe('demoStartVisible', () => {
  it('로그인하지 않은 것이 확인되면 보인다', () => {
    expect(
      demoStartVisible({ available: true, active: false, status: 'anonymous' }),
    ).toBe(true);
  });

  it('세션을 되살리는 중에는 숨긴다', () => {
    expect(
      demoStartVisible({ available: true, active: false, status: 'unknown' }),
    ).toBe(false);
  });

  it('로그인했거나 이미 체험 중이면 숨긴다', () => {
    expect(
      demoStartVisible({
        available: true,
        active: false,
        status: 'authenticated',
      }),
    ).toBe(false);
    expect(
      demoStartVisible({ available: true, active: true, status: 'anonymous' }),
    ).toBe(false);
  });

  it('체험을 끈 빌드에서는 숨긴다', () => {
    expect(
      demoStartVisible({
        available: false,
        active: false,
        status: 'anonymous',
      }),
    ).toBe(false);
  });
});
