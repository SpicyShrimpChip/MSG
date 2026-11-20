import { readFileSync } from 'node:fs';
import { initSync } from '../wasm/conversation-core/conversation_core';
import { markConversationCoreReady } from '../utils/conversation/core';

// 테스트(Node)에서는 fetch 대신 파일을 읽어 대화 처리 WebAssembly를 바로 초기화한다
initSync({
  module: readFileSync(
    new URL(
      '../wasm/conversation-core/conversation_core_bg.wasm',
      import.meta.url,
    ),
  ),
});
markConversationCoreReady();
