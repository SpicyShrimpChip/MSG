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

// 체험 중 바꾼 예시 데이터(자료·과목·퀴즈 결과·학습 기록)는 mocks/가 localStorage(`ai-lms-mock-*`)에 둔다.
// 체험을 시작할 때와 끝낼 때 지워서 다음 체험이 늘 처음 예시 상태에서 시작하게 한다 (탭을 그냥 닫아도 다음 시작 때 지워진다).
// 공지(`ai-lms-mock-notices`)는 체험에서 쓰지 않는 관리자 기능(개발 중 VITE_MOCK_OPEN_ACCESS)이라 남긴다.
// 체험이 켜진 뒤에만 mocks/가 저장소를 읽으므로, 시작할 때 지우면 메모리에 남은 옛 값도 없다
const MOCK_DATA_PREFIX = 'ai-lms-mock-';
const KEEP_MOCK_KEYS = new Set(['ai-lms-mock-notices']);

const clearMockData = () => {
  try {
    const keys = Array.from({ length: localStorage.length }, (_, index) =>
      localStorage.key(index),
    );
    for (const key of keys) {
      if (key?.startsWith(MOCK_DATA_PREFIX) && !KEEP_MOCK_KEYS.has(key)) {
        localStorage.removeItem(key);
      }
    }
  } catch {
    // 저장소를 쓸 수 없는 환경이면 남은 기록도 없다
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
    clearMockData();
    writeDemo(true);
    set({ active: true });
  },
  end: () => {
    clearMockData();
    writeDemo(false);
    set({ active: false });
  },
}));

// 주소 끝에 `?demo=1`이 있으면 "로그인 없이 체험해 보기"와 같게 체험을 시작하고 학습 대시보드로 보낸다.
// 목업 모드가 아니면 아무 일도 하지 않는다(start()의 확인과 같은 기준). 라우터를 만들기 전에 불러야 한다.
export const enterDemoFromUrl = () => {
  if (!IS_MOCK) return;
  try {
    const url = new URL(window.location.href);
    if (url.searchParams.get('demo') !== '1') return;
    useDemoStore.getState().start();
    window.history.replaceState(null, '', '/analysis');
  } catch {
    // 주소를 읽을 수 없는 환경이면 평소처럼 시작한다
  }
};
