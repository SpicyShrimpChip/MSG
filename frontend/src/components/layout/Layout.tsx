import { Outlet } from 'react-router-dom';
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
      {/* 체험 모드 띠도 상단바와 함께 화면 위에 붙어 있게 한 덩어리로 고정한다 */}
      <div className="sticky top-0 z-40 print:hidden">
        <DemoBanner />
        <Header />
      </div>
      <main className="flex-1">
        <PageTransition>
          <Outlet />
        </PageTransition>
      </main>
      <Footer />
    </div>
  );
}

export default Layout;
