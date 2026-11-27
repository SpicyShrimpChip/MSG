import { useRef } from 'react';
import { Outlet, ScrollRestoration } from 'react-router-dom';
import DemoBanner from './DemoBanner';
import Header from './Header';
import Footer from './Footer';
import NavigationProgress from './NavigationProgress';
import PageTransition from './PageTransition';
import RouteAnnouncer from './RouteAnnouncer';
import { MAIN_CONTENT_ID } from './mainContent';
import { useStickyOffset } from './useStickyOffset';

// 모든 페이지 공통 틀: 상단바(주 메뉴 포함) → 페이지 → 푸터
function Layout() {
  const stickyRef = useRef<HTMLDivElement>(null);
  useStickyOffset(stickyRef);

  return (
    <div className="flex min-h-screen flex-col">
      {/* 키보드 사용자가 상단바 메뉴를 건너뛰고 본문으로 가는 링크. 평소엔 숨기고 포커스될 때만 왼쪽 위에 보인다.
          클릭을 통과시키는 고정 묶음 밖에 둔다 */}
      <a
        href={`#${MAIN_CONTENT_ID}`}
        onClick={(event) => {
          // 주소에 #main-content가 붙어 방문 기록이 하나 더 쌓이지 않게 직접 옮긴다
          event.preventDefault();
          const main = document.getElementById(MAIN_CONTENT_ID);
          main?.focus({ preventScroll: true });
          main?.scrollIntoView({ block: 'start' });
        }}
        className="sr-only focus:not-sr-only focus:fixed focus:top-3 focus:left-3 focus:z-50 focus:rounded-lg focus:bg-surface-raised focus:px-4 focus:py-2 focus:font-semibold focus:text-primary focus:shadow-md focus:shadow-shade/10"
      >
        본문으로 건너뛰기
      </a>
      <NavigationProgress />
      {/* 체험 모드 띠도 상단바와 함께 화면 위에 붙어 있게 한 덩어리로 고정한다.
          상단바는 내리면 띠 뒤로 숨고(띠는 늘 보인다), 숨은 동안 빈 자리가 아래 내용의 클릭을 막지 않게 묶음은 클릭을 통과시킨다 */}
      <div
        ref={stickyRef}
        className="pointer-events-none sticky top-0 z-40 print:hidden"
      >
        <DemoBanner />
        <Header />
      </div>
      {/* 코드로만 포커스하는 상자(건너뛰기 링크·화면 이동)라 포커스 테두리를 그리지 않는다 */}
      <main
        id={MAIN_CONTENT_ID}
        tabIndex={-1}
        className="flex-1 outline-hidden"
      >
        <PageTransition>
          <Outlet />
        </PageTransition>
      </main>
      <Footer />
      {/* 새 화면은 맨 위에서 열고, 뒤로·앞으로 가기는 그 화면에서 보던 위치로 돌려놓는다.
          없으면 긴 화면 아래에서 링크를 누를 때 다음 화면이 중간부터 열린다(#230) */}
      <ScrollRestoration />
      <RouteAnnouncer />
    </div>
  );
}

export default Layout;
