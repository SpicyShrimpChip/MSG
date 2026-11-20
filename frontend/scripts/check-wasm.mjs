// 커밋한 WASM(src/wasm/conversation-core)이 지금 러스트 소스로 빌드한 것인지 확인한다 (CI용, 러스트 없이 돈다).
// 실패하면 frontend에서 npm run build:wasm 후 결과를 커밋한다
import { readFileSync } from 'node:fs';
import { OUT_DIR, sourceHash } from './wasm-source-hash.mjs';

const built = readFileSync(new URL('SOURCE_HASH', OUT_DIR), 'utf8').trim();
const current = sourceHash();
if (built !== current) {
  console.error(
    '러스트 소스가 바뀌었는데 WASM을 다시 빌드하지 않았어요. frontend에서 npm run build:wasm 후 커밋해 주세요.',
  );
  process.exit(1);
}
console.log('WASM이 러스트 소스와 맞아요.');
