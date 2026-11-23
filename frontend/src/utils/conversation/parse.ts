import { readClaudeManifest } from './manifest';
import { SOURCE_READERS } from './sources';
import type { ExportFile, ParseResult, ParsedConversation } from './types';

const UNTITLED = '제목 없는 대화';
// 첫 질문으로 만드는 제목의 최대 글자 수
const TITLE_MAX_LENGTH = 40;

// 눈에 보이지 않는 NUL 문자(U+0000). 코드 실행 결과를 붙여 넣은 대화 등에 들어 있을 수 있는데,
// 서버 DB(PostgreSQL)가 저장하지 못해 대화 묶음 전체가 실패했다(#215). 서버도 지우지만
// 미리보기·가리기·보내기가 같은 글을 보도록 읽을 때 지운다. 다른 글자는 그대로 둔다.
const NUL = '\u0000';
// JSON 글자 안의 NUL은 늘 이스케이프(\u0000)로 적힌다(날것의 제어 문자는 JSON.parse가 거절한다)
const ESCAPED_NUL = '\\u0000';

function withoutNul(_key: string, value: unknown): unknown {
  return typeof value === 'string' ? value.replaceAll(NUL, '') : value;
}

// 큰 파일에서 reviver는 JSON.parse를 크게 느리게 하므로 NUL이 있을 수 있는 파일에만 쓴다
// (`\\u0000`처럼 역슬래시 글자 뒤에 온 경우도 걸리지만, NUL만 지우므로 결과는 같다)
function readJson(text: string): unknown {
  try {
    return text.includes(ESCAPED_NUL)
      ? JSON.parse(text, withoutNul)
      : JSON.parse(text);
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
