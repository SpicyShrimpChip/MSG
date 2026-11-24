import { isRecord } from './shared';

// Claude 데이터 내보내기는 먼저 "다운로드 목록 파일"(manifest-....json)을 주고,
// 그 안의 링크로 zip을 하나씩 받는 방식이다(2026-10-31 실제 파일로 확인). 목록 파일에는 대화가 없다.
// 우리 사이트·서버가 링크를 대신 열지 않는다(claude.ai 로그인이 필요하고, 원본이 서버로 오면 안 된다).
// 사용자 브라우저에서 바로 받도록 링크만 보여 준다

// 링크는 만든 뒤 24시간 동안만 쓸 수 있다
const LINK_LIFETIME_MS = 24 * 60 * 60 * 1000;

export interface ClaudeManifest {
  // 대화 기록 zip을 받는 주소. claude.ai의 내보내기 주소가 아니면 없다
  downloadUrl?: string;
  // 받을 파일 이름 (보통 conversations-000.zip)
  fileName: string;
  // 링크가 끝나는 시각 (ISO). 알 수 없으면 없다
  expiresAt?: string;
}

// 올린 파일 안의 주소라서 그대로 믿지 않는다. https의 claude.ai /export/ 주소만 통과시킨다
export function safeClaudeExportUrl(raw: unknown): string | undefined {
  if (typeof raw !== 'string') return undefined;
  let url: URL;
  try {
    url = new URL(raw);
  } catch {
    return undefined;
  }
  if (
    url.protocol !== 'https:' ||
    url.hostname !== 'claude.ai' ||
    url.port !== '' ||
    url.username !== '' ||
    url.password !== '' ||
    !url.pathname.startsWith('/export/')
  ) {
    return undefined;
  }
  return url.href;
}

// Claude 다운로드 목록 파일이면 대화 기록 zip 정보를, 아니면 null을 돌려준다.
// 로그인 기록·메모리 같은 다른 항목의 링크는 꺼내지 않는다
export function readClaudeManifest(json: unknown): ClaudeManifest | null {
  if (!isRecord(json) || !Array.isArray(json.data_files)) return null;
  const files = json.data_files.filter(isRecord);
  if (
    files.length === 0 ||
    !files.every((file) => 'export_url' in file && 'category' in file)
  ) {
    return null;
  }

  const conversations = files.find((file) => file.category === 'conversations');
  const created =
    typeof json.created_at === 'string' ? Date.parse(json.created_at) : NaN;
  return {
    downloadUrl: safeClaudeExportUrl(conversations?.export_url),
    fileName:
      typeof conversations?.filename === 'string'
        ? conversations.filename
        : 'conversations-000.zip',
    expiresAt: Number.isNaN(created)
      ? undefined
      : new Date(created + LINK_LIFETIME_MS).toISOString(),
  };
}
