// 브라우저 탭 제목과 화면 이동 알림에 쓰는 화면 제목 (라우트의 handle.title)

// 라우트 객체의 handle 모양. router/index.tsx의 페이지 라우트마다 단다
export interface RouteHandle {
  title: string;
}

// index.html의 <title>과 같은 값. 제목이 없는 화면(메인)에 쓴다
export const SITE_TITLE = 'MSG · My Study Guide';

// 겹친 라우트 중 가장 안쪽(뒤쪽)에서 문자열 제목이 있는 것을 고른다
export function pageTitle(
  matches: readonly { handle?: unknown }[],
): string | null {
  for (let i = matches.length - 1; i >= 0; i -= 1) {
    const handle = matches[i].handle;
    if (typeof handle === 'object' && handle !== null && 'title' in handle) {
      const { title } = handle;
      if (typeof title === 'string') return title;
    }
  }
  return null;
}

export function documentTitle(page: string | null): string {
  return page ? `${page} · MSG` : SITE_TITLE;
}
