import init, {
  inflate_entry,
  mask_texts,
} from '@/wasm/conversation-core/conversation_core';
import wasmUrl from '@/wasm/conversation-core/conversation_core_bg.wasm?url';

// 러스트로 만든 대화 처리 WebAssembly(frontend/wasm/conversation-core). 가리기와 zip 압축 풀기를 한다.
// 쓰기 전에 loadConversationCore()를 한 번 기다려야 한다(대화 처리 Worker는 요청마다, 테스트는 setupWasm.ts에서).
// 파일·대화는 이 브라우저 안의 WebAssembly 메모리에서만 다룬다

let loading: Promise<void> | null = null;
let ready = false;

export function loadConversationCore(): Promise<void> {
  loading ??= init({ module_or_path: wasmUrl }).then(() => {
    ready = true;
  });
  return loading;
}

// 테스트처럼 이미 초기화한 경우 (initSync)
export function markConversationCoreReady() {
  ready = true;
}

function ensureReady() {
  if (!ready) {
    throw new Error('대화 처리 WebAssembly를 아직 불러오지 않았어요');
  }
}

export interface CoreMaskMatch {
  kind: string;
  original: string;
  start: number;
  end: number;
}

// 글 여러 개를 같은 단어로 가린다 (가릴 단어 정규식은 한 번만 만든다)
export function coreMaskTexts(
  texts: string[],
  names: string[],
  terms: string[],
): { text: string; matches: CoreMaskMatch[] }[] {
  ensureReady();
  return JSON.parse(mask_texts(texts, names, terms)) as {
    text: string;
    matches: CoreMaskMatch[];
  }[];
}

// zip 항목 하나를 풀고 크기·CRC를 확인한다. 실패하면 이유 코드(INFLATE·METHOD·CORRUPT)를 메시지로 던진다
export function coreInflateEntry(
  compressed: Uint8Array,
  method: number,
  uncompressedSize: number,
  crc: number,
): Uint8Array {
  ensureReady();
  return inflate_entry(compressed, method, uncompressedSize, crc);
}
