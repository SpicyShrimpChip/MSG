import {
  maskConversations,
  type MaskOptions,
  type MaskedConversationWithSpots,
} from '../mask';
import type { MergedImport } from '../merge';
import { readExportFiles } from '../readExportFiles';
import type { ParsedConversation } from '../types';
import type { WorkerRequest, WorkerResponse } from './protocol';

// 대화 처리 Web Worker를 부르는 쪽. Worker를 만들 수 없는 환경(테스트, 아주 오래된 브라우저)이나
// Worker가 깨지면 같은 함수를 이 스레드에서 직접 부른다(결과는 같고, 큰 파일에서 화면이 잠깐 멈출 수 있을 뿐이다)

type Pending = {
  resolve: (value: unknown) => void;
  reject: (error: Error) => void;
};

let worker: Worker | null | undefined;
let nextId = 1;
const pending = new Map<number, Pending>();

class ConversationWorkerError extends Error {}

function failAll() {
  for (const { reject } of pending.values()) {
    reject(new ConversationWorkerError('대화 처리 중 문제가 생겼어요.'));
  }
  pending.clear();
}

function getWorker(): Worker | null {
  if (worker !== undefined) return worker;
  if (typeof Worker === 'undefined') {
    worker = null;
    return worker;
  }
  try {
    const created = new Worker(
      new URL('./conversation.worker.ts', import.meta.url),
      { type: 'module', name: 'conversation' },
    );
    created.onmessage = (event: MessageEvent<WorkerResponse>) => {
      const response = event.data;
      const waiting = pending.get(response.id);
      if (!waiting) return;
      pending.delete(response.id);
      if (response.ok) waiting.resolve(response.result);
      else
        waiting.reject(
          new ConversationWorkerError('대화 처리 중 문제가 생겼어요.'),
        );
    };
    // Worker 파일을 못 불러왔거나 Worker가 죽으면, 기다리던 요청은 실패로 끝내고 다음부터는 이 스레드에서 한다
    created.onerror = () => {
      created.terminate();
      worker = null;
      failAll();
    };
    worker = created;
  } catch {
    worker = null;
  }
  return worker;
}

// 요청 종류별 내용 (id는 call이 붙인다)
type RequestPayload = WorkerRequest extends infer T
  ? T extends WorkerRequest
    ? Omit<T, 'id'>
    : never
  : never;

function call<R>(
  payload: RequestPayload,
  fallback: () => Promise<R>,
): Promise<R> {
  const target = getWorker();
  if (!target) return fallback();
  const id = nextId++;
  return new Promise<R>((resolve, reject) => {
    pending.set(id, {
      resolve: resolve as (value: unknown) => void,
      reject,
    });
    target.postMessage({ ...payload, id } satisfies WorkerRequest);
  });
}

// 고른 내보내기 파일들을 읽는다 (zip 풀기·파싱, Worker에서)
export function readExportFilesOffThread(files: File[]): Promise<MergedImport> {
  return call({ type: 'read', files }, () => readExportFiles(files));
}

// 대화 여러 개를 한꺼번에 가리고 가린 곳 목록도 만든다 (Worker에서)
export function maskConversationsOffThread(
  conversations: ParsedConversation[],
  options: MaskOptions,
): Promise<MaskedConversationWithSpots[]> {
  return call({ type: 'mask', conversations, options }, () =>
    maskConversations(conversations, options),
  );
}
