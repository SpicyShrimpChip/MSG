// 러스트 크레이트를 WebAssembly로 빌드해 src/wasm/conversation-core에 둔다 (결과는 커밋한다. Vercel에는 러스트가 없다).
// 필요: rustup의 wasm32-unknown-unknown 대상, wasm-pack. 실행: npm run build:wasm
import { execFileSync } from 'node:child_process';
import { rmSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { CRATE_DIR, OUT_DIR, sourceHash } from './wasm-source-hash.mjs';

const outDir = fileURLToPath(OUT_DIR);
execFileSync(
  'wasm-pack',
  [
    'build',
    fileURLToPath(CRATE_DIR),
    '--target',
    'web',
    '--release',
    '--no-pack',
    '--out-dir',
    outDir,
  ],
  { stdio: 'inherit' },
);
// wasm-pack이 만드는 부속 파일은 쓰지 않는다
for (const name of ['.gitignore', 'package.json', 'README.md']) {
  rmSync(new URL(name, OUT_DIR), { force: true });
}
writeFileSync(new URL('SOURCE_HASH', OUT_DIR), `${sourceHash()}\n`);
console.log('WASM 빌드 완료:', outDir);
