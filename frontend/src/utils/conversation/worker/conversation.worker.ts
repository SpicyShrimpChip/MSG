/// <reference lib="webworker" />
import { maskConversationWithSpots } from '../mask';
import { readExportFiles } from '../readExportFiles';
import type { WorkerRequest, WorkerResponse } from './protocol';

// 대화 내보내기 파일 읽기(zip 풀기·파싱)와 개인정보 가리기를 화면과 다른 스레드에서 한다.
// 큰 파일을 읽는 동안에도 화면이 멈추지 않는다. 파일·대화는 이 브라우저 안에서만 다루고 어디로도 보내지 않는다
const scope = self as unknown as DedicatedWorkerGlobalScope;

async function handle(request: WorkerRequest) {
  switch (request.type) {
    case 'read':
      return readExportFiles(request.files);
    case 'mask':
      return request.conversations.map((conversation) =>
        maskConversationWithSpots(conversation, request.options),
      );
  }
}

scope.onmessage = (event: MessageEvent<WorkerRequest>) => {
  const request = event.data;
  handle(request).then(
    (result) =>
      scope.postMessage({
        id: request.id,
        ok: true,
        result,
      } satisfies WorkerResponse),
    // 오류 내용에 대화 글이 섞일 수 있어 화면에는 실패 여부만 넘긴다
    () =>
      scope.postMessage({ id: request.id, ok: false } satisfies WorkerResponse),
  );
};
