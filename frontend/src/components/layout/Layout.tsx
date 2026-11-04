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
      <DemoBanner />
      <Header />
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
