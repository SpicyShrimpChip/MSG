// 화면 오류(라우터 errorElement)에서 쓰는 판단 함수

// 배포가 바뀐 뒤 열려 있던 탭이 옛 이름의 화면 코드를 불러오다 실패한 경우
// (Vercel은 없는 파일에도 index.html을 돌려줘서 import가 실패한다). 브라우저마다 문구가 다르다
const CHUNK_ERROR_PATTERNS = [
  'Failed to fetch dynamically imported module', // Chrome·Edge
  'error loading dynamically imported module', // Firefox
  'Importing a module script failed', // Safari
  'is not a valid JavaScript MIME type', // index.html을 받은 경우
];

export function isChunkLoadError(error: unknown): boolean {
  if (!(error instanceof Error)) return false;
  if (error.name === 'ChunkLoadError') return true;
  return CHUNK_ERROR_PATTERNS.some((pattern) =>
    error.message.toLowerCase().includes(pattern.toLowerCase()),
  );
}

// 새로고침해도 같은 오류가 나면 계속 새로고침하지 않도록, 자동 새로고침은 이 시간 안에 한 번만
export const CHUNK_RELOAD_WINDOW_MS = 30_000;

// 직전 자동 새로고침 시각(lastReloadAt, 없으면 null)을 보고 지금 다시 새로고침해도 되는지
export function shouldAutoReload(
  lastReloadAt: number | null,
  now: number,
): boolean {
  return lastReloadAt === null || now - lastReloadAt > CHUNK_RELOAD_WINDOW_MS;
}
