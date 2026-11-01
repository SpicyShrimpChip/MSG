import {
  ATTACHMENT_PLACEHOLDER,
  IMAGE_PLACEHOLDER,
  type ConversationMessage,
  type ConversationSourceReader,
  type ParsedConversation,
} from '../types';

// ChatGPT 내보내기(conversations.json)의 대화는 메시지가 트리(mapping)로 들어 있다.
// 다시 생성하기·수정으로 가지가 갈라질 수 있어서, 마지막으로 본 가지(current_node)에서
// 위로 거슬러 올라가 한 줄로 만든다.
// 2026-11-01 실제 내보내기 파일로 확인함. 공식 형식 문서가 없어 바뀔 수 있다.
// - 대화는 100개씩 conversations-000.json, -001.json …으로 나뉜다
// - 역할은 user·assistant뿐이고, 생각 과정(thoughts·reasoning_recap)·맞춤 지침은 따로 된 메시지다
// - 답변 글에 화면에는 안 보이는 출처·위젯 표시(\ue200종류\ue202인자\ue201)가 섞여 있다
// - 첨부는 metadata.attachments에 이름·종류만 있다. 길게 붙여 넣은 글도 첨부가 되고 내용은 없다

interface RawNode {
  parent?: string | null;
  children?: string[];
  message?: {
    author?: { role?: string };
    content?: { content_type?: string; parts?: unknown[]; text?: string };
    metadata?: {
      is_visually_hidden_from_conversation?: boolean;
      attachments?: unknown[];
    };
  } | null;
}

// 출처·위젯 표시. 종류 뒤에 \ue202로 나뉜 인자가 붙는다
const MARKER = /([ \t]*)\ue200([^\ue201\ue202]+)(?:\ue202([^\ue201]*))?\ue201/g;
// 짝이 맞지 않고 남은 표시 문자
const STRAY_MARKER = /[\ue200-\ue202]/g;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function toIso(seconds: unknown): string | undefined {
  if (typeof seconds !== 'number' || !Number.isFinite(seconds))
    return undefined;
  return new Date(seconds * 1000).toISOString();
}

// 표시 하나를 화면에 보이던 글자로 바꾼다. 출처 칩·위젯처럼 글자가 아닌 것은 지운다
function markerText(kind: string, args: string[]): string {
  if (kind === 'entity') {
    // ["종류", "이름", "설명"] 중 이름이 문장 속 글자로 보인다
    try {
      const value: unknown = JSON.parse(args[0] ?? '');
      return Array.isArray(value) && typeof value[1] === 'string'
        ? value[1]
        : '';
    } catch {
      return '';
    }
  }
  // url은 [글자, 주소], link_title은 [글자, 검색 결과 id]
  if (kind === 'url' || kind === 'link_title') return args[0] ?? '';
  // cite·filecite(출처 칩), genui·image_group·products(위젯), message_reaction 등
  return '';
}

function stripMarkers(text: string): string {
  return text
    .replace(MARKER, (_, space: string, kind: string, rawArgs?: string) => {
      const replaced = markerText(kind, rawArgs?.split('\ue202') ?? []);
      // 지우는 표시 앞의 공백도 같이 지워 "써요 ." 처럼 남지 않게 한다
      return replaced ? space + replaced : '';
    })
    .replace(STRAY_MARKER, '');
}

// 이미지가 아닌 첨부(문서, 길게 붙여 넣은 글 등)가 있는지, 이미지 첨부가 있는지
function attachmentKinds(attachments: unknown[] | undefined) {
  let image = false;
  let other = false;
  for (const attachment of attachments ?? []) {
    const mime = isRecord(attachment) ? attachment.mime_type : undefined;
    if (typeof mime === 'string' && mime.startsWith('image/')) image = true;
    else other = true;
  }
  return { image, other };
}

// 현재 가지의 끝 노드: current_node가 있으면 그것, 없으면 마지막 자식만 따라 내려간 끝
function findLeafId(
  mapping: Record<string, RawNode>,
  currentNode: unknown,
): string | undefined {
  if (typeof currentNode === 'string' && mapping[currentNode])
    return currentNode;
  const root = Object.keys(mapping).find((id) => !mapping[id].parent);
  if (!root) return undefined;
  let id = root;
  const seen = new Set<string>();
  while (!seen.has(id)) {
    seen.add(id);
    const children = mapping[id].children ?? [];
    const next = children[children.length - 1];
    if (!next || !mapping[next]) break;
    id = next;
  }
  return id;
}

function nodeToMessage(node: RawNode): ConversationMessage | null {
  const message = node.message;
  if (!message) return null;
  if (message.metadata?.is_visually_hidden_from_conversation) return null;
  const role = message.author?.role;
  if (role !== 'user' && role !== 'assistant') return null;

  const content = message.content;
  if (!content) return null;
  const type = content.content_type;

  let text: string;
  let hasImagePart = false;
  if (type === 'text' || type === 'multimodal_text') {
    const parts = Array.isArray(content.parts) ? content.parts : [];
    text = parts
      .map((part) => {
        if (typeof part === 'string')
          return role === 'assistant' ? stripMarkers(part) : part;
        // 이미지 등 글자가 아닌 조각
        hasImagePart = true;
        return IMAGE_PLACEHOLDER;
      })
      .filter((part) => part.length > 0)
      .join('\n');
  } else if (type === 'code' && typeof content.text === 'string') {
    text = content.text;
  } else {
    // 생각 과정·맞춤 지침·도구 실행 결과 같은 종류는 화면에 보이는 대화가 아니라서 건너뛴다
    return null;
  }

  // 이미지 첨부는 보통 이미지 조각으로 이미 표시돼 있어서, 조각이 없을 때만 붙인다
  const lines = [text.trim()];
  const attached = attachmentKinds(message.metadata?.attachments);
  if (attached.image && !hasImagePart) lines.push(IMAGE_PLACEHOLDER);
  if (attached.other) lines.push(ATTACHMENT_PLACEHOLDER);

  const joined = lines.filter((line) => line.length > 0).join('\n');
  if (!joined) return null;
  return { role, text: joined };
}

function parseChatGptConversation(raw: unknown): ParsedConversation | null {
  if (!isRecord(raw) || !isRecord(raw.mapping)) return null;
  const mapping = raw.mapping as Record<string, RawNode>;

  const leafId = findLeafId(mapping, raw.current_node);
  if (!leafId) return null;

  const chain: RawNode[] = [];
  const seen = new Set<string>();
  let id: string | null | undefined = leafId;
  while (id && mapping[id] && !seen.has(id)) {
    seen.add(id);
    chain.push(mapping[id]);
    id = mapping[id].parent;
  }
  chain.reverse();

  const messages = chain
    .map(nodeToMessage)
    .filter((message): message is ConversationMessage => message !== null);
  if (messages.length === 0) return null;

  const rawId = raw.conversation_id ?? raw.id;
  return {
    id: typeof rawId === 'string' ? rawId : `chatgpt-${leafId}`,
    source: 'chatgpt',
    title: typeof raw.title === 'string' ? raw.title.trim() : '',
    createdAt: toIso(raw.create_time),
    messages,
  };
}

// conversations.json(나뉘면 conversations-000.json 등)은 대화 배열이고, 대화마다 mapping이 있다
export const chatGptReader: ConversationSourceReader = {
  source: 'chatgpt',
  matches: ({ json }) =>
    Array.isArray(json) && isRecord(json[0]) && isRecord(json[0].mapping),
  read: ({ json }) =>
    Array.isArray(json) ? json.map(parseChatGptConversation) : [],
};
