import { Navigate, createBrowserRouter } from 'react-router-dom';
import { Layout, LoadingFallback } from '@/components';
import MainPage from '@/pages/main/MainPage';
import NotFoundPage from '@/pages/NotFoundPage';
import { lazyPage } from './lazyPage';
import RequireAdmin from './RequireAdmin';
import RequireAuth from './RequireAuth';

// 페이지 추가 시 배열 안에 객체로 정리
/*
<페이지 추가 시>
1. ./pages 하위에 페이지 폴더와 파일 생성 (default export, 페이지 전용 컴포넌트는 pages/<페이지>/components/)
2. 아래 Layout의 children 배열에 lazyPage(() => import(...))로 추가 (주 메뉴는 constants/menu.ts)
   - 메인·404만 처음부터 불러오고, 나머지는 처음 방문할 때 코드를 불러온다 (pages/index.ts 같은 한 곳 모음 파일을 거치면 쪼개지지 않는다)
   - 내 데이터를 보여 주는 화면은 RequireAuth(로그인 가드) 아래에 넣는다
*/
export const router = createBrowserRouter([
  {
    path: '/',
    element: <Layout />,
    // 첫 방문에서 페이지 코드를 불러오는 동안 보여 줄 화면
    hydrateFallbackElement: <LoadingFallback />,
    children: [
      { index: true, element: <MainPage /> },

      // 로그인이 필요한 화면 (목업 모드에서는 검사하지 않는다)
      {
        element: <RequireAuth />,
        children: [
          // 학습자료
          {
            path: 'courses',
            lazy: lazyPage(() => import('@/pages/courses/CoursePage')),
          },
          {
            path: 'materials',
            lazy: lazyPage(() => import('@/pages/materials/MaterialListPage')),
          },
          {
            path: 'materials/upload',
            lazy: lazyPage(
              () => import('@/pages/materials/MaterialUploadPage'),
            ),
          },
          {
            path: 'materials/:materialId',
            lazy: lazyPage(
              () => import('@/pages/materials/MaterialDetailPage'),
            ),
          },

          // 맞춤학습
          {
            path: 'curriculum',
            lazy: lazyPage(() => import('@/pages/curriculum/CurriculumPage')),
          },
          {
            path: 'quizzes',
            lazy: lazyPage(() => import('@/pages/quizzes/QuizListPage')),
          },
          {
            path: 'quizzes/:quizId',
            lazy: lazyPage(() => import('@/pages/quizzes/QuizSolvePage')),
          },
          {
            path: 'quizzes/:quizId/result',
            lazy: lazyPage(() => import('@/pages/quizzes/QuizResultPage')),
          },

          // 학습분석
          {
            path: 'analysis',
            lazy: lazyPage(() => import('@/pages/analysis/DashboardPage')),
          },
          {
            path: 'analysis/import',
            lazy: lazyPage(
              () => import('@/pages/analysis/ConversationImportPage'),
            ),
          },

          // 계정
          {
            path: 'mypage',
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
                lazy: lazyPage(
                  () => import('@/pages/admin/AdminNoticeListPage'),
                ),
              },
              {
                path: 'admin/notices/new',
                lazy: lazyPage(
                  () => import('@/pages/admin/AdminNoticeFormPage'),
                ),
              },
              {
                path: 'admin/notices/:noticeId/edit',
                lazy: lazyPage(
                  () => import('@/pages/admin/AdminNoticeFormPage'),
                ),
              },
            ],
          },
        ],
      },

      // 커뮤니티
      {
        path: 'notices',
        lazy: lazyPage(() => import('@/pages/notices/NoticeListPage')),
      },
      {
        path: 'notices/:noticeId',
        lazy: lazyPage(() => import('@/pages/notices/NoticeDetailPage')),
      },

      // 이용안내
      {
        path: 'about',
        lazy: lazyPage(() => import('@/pages/about/AboutPage')),
      },
      {
        path: 'privacy',
        lazy: lazyPage(() => import('@/pages/privacy/PrivacyPage')),
      },

      // 대화 내보내기 도움말: 가입 전에도 미리 신청해 둘 수 있게 로그인 없이 연다
      {
        path: 'analysis/import/help/:service',
        lazy: lazyPage(() => import('@/pages/analysis/ExportHelpPage')),
      },

      // 계정·기타
      {
        path: 'login',
        lazy: lazyPage(() => import('@/pages/auth/LoginPage')),
      },
      {
        path: 'signup',
        lazy: lazyPage(() => import('@/pages/auth/SignupPage')),
      },
      {
        path: 'search',
        lazy: lazyPage(() => import('@/pages/search/SearchPage')),
      },

      { path: '*', element: <NotFoundPage /> },
    ],
  },
]);
