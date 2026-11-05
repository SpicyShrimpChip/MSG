import Hero from './components/Hero';
import QuickLinks from './components/QuickLinks';
import ScrollStory from './components/story/ScrollStory';

// 공지사항은 메인에 두지 않는다. 로그인 사용자용 홈(이어서 공부하기 등)은 나중에 따로 만든다
function MainPage() {
  return (
    <>
      <Hero />
      <QuickLinks />
      <ScrollStory />
    </>
  );
}

export default MainPage;
