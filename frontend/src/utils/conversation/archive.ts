import { strFromU8 } from 'fflate';
import {
  extractEntry,
  readCentralDirectory,
  scanLocalEntries,
  ZipDataError,
  ZipFormatError,
  type ZipEntry,
} from './zipReader';

// 내보내기 zip을 브라우저 안에서 연다. 서버로는 아무것도 보내지 않는다.
// zip 전체를 메모리에 올리지 않고(Blob.slice) 목차를 본 뒤 대화 기록 파일만 꺼내 푼다.
// 다른 파일(첨부·로그인 기록·메모리 등)은 읽지 않는다. 목차가 없는(중간에 잘린) zip은 앞에서부터 헤더를 따라가며 건진다

// 풀어서 읽을 대화 기록 파일 이름. ChatGPT는 conversations.json(나뉘면 conversations-000.json 등),
// Claude는 conversations-000.zip 안의 conversations.json
const CONVERSATIONS_ENTRY = /(^|\/)conversations(-\d+)?\.json$/i;

// 대화는 없지만 내보내기에 들어 있는 것으로 알려진 파일 (이름만 보고 판단한다)
// ChatGPT: 첨부 이름 목록·계정·피드백·모델 비교·공유·광고, Claude: 계정·로그인 기록
const KNOWN_NON_CONVERSATION_FILE =
  /(^|\/)(conversation_asset_file_names|user|users|message_feedback|model_comparisons|shared_conversations|ads|login_history)\.json$/i;

export const isKnownNonConversationFile = (name: string) =>
  KNOWN_NON_CONVERSATION_FILE.test(name);

// 풀었을 때 이보다 크면 열지 않는다 (잘못 만든 압축 파일로 브라우저가 멈추지 않게)
// 실제 Claude 대화 기록은 31MB였다(2026-10-31)
export const MAX_UNZIPPED_BYTES = 300 * 1024 * 1024;

export interface ZipProblem {
  title: string;
  message: string;
}

export type ZipReadResult =
  // truncated: 목차가 없어 앞에서부터 읽었고 끝이 잘려 빠진 대화가 있을 수 있다
  | {
      ok: true;
      files: { name: string; text: string }[];
      truncated?: true;
    }
  | ({ ok: false } & ZipProblem);

// 대화 기록이 없는 zip이면 무엇인지 알아보고 안내 문구를 만든다 (이름만 보고 판단한다)
export function describeZipWithoutConversations(names: string[]): ZipProblem {
  const has = (pattern: RegExp) => names.some((name) => pattern.test(name));

  if (has(/^Takeout\//)) {
    return {
      title: 'Gemini 대화는 아직 가져올 수 없어요',
      message:
        'Gemini 가져오기는 준비 중이에요. 지금은 ChatGPT나 Claude에서 내보낸 zip을 올려 주세요.',
    };
  }
  // Claude 내보내기의 다른 zip들 (users.json 등은 계정 정보, projects/ 등은 프로젝트·메모리)
  if (
    has(
      /^(users\.json|login_history\.json|projects\/|memories\/|reflections\/|claude_code_cloud\/)/,
    )
  ) {
    return {
      title: '대화 기록이 들어 있는 zip이 아니에요',
      message:
        'Claude 내보내기는 zip이 여러 개로 와요. 그중 conversations-000.zip을 올려 주세요.',
    };
  }
  return {
    title: '대화 기록 파일이 아니에요',
    message: 'ChatGPT나 Claude에서 내보낸 zip을 그대로 올려 주세요.',
  };
}

const OPEN_FAILED: ZipReadResult = {
  ok: false,
  title: '압축 파일을 열지 못했어요',
  message:
    '파일이 중간에 잘리지 않았는지 확인하고, 내보내기 파일을 다시 받아 주세요.',
};

const TOO_LARGE: ZipReadResult = {
  ok: false,
  title: '대화 기록이 너무 커요',
  message: `풀었을 때 ${MAX_UNZIPPED_BYTES / 1024 / 1024}MB가 넘는 파일은 브라우저에서 열지 않아요.`,
};

const CUT_OFF: ZipReadResult = {
  ok: false,
  title: '압축 파일이 잘려 있어요',
  message:
    '대화 기록 파일을 찾기 전에 파일이 끝났어요. 내보내기 파일을 다시 받아 주세요.',
};

// 푼 크기 합계가 상한을 넘지 않게 센다
function createSizeBudget() {
  let used = 0;
  return {
    take(size: number) {
      if (used + size > MAX_UNZIPPED_BYTES) return false;
      used += size;
      return true;
    },
  };
}

type Found = { name: string; text: string }[];

// 중앙 목차가 있는 정상 zip: 목차에서 대화 파일만 골라 꺼낸다
async function readWithDirectory(
  blob: Blob,
  entries: ZipEntry[],
): Promise<ZipReadResult> {
  const budget = createSizeBudget();
  const files: Found = [];
  let tooLarge = false;
  for (const entry of entries) {
    if (!CONVERSATIONS_ENTRY.test(entry.name)) continue;
    if (!budget.take(entry.uncompressedSize)) {
      tooLarge = true;
      continue;
    }
    files.push({
      name: entry.name,
      text: strFromU8(await extractEntry(blob, entry)),
    });
  }
  if (files.length > 0) return { ok: true, files };
  if (tooLarge) return TOO_LARGE;
  return {
    ok: false,
    ...describeZipWithoutConversations(entries.map((entry) => entry.name)),
  };
}

// 중앙 목차가 없는 zip(잘린 파일): 앞에서부터 헤더를 따라가며 대화 파일을 건진다
// 파일 순서는 공식으로 보장된 게 아니라서, 데이터는 건너뛰되 끝까지 헤더를 본다
async function readWithScan(blob: Blob): Promise<ZipReadResult> {
  const budget = createSizeBudget();
  const files: Found = [];
  const names: string[] = [];
  let tooLarge = false;
  let stoppedEarly = false;
  for await (const item of scanLocalEntries(blob)) {
    if (item.kind !== 'entry') {
      // 잘린 파일의 이름은 어떤 zip인지 알아보는 데만 쓴다 (내용은 못 꺼낸다)
      if (item.kind === 'truncated' && item.partialName) {
        names.push(item.partialName);
      }
      stoppedEarly = true;
      break;
    }
    names.push(item.entry.name);
    if (!CONVERSATIONS_ENTRY.test(item.entry.name)) continue;
    if (!budget.take(item.entry.uncompressedSize)) {
      tooLarge = true;
      continue;
    }
    try {
      files.push({
        name: item.entry.name,
        text: strFromU8(await item.extract()),
      });
    } catch (error) {
      // 이 대화 파일은 데이터가 깨졌다. 버리고 여기서 멈춘다
      if (!(error instanceof ZipDataError)) throw error;
      stoppedEarly = true;
      break;
    }
  }
  // 헤더를 하나도 못 읽었으면 zip이 아니다
  if (names.length === 0) return OPEN_FAILED;
  if (files.length > 0) {
    return { ok: true, files, ...(stoppedEarly ? { truncated: true } : {}) };
  }
  if (tooLarge) return TOO_LARGE;
  if (stoppedEarly) return CUT_OFF;
  return { ok: false, ...describeZipWithoutConversations(names) };
}

export async function readExportZip(
  source: Blob | Uint8Array,
): Promise<ZipReadResult> {
  const blob = source instanceof Blob ? source : new Blob([source as BlobPart]);
  let entries: ZipEntry[] | null = null;
  try {
    entries = await readCentralDirectory(blob);
  } catch (error) {
    // 목차가 깨졌으면 앞에서부터 읽어 본다
    if (!(error instanceof ZipFormatError)) throw error;
  }
  try {
    return entries
      ? await readWithDirectory(blob, entries)
      : await readWithScan(blob);
  } catch (error) {
    if (error instanceof ZipDataError || error instanceof ZipFormatError) {
      return OPEN_FAILED;
    }
    throw error;
  }
}
