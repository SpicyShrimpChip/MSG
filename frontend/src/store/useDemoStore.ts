import { create } from 'zustand';

// 체험 모드: 백엔드 없이 목업 데이터로 둘러보는 상태.
// 목업 모드(VITE_USE_MOCK=true, Vercel 배포)에서만 의미가 있고, 로그인 가드가 이 값으로 화면을 열어 준다.
// 탭 안(sessionStorage)에만 저장해서 탭을 닫으면 사라진다. 서버 데이터가 아니므로 React Query가 아니라 여기 둔다
const DEMO_KEY = 'ai-lms-demo';

const IS_MOCK = import.meta.env.VITE_USE_MOCK === 'true';

// 개발 중에만 쓰는 예외: .env.development.local에 VITE_MOCK_OPEN_ACCESS=true를 넣으면
// 목업 모드에서도 체험 버튼 없이 모든 화면(관리자 포함)이 열린다. 공개 배포에는 넣지 않는다
export const MOCK_OPEN_ACCESS =
  IS_MOCK && import.meta.env.VITE_MOCK_OPEN_ACCESS === 'true';

const readDemo = () => {
  if (!IS_MOCK) return false;
  try {
    return sessionStorage.getItem(DEMO_KEY) === '1';
  } catch {
    return false;
  }
};

const writeDemo = (on: boolean) => {
  try {
    if (on) sessionStorage.setItem(DEMO_KEY, '1');
    else sessionStorage.removeItem(DEMO_KEY);
  } catch {
    // 저장소를 쓸 수 없는 환경(비공개 창 등)에서는 새로고침하면 체험이 끝난다
  }
};

interface DemoState {
  active: boolean;
  start: () => void;
  end: () => void;
}

export const useDemoStore = create<DemoState>()((set) => ({
  active: readDemo(),
  start: () => {
    if (!IS_MOCK) return;
    writeDemo(true);
    set({ active: true });
  },
  end: () => {
    writeDemo(false);
    set({ active: false });
  },
}));
