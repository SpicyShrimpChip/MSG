/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    // tsconfig의 paths(@/ → src/)를 그대로 쓴다 (Vite 8 내장 기능)
    tsconfigPaths: true,
  },
  // 대화 처리 Web Worker(utils/conversation/worker)를 ES 모듈로 묶는다 (Worker 안에서도 코드 나누기가 되게)
  worker: {
    format: 'es',
  },
  server: {
    // /api 요청은 로컬 스프링 서버로 넘긴다. 배포 때는 Vercel rewrite가 같은 역할을 한다
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  test: {
    include: ['src/**/*.test.ts'],
    // 대화 처리 WebAssembly(러스트)를 테스트 전에 초기화한다
    setupFiles: ['src/test/setupWasm.ts'],
  },
});
