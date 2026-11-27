// PostToolUse 훅(경고만): 화면 코드(frontend/src)에 근거 없이 단정하는 표현이 새로 들어가면 Claude에게 알린다.
// 규칙 원문은 루트 CLAUDE.md "반드시 지킬 원칙"의 마지막 항목이다.
// "정확도가 아니에요"처럼 부정하는 문장은 허용한다. 테스트 파일은 검사하지 않는다.
import { readFileSync } from 'node:fs';
import path from 'node:path';

const PATTERNS = [
  /정확도\s*(를|가|이)?\s*(향상|개선|높|올리|올라|\d)/,
  /정확(한|하게)\s*진단/,
  /완벽(한|하게|히)?\s*(진단|분석|파악|예측)/,
  /100\s*%\s*(정확|진단|맞)/,
];

function addedText(toolInput) {
  if (typeof toolInput?.content === 'string') return toolInput.content;
  if (typeof toolInput?.new_string === 'string') return toolInput.new_string;
  if (Array.isArray(toolInput?.edits)) return toolInput.edits.map((edit) => edit.new_string ?? '').join('\n');
  return '';
}

try {
  const input = JSON.parse(readFileSync(0, 'utf8'));
  const filePath = input?.tool_input?.file_path ?? '';
  const normalized = filePath.split(path.sep).join('/');
  if (!/(^|\/)frontend\/src\/.+\.(tsx?|css)$/.test(normalized)) process.exit(0);
  if (/\.test\.tsx?$/.test(normalized)) process.exit(0);

  const hits = addedText(input.tool_input)
    .split('\n')
    .filter((line) => !/아니|않|말고/.test(line))
    .filter((line) => PATTERNS.some((pattern) => pattern.test(line)))
    .map((line) => line.trim().slice(0, 120));
  if (hits.length === 0) process.exit(0);

  process.stderr.write(
    '[금지 표현 경고] 근거 없는 "정확도 향상", "완벽한 진단" 같은 표현은 화면에 쓰지 않습니다(루트 CLAUDE.md "반드시 지킬 원칙"). ' +
      `의도한 문구가 아니면 고쳐 주세요:\n${hits.map((line) => `- ${line}`).join('\n')}\n`,
  );
  process.exit(2);
} catch {
  process.exit(0);
}
