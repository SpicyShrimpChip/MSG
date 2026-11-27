import {
  CONTENT_DISABLED_MESSAGE,
  MATERIAL_EXTENSIONS,
  MATERIAL_MAX_SIZE_MB,
} from '@/constants';
import type { QuizAiStatus } from '@/types';

// 지원 형식 안내 문구 (예: "PDF, TXT, Markdown")
export const MATERIAL_FORMAT_LABEL = MATERIAL_EXTENSIONS.map((ext) =>
  ext === 'md' ? 'Markdown' : ext.toUpperCase(),
).join(', ');

// 올릴 파일의 형식·크기를 검사한다. 문제가 없으면 undefined, 있으면 사용자에게 보일 문구
// 서버도 같은 검사를 해야 한다 (브라우저 검사는 우회할 수 있다)
export function validateMaterialFile(
  file: Pick<File, 'name' | 'size'>,
): string | undefined {
  const ext = file.name.includes('.')
    ? (file.name.split('.').pop()?.toLowerCase() ?? '')
    : '';
  if (!(MATERIAL_EXTENSIONS as readonly string[]).includes(ext)) {
    return `${MATERIAL_FORMAT_LABEL} 파일만 올릴 수 있어요.`;
  }
  if (file.size === 0) return '빈 파일은 올릴 수 없어요.';
  if (file.size > MATERIAL_MAX_SIZE_MB * 1024 * 1024) {
    return `파일은 ${MATERIAL_MAX_SIZE_MB}MB까지 올릴 수 있어요.`;
  }
  return undefined;
}

// 본문을 읽을 수 있는 형식인지 (서버 MaterialTextExtractor와 같다). 올릴 수 있는 형식은 모두 읽지만,
// PDF는 글이 들어 있는 PDF만 읽고 스캔본은 OCR이 없어 서버가 "본문 없음"으로 알려 준다
export function isReadable(fileName: string): boolean {
  const ext = fileName.split('.').pop()?.toLowerCase() ?? '';
  return (MATERIAL_EXTENSIONS as readonly string[]).includes(ext);
}

export const UNREADABLE_FORMAT_MESSAGE =
  '이 형식은 본문을 읽지 못해요. TXT·MD·PPTX·글이 들어 있는 PDF만 읽어요.';

// 자료 본문을 외부 AI로 보낼 수 없는 이유 (운영 설정 꺼짐 → 읽지 못하는 형식 순). 보낼 수 있으면 null
export function materialContentBlockedReason(
  ai: Pick<QuizAiStatus, 'materialContentEnabled'>,
  fileName: string,
): string | null {
  if (!ai.materialContentEnabled) return CONTENT_DISABLED_MESSAGE;
  if (!isReadable(fileName)) return UNREADABLE_FORMAT_MESSAGE;
  return null;
}

// AI 퀴즈 기능(개념 뽑기·요약·평가)을 쓸 수 없는 이유 (AI 연결 없음 → AI 퀴즈 처리 동의 없음 순). 쓸 수 있으면 null.
// action은 "AI로 ○○려면"에 들어갈 말(예: '요약을 만들'). 내 정보를 아직 못 읽었으면(undefined) 막지 않는다
export function aiQuizBlockedReason(
  ai: Pick<QuizAiStatus, 'available' | 'message'>,
  aiQuizConsent: boolean | undefined,
  action: string,
): string | null {
  if (!ai.available) return ai.message;
  if (aiQuizConsent === false)
    return `AI로 ${action}려면 AI 퀴즈 처리 동의가 필요해요. 마이페이지에서 동의할 수 있어요.`;
  return null;
}
