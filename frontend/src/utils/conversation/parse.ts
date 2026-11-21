import { readClaudeManifest } from './manifest';
import { SOURCE_READERS } from './sources';
import type { ExportFile, ParseResult, ParsedConversation } from './types';

const UNTITLED = '제목 없는 대화';
// 첫 질문으로 만드는 제목의 최대 글자 수
const TITLE_MAX_LENGTH = 40;

function readJson(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return undefined;
  }
}

// 제목이 없는 대화는 첫 질문 앞부분으로 제목을 만든다 (Claude는 제목 없는 대화가 많다)
function titleFromMessages(messages: ParsedConversation['messages']): string {
  const first = messages.find((message) => message.role === 'user');
  const line = first?.text.replace(/\s+/g, ' ').trim() ?? '';
  if (!line) return UNTITLED;
  return line.length > TITLE_MAX_LENGTH
    ? `${line.slice(0, TITLE_MAX_LENGTH).trimEnd()}…`
    : line;
}

// 파일에서 읽은 글자를 대화 목록으로 바꾼다. 서버로는 아무것도 보내지 않는다
export function parseConversationExport(
  name: string,
  text: string,
): ParseResult {
  const file: ExportFile = { name, text, json: readJson(text) };

  const manifest = readClaudeManifest(file.json);
  if (manifest) {
    return {
      ok: false,
      title: 'Claude 다운로드 목록 파일이에요',
      message:
        '이 파일에는 대화가 없고 받을 파일의 링크만 있어요. 대화 기록 zip을 받은 뒤, 그 zip을 그대로 여기에 놓아 주세요.',
      manifest,
    };
  }

  const reader = SOURCE_READERS.find((candidate) => candidate.matches(file));
  if (!reader) {
    if (file.json === undefined && name.toLowerCase().endsWith('.json')) {
      return {
        ok: false,
        title: '파일을 읽지 못했어요',
        message:
          '파일이 중간에 잘리지 않았는지 확인하고, 내보내기 파일을 다시 받아 주세요.',
      };
    }
    return {
      ok: false,
      title: '대화 기록 파일이 아니에요',
      message: 'ChatGPT나 Claude에서 내보낸 zip을 그대로 올려 주세요.',
    };
  }

  const conversations: ParsedConversation[] = [];
  let skippedCount = 0;
  for (const parsed of reader.read(file)) {
    if (!parsed) {
      skippedCount += 1;
      continue;
    }
    conversations.push(
      parsed.title
        ? parsed
        : { ...parsed, title: titleFromMessages(parsed.messages) },
    );
  }

  if (conversations.length === 0) {
    return {
      ok: false,
      title: '글이 있는 대화가 없어요',
      message:
        '대화는 찾았지만 모두 비어 있어요. 이미지만 있는 대화이거나 내보내기 형식이 바뀌었을 수 있어요.',
    };
  }
  return { ok: true, source: reader.source, conversations, skippedCount };
}
