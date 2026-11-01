#!/bin/bash
# 브랜치별 허용 목록(.claude/branch-allowlists/<브랜치 이름, /는 __>)이 있을 때만,
# 목록 밖의 레포 파일을 Claude가 고치지 못하게 막는다. 목록이 없는 브랜치는 그대로 둔다.
# 셸 명령으로 고치는 경우는 막지 못하므로 PR 전에 `git diff --name-only main`으로 한 번 더 확인한다.
input=$(cat)
file=$(jq -r '.tool_input.file_path // .tool_input.notebook_path // empty' <<<"$input" 2>/dev/null)
cwd=$(jq -r '.cwd // empty' <<<"$input" 2>/dev/null)
[ -z "$file" ] && exit 0
root=$(git -C "${cwd:-.}" rev-parse --show-toplevel 2>/dev/null) || exit 0
case "$file" in "$root"/*) ;; *) exit 0 ;; esac # 레포 밖 파일은 검사하지 않는다
branch=$(git -C "$root" branch --show-current)
[ -z "$branch" ] && exit 0
list="$root/.claude/branch-allowlists/${branch//\//__}"
[ -f "$list" ] || exit 0
rel="${file#"$root"/}"
grep -v '^#' "$list" | grep -qxF "$rel" && exit 0
echo "이 브랜치($branch)에서는 .claude/branch-allowlists/${branch//\//__} 에 적힌 파일만 고칠 수 있어요: $rel" >&2
exit 2
