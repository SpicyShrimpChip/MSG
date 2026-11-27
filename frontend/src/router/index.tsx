import { Navigate, createBrowserRouter } from 'react-router-dom';
import { Layout, LoadingFallback } from '@/components';
import { enterDemoFromUrl } from '@/store';
import MainPage from '@/pages/main/MainPage';
import NotFoundPage from '@/pages/NotFoundPage';
import RouteErrorPage from '@/pages/RouteErrorPage';
import { lazyPage } from './lazyPage';
import RequireAdmin from './RequireAdmin';
import RequireAuth from './RequireAuth';
import type { RouteHandle } from './routeTitle';

// 페이지 추가 시 배열 안에 객체로 정리
/*
<페이지 추가 시>
1. ./pages 하위에 페이지 폴더와 파일 생성 (default export, 페이지 전용 컴포넌트는 pages/<페이지>/components/)
2. 아래 Layout의 children 배열에 lazyPage(() => import(...))로 추가 (주 메뉴는 constants/menu.ts)
   - handle: { title }(브라우저 탭 제목·이동 알림, RouteAnnouncer)도 함께 단다
   - 메인·404만 처음부터 불러오고, 나머지는 처음 방문할 때 코드를 불러온다 (pages/index.ts 같은 한 곳 모음 파일을 거치면 쪼개지지 않는다)
   - 내 데이터를 보여 주는 화면은 RequireAuth(로그인 가드) 아래에 넣는다
*/
// 라우터가 주소를 읽기 전에 `?demo=1`을 처리한다 (체험을 켤 수 있는 빌드에서만, DEMO_AVAILABLE)
enterDemoFromUrl();

export const router = createBrowserRouter([
  {
    path: '/',
    element: <Layout />,
    // 화면을 그리다 오류가 나거나 배포 뒤 옛 화면 코드를 못 불러왔을 때 (기본 화면은 영어·오류 내용 노출)
    errorElement: <RouteErrorPage />,
    // 첫 방문에서 페이지 코드를 불러오는 동안 보여 줄 화면
    hydrateFallbackElement: <LoadingFallback />,
    children: [
      { index: true, element: <MainPage /> },

      // 로그인이 필요한 화면 (체험 중이면 로그인 없이 연다)
      {
        element: <RequireAuth />,
        children: [
          // 학습자료
          {
            path: 'courses',
            handle: { title: '내 과목' } satisfies RouteHandle,
            lazy: lazyPage(() => import('@/pages/courses/CoursePage')),
          },
          {
            path: 'materials',
            handle: { title: '내 학습자료' } satisfies RouteHandle,
            lazy: lazyPage(() => import('@/pages/materials/MaterialListPage')),
          },
          {
            path: 'materials/upload',
            handle: { title: '자료 올리기' } satisfies RouteHandle,
            lazy: lazyPage(
              () => import('@/pages/materials/MaterialUploadPage'),
            ),
          },
          {
            path: 'materials/:materialId',
            handle: { title: '학습자료' } satisfies RouteHandle,
            lazy: lazyPage(
              () => import('@/pages/materials/MaterialDetailPage'),
            ),
          },

          // 맞춤학습
          {
            path: 'curriculum',
            handle: { title: '맞춤 커리큘럼' } satisfies RouteHandle,
            lazy: lazyPage(() => import('@/pages/curriculum/CurriculumPage')),
          },
          {
            path: 'quizzes',
            handle: { title: '퀴즈' } satisfies RouteHandle,
            lazy: lazyPage(() => import('@/pages/quizzes/QuizListPage')),
          },
          {
            path: 'assessments',
            handle: { title: '사전·사후평가' } satisfies RouteHandle,
            lazy: lazyPage(() => import('@/pages/assessments/AssessmentPage')),
          },
          {
            path: 'quizzes/new',
            handle: { title: '퀴즈 만들기' } satisfies RouteHandle,
            lazy: lazyPage(() => import('@/pages/quizzes/QuizCreatePage')),
          },
          {
            path: 'quizzes/:quizId',
            handle: { title: '퀴즈 풀기' } satisfies RouteHandle,
            lazy: lazyPage(() => import('@/pages/quizzes/QuizSolvePage')),
          },
          {
            path: 'quizzes/:quizId/result',
            handle: { title: '퀴즈 결과' } satisfies RouteHandle,
            lazy: lazyPage(() => import('@/pages/quizzes/QuizResultPage')),
          },

          // 학습분석
          {
            path: 'analysis',
            handle: { title: '학습 대시보드' } satisfies RouteHandle,
            lazy: lazyPage(() => import('@/pages/analysis/DashboardPage')),
          },
          {
            path: 'analysis/import',
            handle: { title: 'AI 대화 가져오기' } satisfies RouteHandle,
            lazy: lazyPage(
              () => import('@/pages/analysis/ConversationImportPage'),
            ),
          },

          // 계정
          {
            path: 'mypage',
            handle: { title: '마이페이지' } satisfies RouteHandle,
            lazy: lazyPage(() => import('@/pages/mypage/MyPage')),
          },

          // 관리자 (로그인 + 관리자 역할 필요. 서버도 /api/admin/** 에서 다시 확인한다)
          {
            element: <RequireAdmin />,
            children: [
              {
                path: 'admin',
                element: <Navigate to="/admin/notices" replace />,
              },
              {
                path: 'admin/notices',
                handle: { title: '공지 관리' } satisfies RouteHandle,
                lazy: lazyPage(
                  () => import('@/pages/admin/AdminNoticeListPage'),
                ),
              },
              {
                path: 'admin/notices/new',
                handle: { title: '새 공지 쓰기' } satisfies RouteHandle,
                lazy: lazyPage(
                  () => import('@/pages/admin/AdminNoticeFormPage'),
                ),
              },
              {
                path: 'admin/notices/:noticeId/edit',
                handle: { title: '공지 고치기' } satisfies RouteHandle,
                lazy: lazyPage(
                  () => import('@/pages/admin/AdminNoticeFormPage'),
                ),
              },
            ],
          },
        ],
      },

      // 공지사항: 11/5 멘토링으로 주 메뉴에서 뺐다(AI 서비스 정체성). 관리자 화면·검색 결과에서만 연다
      {
        path: 'notices',
        handle: { title: '공지사항' } satisfies RouteHandle,
        lazy: lazyPage(() => import('@/pages/notices/NoticeListPage')),
      },
      {
        path: 'notices/:noticeId',
        handle: { title: '공지사항' } satisfies RouteHandle,
        lazy: lazyPage(() => import('@/pages/notices/NoticeDetailPage')),
      },

      // 이용안내
      {
        path: 'about',
        handle: { title: '서비스 소개' } satisfies RouteHandle,
        lazy: lazyPage(() => import('@/pages/about/AboutPage')),
      },
      {
        path: 'terms',
        handle: { title: '이용약관' } satisfies RouteHandle,
        lazy: lazyPage(() => import('@/pages/terms/TermsPage')),
      },
      {
        path: 'privacy',
        handle: { title: '개인정보 처리방침' } satisfies RouteHandle,
        lazy: lazyPage(() => import('@/pages/privacy/PrivacyPage')),
      },

      // 대화 내보내기 도움말: 가입 전에도 미리 신청해 둘 수 있게 로그인 없이 연다
      {
        path: 'analysis/import/help/:service',
        handle: { title: '대화 내보내기 도움말' } satisfies RouteHandle,
        lazy: lazyPage(() => import('@/pages/analysis/ExportHelpPage')),
      },

      // 계정·기타
      {
        path: 'login',
        handle: { title: '로그인' } satisfies RouteHandle,
        lazy: lazyPage(() => import('@/pages/auth/LoginPage')),
      },
      {
        path: 'signup',
        handle: { title: '회원가입' } satisfies RouteHandle,
        lazy: lazyPage(() => import('@/pages/auth/SignupPage')),
      },
      // 카카오 로그인이 끝나 돌아오는 화면 (서버 콜백이 보낸다, 로그인 없이 연다)
      {
        path: 'signup/social',
        handle: { title: '카카오로 가입하기' } satisfies RouteHandle,
        lazy: lazyPage(() => import('@/pages/auth/SocialSignupPage')),
      },
      {
        path: 'auth/social/done',
        handle: { title: '카카오 로그인' } satisfies RouteHandle,
        lazy: lazyPage(() => import('@/pages/auth/SocialDonePage')),
      },
      {
        path: 'auth/social/link',
        handle: { title: '카카오 계정 연결' } satisfies RouteHandle,
        lazy: lazyPage(() => import('@/pages/auth/SocialLinkPage')),
      },
      {
        path: 'search',
        handle: { title: '검색 결과' } satisfies RouteHandle,
        lazy: lazyPage(() => import('@/pages/search/SearchPage')),
      },

      {
        path: '*',
        handle: { title: '페이지를 찾을 수 없어요' } satisfies RouteHandle,
        element: <NotFoundPage />,
      },
    ],
  },
]);
