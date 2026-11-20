import { create } from 'zustand';

// 체험 모드: 로그인 없이 목업 데이터(mocks/)로 둘러보는 상태.
// 탭 안(sessionStorage)에만 저장해서 탭을 닫으면 사라진다. 서버 데이터가 아니므로 React Query가 아니라 여기 둔다
//
// "목업이냐"를 판단하는 곳은 이 파일 하나다. 세 가지를 구분한다.
// - NO_BACKEND: 연결할 백엔드가 없는 빌드 (VITE_USE_MOCK=true, 지금 Vercel 배포). 로그인·가입이 닫히고 데이터는 늘 목업
// - DEMO_AVAILABLE: "로그인 없이 체험해 보기"를 켤 수 있는지. 실제 서버 빌드에서도 켤 수 있다(11/21, 주소 하나로 체험·실제 서비스)
// - isMockData()/useMockData(): 지금 데이터를 목업에서 읽는지 (백엔드가 없거나 체험 중). API 함수와 "예시 데이터" 문구가 이걸 본다
const DEMO_KEY = 'ai-lms-demo';

export const NO_BACKEND = import.meta.env.VITE_USE_MOCK === 'true';

// 실제 서버 빌드에서도 체험을 켤 수 있다. 체험 중에는 isMockData()가 true라 API 함수가 서버를 부르지 않는다
// (store/useDemoStore.test.ts). 로그인한 상태에서는 버튼을 숨기고, 체험 중 로그인하면 체험을 끝낸다(api/auth.api.ts)
export const DEMO_AVAILABLE = true;

// 개발 중에만 쓰는 예외: .env.development.local에 VITE_MOCK_OPEN_ACCESS=true를 넣으면
// 목업 모드에서도 체험 버튼 없이 모든 화면(관리자 포함)이 열린다. 공개 배포에는 넣지 않는다
export const MOCK_OPEN_ACCESS =
  NO_BACKEND && import.meta.env.VITE_MOCK_OPEN_ACCESS === 'true';

const readDemo = () => {
  if (!DEMO_AVAILABLE) return false;
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
    if (!DEMO_AVAILABLE) return;
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
// 체험을 켤 수 없는 빌드면 아무 일도 하지 않는다(start()의 확인과 같은 기준). 라우터를 만들기 전에 불러야 한다.
export const enterDemoFromUrl = () => {
  if (!DEMO_AVAILABLE) return;
  try {
    const url = new URL(window.location.href);
    if (url.searchParams.get('demo') !== '1') return;
    useDemoStore.getState().start();
    window.history.replaceState(null, '', '/analysis');
  } catch {
    // 주소를 읽을 수 없는 환경이면 평소처럼 시작한다
  }
};

// 지금 데이터를 목업에서 읽는지. React 밖(API 함수)에서는 부르는 순간의 값을 읽는다
export const isMockData = () => NO_BACKEND || useDemoStore.getState().active;

// 화면에서 쓰는 같은 값. 체험을 켜고 끄면 다시 그린다
export const useMockData = () =>
  useDemoStore((state) => NO_BACKEND || state.active);
