// PreToolUse 훅: origin/main에 이미 있는 Flyway 마이그레이션(V*.sql)을 고치려 하면 막는다.
// 근거: docs/wiki/backend.md "머지된 마이그레이션은 고치지 않고 새 버전을 추가한다"
// 판단할 수 없으면(git 없음, origin/main 없음 등) 막지 않고 통과시킨다.
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';

const MIGRATION_PATTERN = /^backend\/src\/main\/resources\/db\/(migration|vector|seed)\/V[^/]*\.sql$/;

function git(cwd, args) {
  return execFileSync('git', ['-C', cwd, ...args], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim();
}

function nearestExistingDir(filePath) {
  let dir = path.dirname(filePath);
  while (!existsSync(dir) && path.dirname(dir) !== dir) dir = path.dirname(dir);
  return dir;
}

try {
  const input = JSON.parse(readFileSync(0, 'utf8'));
  const filePath = input?.tool_input?.file_path;
  if (!filePath || !filePath.endsWith('.sql')) process.exit(0);

  const absolute = path.resolve(input.cwd ?? process.cwd(), filePath);
  const root = git(nearestExistingDir(absolute), ['rev-parse', '--show-toplevel']);
  const relative = path.relative(root, absolute).split(path.sep).join('/');
  if (!MIGRATION_PATTERN.test(relative)) process.exit(0);

  // origin/main에 없으면 git이 실패해 catch로 가고 통과한다
  git(root, ['cat-file', '-e', `origin/main:${relative}`]);

  process.stderr.write(
    `${relative}은(는) 이미 main에 머지된 마이그레이션이라 고치면 안 됩니다. ` +
      '바꿀 내용은 다음 번호의 새 V 파일로 추가하세요(docs/wiki/backend.md "머지된 마이그레이션은 고치지 않고 새 버전을 추가한다").\n',
  );
  process.exit(2);
} catch {
  process.exit(0);
}
