import type { SearchResult } from '@/types';
import { mockSearch } from '@/mocks';
import { USE_MOCK } from './axios';
import { request } from './request';

export const SEARCH_QUERY_KEY = ['search'] as const;

// 통합 검색 (Swagger `검색`). 내 과목·자료·자료 핵심 개념과 공지 제목에서 찾는다(대소문자·띄어쓰기 무시, 자료 본문은 찾지 않음)
export function search(query: string): Promise<SearchResult> {
  if (USE_MOCK) return mockSearch(query);
  return request<SearchResult>({
    method: 'GET',
    url: '/search',
    params: { q: query },
  });
}
