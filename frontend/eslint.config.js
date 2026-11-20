import js from '@eslint/js';
import globals from 'globals';
import reactHooks from 'eslint-plugin-react-hooks';
import reactX from 'eslint-plugin-react-x';
import reactDom from 'eslint-plugin-react-dom';
import tseslint from 'typescript-eslint';
import eslintConfigPrettier from 'eslint-config-prettier';

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
    },
  },

  // 다른 모든 규칙 중 코드 스타일 관련 사항 덮어쓰기
  eslintConfigPrettier,
);
