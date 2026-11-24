import {
  ATTACHMENT_PLACEHOLDER,
  IMAGE_PLACEHOLDER,
  type ConversationMessage,
  type ConversationSourceReader,
  type ParsedConversation,
} from '../types';
import { isRecord } from '../shared';

// Claude 내보내기(conversations-000.zip 안의 conversations.json)는 대화마다 chat_messages 배열이 있다.
// 2026-10-31 실제 내보내기 파일로 확인함. 형식은 바뀔 수 있다.
// - 메시지 글은 content 블록의 text만 모은다. text 필드에는 생각 과정·도구 실행 기록이 섞여 있다
// - 글이 하나도 없는 대화가 있다(파일 자체가 비어 있음). 건너뛴다
// - files는 올린 파일 이름만 있고(이름이 null인 것도 많다), 이미지와 문서가 섞여 있다
// - attachments의 추출 내용(extracted_content)은 사용자의 학습 자료·개인 문서일 수 있어
//   가져오지 않고 [첨부 파일] 표시만 남긴다

const IMAGE_EXTENSIONS = new Set([
  'png',
  'jpg',
  'jpeg',
  'gif',
  'webp',
  'heic',
  'bmp',
]);

function toIso(value: unknown): string | undefined {
  if (typeof value !== 'string') return undefined;
  const time = Date.parse(value);
  return Number.isNaN(time) ? undefined : new Date(time).toISOString();
}

function messageText(raw: Record<string, unknown>): string {
  // content 블록이 있으면 그 안의 text만 모으고, 없으면 text 필드를 쓴다
  if (Array.isArray(raw.content)) {
    const texts = raw.content
      .filter(
        (block): block is { type: string; text: string } =>
          isRecord(block) &&
          block.type === 'text' &&
          typeof block.text === 'string',
      )
      .map((block) => block.text);
    if (texts.length > 0) return texts.join('\n');
  }
  return typeof raw.text === 'string' ? raw.text : '';
}

// 확장자. 이름이 없거나 확장자가 없으면 undefined
function extensionOf(name: unknown): string | undefined {
  if (typeof name !== 'string' || !name.includes('.')) return undefined;
  return name.split('.').pop()!.toLowerCase();
}

// 이미지와 그 밖의 파일을 나눠 표시한다.
// 이름으로 알 수 없는 파일은 메시지에 이미지 블록이 있으면 이미지로, 없으면 [첨부 파일]로 본다
function filePlaceholders(raw: Record<string, unknown>): string[] {
  const files = Array.isArray(raw.files) ? raw.files : [];
  const attachments = Array.isArray(raw.attachments) ? raw.attachments : [];
  const hasImageBlock =
    Array.isArray(raw.content) &&
    raw.content.some((block) => isRecord(block) && block.type === 'image');

  let image = false;
  let other = attachments.length > 0;
  for (const file of files) {
    const extension = extensionOf(isRecord(file) ? file.file_name : undefined);
    if (extension ? IMAGE_EXTENSIONS.has(extension) : hasImageBlock)
      image = true;
    else other = true;
  }

  const lines: string[] = [];
  if (image) lines.push(IMAGE_PLACEHOLDER);
  if (other) lines.push(ATTACHMENT_PLACEHOLDER);
  return lines;
}

function toMessage(raw: unknown): ConversationMessage | null {
  if (!isRecord(raw)) return null;
  const role =
    raw.sender === 'human'
      ? 'user'
      : raw.sender === 'assistant'
        ? 'assistant'
        : null;
  if (!role) return null;

  const lines: string[] = [];
  const text = messageText(raw).trim();
  if (text) lines.push(text);
  lines.push(...filePlaceholders(raw));
  if (lines.length === 0) return null;
  return { role, text: lines.join('\n') };
}

function parseClaudeConversation(raw: unknown): ParsedConversation | null {
  if (!isRecord(raw) || !Array.isArray(raw.chat_messages)) return null;

  const messages = raw.chat_messages
    .map(toMessage)
    .filter((message): message is ConversationMessage => message !== null);
  if (messages.length === 0) return null;

  return {
    id: typeof raw.uuid === 'string' ? raw.uuid : `claude-${String(raw.name)}`,
    source: 'claude',
    title: typeof raw.name === 'string' ? raw.name.trim() : '',
    createdAt: toIso(raw.created_at),
    messages,
  };
}

export const claudeReader: ConversationSourceReader = {
  source: 'claude',
  matches: ({ json }) =>
    Array.isArray(json) &&
    isRecord(json[0]) &&
    Array.isArray(json[0].chat_messages),
  read: ({ json }) =>
    Array.isArray(json) ? json.map(parseClaudeConversation) : [],
};
