import type { ClaudeManifest } from './manifest';
import type { ParseResult, ParsedConversation } from './types';

export interface FileParseResult {
  fileName: string;
  result: ParseResult;
}

export interface MergedImport {
  // 최신 대화가 위로
  conversations: ParsedConversation[];
  // 글이 없거나 형식이 달라 파서가 건너뛴 대화 수 (모든 파일 합계)
  skippedCount: number;
  // 여러 파일에 같은 대화가 들어 있어 하나로 합친 수
  duplicateCount: number;
  // 읽지 못한 파일과 그 이유 (화면에 그대로 보여 준다). manifest가 있으면 오류가 아니라 받기 안내
  errors: {
    fileName: string;
    title: string;
    message: string;
    manifest?: ClaudeManifest;
  }[];
  // 오류는 아니지만 알려 줄 것 (회색 안내): 대화 없는 파일을 건너뜀, 잘린 zip 등
  notices: { fileName: string; message: string }[];
}

// 같은 서비스의 같은 대화 id면 같은 대화로 본다
export const conversationKey = (conversation: ParsedConversation) =>
  `${conversation.source}:${conversation.id}`;

// 파일 여러 개(ChatGPT는 conversations-000.json처럼 나뉘기도 한다)의 파싱 결과를 하나로 합친다
export function mergeParseResults(files: FileParseResult[]): MergedImport {
  const byKey = new Map<string, ParsedConversation>();
  let skippedCount = 0;
  let duplicateCount = 0;
  const errors: MergedImport['errors'] = [];

  for (const { fileName, result } of files) {
    if (!result.ok) {
      errors.push({
        fileName,
        title: result.title,
        message: result.message,
        manifest: result.manifest,
      });
      continue;
    }
    skippedCount += result.skippedCount;
    for (const conversation of result.conversations) {
      const key = conversationKey(conversation);
      if (byKey.has(key)) {
        duplicateCount += 1;
        continue;
      }
      byKey.set(key, conversation);
    }
  }

  // 날짜가 없는 대화는 맨 뒤로
  const conversations = [...byKey.values()].sort((a, b) =>
    (b.createdAt ?? '').localeCompare(a.createdAt ?? ''),
  );
  return { conversations, skippedCount, duplicateCount, errors, notices: [] };
}
