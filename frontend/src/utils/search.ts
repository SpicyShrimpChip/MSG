// 검색 비교 규칙 (백엔드 SearchService.squash와 같다): 대소문자와 띄어쓰기를 무시하고 글자가 들어 있으면 맞은 것으로 본다.
// "정보 보호"로도 "정보보호개론"을 찾는다
export function squashForSearch(text: string | null | undefined): string {
  return (text ?? '').toLowerCase().replace(/\s+/g, '');
}

export function matchesQuery(
  text: string | null | undefined,
  query: string,
): boolean {
  const needle = squashForSearch(query);
  return needle.length > 0 && squashForSearch(text).includes(needle);
}

// 검색어 길이 상한 (백엔드 SearchService.MAX_QUERY_LENGTH와 같다)
export const SEARCH_MAX_LENGTH = 50;
