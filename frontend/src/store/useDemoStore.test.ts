import { describe, expect, it } from 'vitest';
import {
  DEMO_AVAILABLE,
  NO_BACKEND,
  isMockData,
  useDemoStore,
} from './useDemoStore';

// 테스트는 VITE_USE_MOCK 없이(실제 서버 빌드처럼) 돈다.
// 지금은 백엔드가 있는 빌드에서 체험을 켤 수 없고 데이터는 늘 서버에서 읽는다 (바꿀 때 이 테스트도 같이 고친다)
describe('목업 판단 기준 (실제 서버 빌드)', () => {
  it('백엔드가 있는 빌드에서는 체험을 켤 수 없고 목업 데이터를 쓰지 않는다', () => {
    expect(NO_BACKEND).toBe(false);
    expect(DEMO_AVAILABLE).toBe(false);

    useDemoStore.getState().start();

    expect(useDemoStore.getState().active).toBe(false);
    expect(isMockData()).toBe(false);
  });
});
