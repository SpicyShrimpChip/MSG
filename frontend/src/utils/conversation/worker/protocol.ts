import type { MaskOptions, MaskedConversationWithSpots } from '../mask';
import type { MergedImport } from '../merge';
import type { ParsedConversation } from '../types';

// 화면 ↔ 대화 처리 Web Worker 사이에 오가는 메시지. 파일·대화는 이 브라우저 안에서만 오간다
export type WorkerRequest =
  | { id: number; type: 'read'; files: File[] }
  | {
      id: number;
      type: 'mask';
      conversations: ParsedConversation[];
      options: MaskOptions;
    };

export type WorkerResult = {
  read: MergedImport;
  mask: MaskedConversationWithSpots[];
};

export type WorkerResponse =
  | { id: number; ok: true; result: WorkerResult[keyof WorkerResult] }
  | { id: number; ok: false };
