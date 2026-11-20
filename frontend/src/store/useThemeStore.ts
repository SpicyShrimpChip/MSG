import { create } from 'zustand';
import { persist } from 'zustand/middleware';

export type ThemePreference = 'system' | 'light' | 'dark';

interface ThemeState {
  preference: ThemePreference;
  setPreference: (preference: ThemePreference) => void;
}

const darkQuery = () => window.matchMedia('(prefers-color-scheme: dark)');

// 색이 서서히 바뀌는 시간(350ms, styles/theme-transition.css의 --theme-transition-duration)보다 조금 길게 요소별 전환을 꺼 둔다
const THEME_SWITCH_MS = 400;
let switchingTimer: number | undefined;

// 연달아 눌렀을 때 마지막 전환이 끝나기 전에 theme-vt를 떼지 않도록 번호를 매긴다
let viewTransitionId = 0;
// 전환이 시작됐지만 아직 화면에 적용되지 않은 최신 목표. 연달아 눌렀을 때 "이미 그 상태"로 잘못 보지 않도록
// <html>의 dark 클래스 대신 이 값을 먼저 본다
let pendingDark: boolean | null = null;

// 선택값과 기기 설정을 보고 <html>에 dark 클래스를 붙이거나 뗀다
const applyTheme = (preference: ThemePreference) => {
  const dark =
    preference === 'dark' || (preference === 'system' && darkQuery().matches);
  const root = document.documentElement;
  if ((pendingDark ?? root.classList.contains('dark')) === dark) return;
  pendingDark = dark;

  // 1순위: View Transitions. 이전 화면과 새 화면을 겹쳐 서서히 바꾼다.
  // 색을 하나씩 섞으면 글자와 배경의 밝기가 중간에 같아져 글자가 잠깐 사라졌다 나타난다(컷편집처럼 보임)
  const reduceMotion = window.matchMedia(
    '(prefers-reduced-motion: reduce)',
  ).matches;
  if (!reduceMotion && typeof document.startViewTransition === 'function') {
    const id = ++viewTransitionId;
    root.classList.add('theme-vt');
    const transition = document.startViewTransition(() => {
      // 건너뛴 전환의 콜백도 호출되므로, 항상 가장 최근 목표를 적용한다
      root.classList.toggle('dark', pendingDark ?? dark);
      pendingDark = null;
    });
    transition.finished
      .catch(() => undefined)
      .finally(() => {
        if (id === viewTransitionId) root.classList.remove('theme-vt');
      });
    return;
  }

  // 2순위(지원하지 않는 브라우저): 색 토큰이 :root에서 서서히 바뀐다(styles/theme-transition.css).
  // 그동안 요소별 전환은 꺼 둔다(index.css의 .theme-switching)
  root.classList.add('theme-switching');
  root.classList.toggle('dark', dark);
  pendingDark = null;
  window.clearTimeout(switchingTimer);
  switchingTimer = window.setTimeout(
    () => root.classList.remove('theme-switching'),
    THEME_SWITCH_MS,
  );
};

// 화면 모드 선택 (시스템 설정 / 밝게 / 어둡게). 저장 키는 index.html 스크립트와 같아야 한다
export const useThemeStore = create<ThemeState>()(
  persist(
    (set) => ({
      preference: 'system',
      setPreference: (preference) => {
        applyTheme(preference);
        set({ preference });
      },
    }),
    { name: 'ai-lms-theme' },
  ),
);

// "시스템 설정"을 고른 상태에서 기기 모드가 바뀌면 바로 따라간다
darkQuery().addEventListener('change', () => {
  if (useThemeStore.getState().preference === 'system') applyTheme('system');
});
