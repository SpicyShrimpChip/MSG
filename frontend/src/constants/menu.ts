// 상단바 주 메뉴 구성. 경로는 router와 같은 값을 쓴다
// 순서는 학습 흐름: 모으기(학습자료) → 진단(학습분석) → 공부·확인(맞춤학습). 11/5 멘토링 뒤 정리
export interface MenuItem {
  label: string;
  path: string;
}

export interface MenuGroup {
  label: string;
  path: string;
  children: MenuItem[];
}

export const MENU: MenuGroup[] = [
  {
    label: '학습자료',
    path: '/materials',
    children: [
      { label: '내 과목', path: '/courses' },
      { label: '내 학습자료', path: '/materials' },
      { label: '자료 올리기', path: '/materials/upload' },
      // 주소는 /analysis 아래지만 기록을 모으는 단계라 여기 둔다 (현재 위치 표시는 menuActive.currentMenuPath)
      { label: 'AI 대화 가져오기', path: '/analysis/import' },
    ],
  },
  {
    label: '학습분석',
    path: '/analysis',
    children: [{ label: '학습 대시보드', path: '/analysis' }],
  },
  {
    label: '맞춤학습',
    path: '/curriculum',
    children: [
      { label: '맞춤 커리큘럼', path: '/curriculum' },
      { label: '사전·사후평가', path: '/assessments' },
      { label: '퀴즈', path: '/quizzes' },
    ],
  },
  {
    label: '이용안내',
    path: '/about',
    children: [
      { label: '서비스 소개', path: '/about' },
      { label: '개인정보 처리방침', path: '/privacy' },
    ],
  },
];
