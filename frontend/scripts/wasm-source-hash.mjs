// 러스트 크레이트(wasm/conversation-core) 소스의 해시. 빌드한 WASM이 지금 소스로 만든 것인지 확인하는 데 쓴다.
// 줄바꿈(CRLF/LF) 차이로 해시가 달라지지 않게 LF로 맞춘 뒤 센다
import { createHash } from 'node:crypto';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

export const CRATE_DIR = new URL('../wasm/conversation-core/', import.meta.url);
export const OUT_DIR = new URL(
  '../src/wasm/conversation-core/',
  import.meta.url,
);

function listFiles(dir) {
  return readdirSync(dir)
    .flatMap((name) => {
      const path = join(dir, name);
      return statSync(path).isDirectory() ? listFiles(path) : [path];
    })
    .sort();
}

export function sourceHash() {
  const root = fileURLToPath(CRATE_DIR);
  const files = [
    join(root, 'Cargo.toml'),
    join(root, 'Cargo.lock'),
    join(root, '.cargo', 'config.toml'),
    ...listFiles(join(root, 'src')),
  ];
  const hash = createHash('sha256');
  for (const file of files) {
    hash.update(relative(root, file).split(sep).join('/'));
    hash.update('\0');
    hash.update(readFileSync(file, 'utf8').replace(/\r\n/g, '\n'));
    hash.update('\0');
  }
  return hash.digest('hex');
}
