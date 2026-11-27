import { useEffect, useRef } from 'react';
import { useLocation, useMatches } from 'react-router-dom';
import { documentTitle, pageTitle } from '@/router/routeTitle';
import { focusMainContent } from './mainContent';

// 화면마다 브라우저 탭 제목을 바꾸고, 다른 화면으로 옮겼을 때 화면 낭독기에 새 화면 이름을 알린다.
// 제목은 router/index.tsx의 라우트 handle.title에서 읽는다(페이지 코드·데이터를 불러오기 전에도 맞다)
function RouteAnnouncer() {
  const matches = useMatches();
  const { pathname } = useLocation();
  const page = pageTitle(matches);
  const previousPathname = useRef(pathname);
  const liveRef = useRef<HTMLParagraphElement>(null);

  useEffect(() => {
    document.title = documentTitle(page);
  }, [page]);

  // 주소의 경로가 바뀔 때만 알린다(첫 화면, 검색어·#만 바뀐 이동은 그대로 둔다)
  useEffect(() => {
    if (previousPathname.current === pathname) return;
    previousPathname.current = pathname;
    if (liveRef.current) liveRef.current.textContent = page ?? '메인';
    // 눌렀던 링크가 이전 화면과 함께 사라져 포커스를 잃었을 때만 본문으로 옮긴다.
    // 상단바 메뉴로 옮겨 포커스가 상단바에 남아 있으면 메뉴를 이어서 쓸 수 있게 그대로 둔다
    const active = document.activeElement;
    if (!active || active === document.body) focusMainContent();
  }, [pathname, page]);

  return <p ref={liveRef} aria-live="polite" className="sr-only" />;
}

export default RouteAnnouncer;
