// 본문(<main>) 상자의 id. 본문으로 건너뛰기 링크와 화면 이동 뒤 포커스 옮기기가 같이 쓴다
export const MAIN_CONTENT_ID = 'main-content';

// 본문 상자로 포커스를 옮긴다. 스크롤은 ScrollRestoration·건너뛰기 링크가 따로 맞춘다
export function focusMainContent(): void {
  document.getElementById(MAIN_CONTENT_ID)?.focus({ preventScroll: true });
}
