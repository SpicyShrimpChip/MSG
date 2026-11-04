// TS에 .env의 존재를 알리고 주입

// TS 특수 명령어: vite 기본 규칙 적용
/// <reference types="vite/client" />

// 커스텀 환경변수 명세서 (환경변수 추가 시 작성)
interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string;
  // 'true'면 API 함수가 src/mocks/ 데이터를 돌려준다
  readonly VITE_USE_MOCK?: string;
  // 'true'면 목업 모드에서도 체험 버튼 없이 모든 화면이 열린다 (개발용, 공개 배포에는 넣지 않는다)
  readonly VITE_MOCK_OPEN_ACCESS?: string;
}

// TS 시스템에 명세서 강제 병합
interface ImportMeta {
  readonly env: ImportMetaEnv;
}
