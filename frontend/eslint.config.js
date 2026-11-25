import js from '@eslint/js';
import globals from 'globals';
import reactHooks from 'eslint-plugin-react-hooks';
import reactX from 'eslint-plugin-react-x';
import reactDom from 'eslint-plugin-react-dom';
import { reactRefresh } from 'eslint-plugin-react-refresh';
import tseslint from 'typescript-eslint';
import eslintConfigPrettier from 'eslint-config-prettier';

// ── 프로젝트 규칙 (frontend/CLAUDE.md·루트 CLAUDE.md에 글로 적힌 것을 자동으로 막는다) ──
// 예외 파일은 아래 별도 설정 객체에서 해당 항목만 빼고 다시 건다.
// ESLint는 같은 규칙을 파일별 설정으로 덮어쓸 때 옵션을 합치지 않고 통째로 바꾸므로,
// 항목을 상수로 나눠 두고 예외 파일에는 나머지 항목만 넘긴다.

// motion.div 등: LazyMotion strict(main.tsx) 아래에서는 m.* 만 쓴다 (frontend/CLAUDE.md 움직임)
const NO_MOTION_COMPONENT = {
  selector: "JSXOpeningElement[name.object.name='motion']",
  message:
    "motion.div 대신 'motion/react-m'의 m.div를 써 주세요 (frontend/CLAUDE.md 움직임)",
};

// HTML 문자열 렌더링 금지 (루트 CLAUDE.md 보안 원칙)
const DANGEROUS_HTML_MESSAGE =
  'HTML 문자열 렌더링은 쓰지 않아요 (루트 CLAUDE.md 보안 원칙)';
const NO_DANGEROUS_HTML = [
  {
    selector: "JSXAttribute[name.name='dangerouslySetInnerHTML']",
    message: DANGEROUS_HTML_MESSAGE,
  },
  // createElement(..., { dangerouslySetInnerHTML }) 나 props 객체로 넘기는 경우
  {
    selector: "Property[key.name='dangerouslySetInnerHTML']",
    message: DANGEROUS_HTML_MESSAGE,
  },
];

// 목업 여부는 store/useDemoStore.ts 한 곳에서만 판단한다 (frontend/CLAUDE.md 백엔드 연동)
const MOCK_ENV_MESSAGE =
  '목업 여부는 store/useDemoStore.ts의 isMockData()·useMockData()·NO_BACKEND로 읽어 주세요 (frontend/CLAUDE.md 백엔드 연동)';
const NO_MOCK_ENV = [
  // import.meta.env.VITE_USE_MOCK
  {
    selector: "MemberExpression[property.name='VITE_USE_MOCK']",
    message: MOCK_ENV_MESSAGE,
  },
  // import.meta.env['VITE_USE_MOCK']
  {
    selector: "MemberExpression[property.value='VITE_USE_MOCK']",
    message: MOCK_ENV_MESSAGE,
  },
  // const { VITE_USE_MOCK } = import.meta.env
  {
    selector: "ObjectPattern > Property[key.name='VITE_USE_MOCK']",
    message: MOCK_ENV_MESSAGE,
  },
];

// 서버 데이터를 저장하는 버튼은 동기 잠금이 걸린 훅을 쓴다 (frontend/CLAUDE.md, #210)
const NO_USE_MUTATION = {
  name: '@tanstack/react-query',
  importNames: ['useMutation'],
  message:
    '서버 데이터를 저장하는 버튼은 hooks의 useSingleFlightMutation을 써 주세요 (더블클릭 중복 저장 방지, #210)',
};

// motion 컴포넌트 묶음을 가져오는 길도 막는다. LazyMotion·MotionConfig·AnimatePresence·useScroll 등은 그대로 쓴다
const MOTION_IMPORT_MESSAGE =
  "motion.div 대신 'motion/react-m'의 m.div를 써 주세요 (frontend/CLAUDE.md 움직임)";
const NO_MOTION_IMPORT = [
  {
    name: 'motion/react',
    importNames: ['motion'],
    message: MOTION_IMPORT_MESSAGE,
  },
  { name: 'motion/react-client', message: MOTION_IMPORT_MESSAGE },
];

export default tseslint.config(
  // 빌드 결과물 폴더는 린트 검사에서 제외
  // src/wasm은 wasm-pack이 만든 파일(npm run build:wasm), wasm/*/target은 러스트 빌드 결과
  { ignores: ['dist', 'src/wasm', 'wasm/*/target'] },
  {
    // JS, TS 공식 권장 규칙 일괄 적용 (배열 전개 구문)
    extends: [
      js.configs.recommended,
      ...tseslint.configs.recommended,
      reactX.configs['recommended-typescript'],
      reactDom.configs.recommended,
      // Fast Refresh: 컴포넌트 파일은 컴포넌트만 내보낸다(상수는 허용, error).
      // 상수·도우미 함수·훅은 같은 폴더의 별도 파일로 나눈다. 섞여 있으면 개발 중 그 파일을 고칠 때 화면 상태가 초기화된다.
      reactRefresh.configs.vite(),
    ],

    // 린트 적용할 파일 확장자 지정
    files: ['**/*.{ts,tsx}'],

    // 언어 환경 설정 (브라우저 전역 변수 허용 등)
    languageOptions: {
      ecmaVersion: 2020,
      globals: globals.browser,
    },

    // 사용할 외부 플러그인 등록
    plugins: {
      'react-hooks': reactHooks,
    },

    // 세부 규칙 커스텀
    rules: {
      ...reactHooks.configs.recommended.rules,

      // 프로젝트 규칙 (위 상수 참고)
      'no-restricted-syntax': [
        'error',
        NO_MOTION_COMPONENT,
        ...NO_DANGEROUS_HTML,
        ...NO_MOCK_ENV,
      ],
      'no-restricted-imports': [
        'error',
        { paths: [NO_USE_MUTATION, ...NO_MOTION_IMPORT] },
      ],
    },
  },

  // 예외: 목업 여부를 판단하는 원본 파일이라 여기서만 VITE_USE_MOCK을 읽는다
  {
    files: ['src/store/useDemoStore.ts'],
    rules: {
      'no-restricted-syntax': [
        'error',
        NO_MOTION_COMPONENT,
        ...NO_DANGEROUS_HTML,
      ],
    },
  },

  // 예외: useSingleFlightMutation 구현이 useMutation을 감싸는 곳이라 여기서만 가져온다
  {
    files: ['src/hooks/useSingleFlightMutation.ts'],
    rules: {
      'no-restricted-imports': ['error', { paths: NO_MOTION_IMPORT }],
    },
  },

  // 다른 모든 규칙 중 코드 스타일 관련 사항 덮어쓰기
  eslintConfigPrettier,
);
