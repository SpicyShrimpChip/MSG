import { Outlet, ScrollRestoration } from 'react-router-dom';
import DemoBanner from './DemoBanner';
import Header from './Header';
import Footer from './Footer';
import NavigationProgress from './NavigationProgress';
import PageTransition from './PageTransition';

// 모든 페이지 공통 틀: 상단바(주 메뉴 포함) → 페이지 → 푸터
function Layout() {
  return (
    <div className="flex min-h-screen flex-col">
      <NavigationProgress />
      {/* 체험 모드 띠도 상단바와 함께 화면 위에 붙어 있게 한 덩어리로 고정한다.
          상단바는 내리면 띠 뒤로 숨고(띠는 늘 보인다), 숨은 동안 빈 자리가 아래 내용의 클릭을 막지 않게 묶음은 클릭을 통과시킨다 */}
      <div className="pointer-events-none sticky top-0 z-40 print:hidden">
        <DemoBanner />
        <Header />
      </div>
      <main className="flex-1">
        <PageTransition>
          <Outlet />
        </PageTransition>
      </main>
      <Footer />
      {/* 새 화면은 맨 위에서 열고, 뒤로·앞으로 가기는 그 화면에서 보던 위치로 돌려놓는다.
          없으면 긴 화면 아래에서 링크를 누를 때 다음 화면이 중간부터 열린다(#230) */}
      <ScrollRestoration />
    </div>
  );
}

export default Layout;
