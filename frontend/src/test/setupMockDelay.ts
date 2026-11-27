import { vi } from 'vitest';

// 목업 API는 로딩 화면을 보려고 300~1500ms 기다렸다가 답한다(mocks/delay.ts). 테스트는 화면이 아니라
// 목업의 규칙만 보므로 기다리지 않고 바로 답하게 한다 (모든 테스트 파일에 적용된다)
vi.mock('../mocks/delay', () => ({
  mockDelay: <T>(data: T) => Promise.resolve(data),
}));
