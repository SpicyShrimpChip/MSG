import type {
  Material,
  MaterialDetail,
  MaterialSummary,
  MaterialUploadInput,
} from '@/types';
import {
  mockDeleteMaterial,
  mockGetMaterial,
  mockGetMaterials,
  mockUploadMaterial,
} from '@/mocks';
import { USE_MOCK } from './axios';
import { request } from './request';

// React Query에서 내 학습자료를 담는 키. 상세는 [...MATERIALS_QUERY_KEY, id]
export const MATERIALS_QUERY_KEY = ['materials'] as const;

// 20MB 파일을 느린 망에서 올려도 끊기지 않게 기본 5초보다 길게 기다린다
const UPLOAD_TIMEOUT_MS = 120_000;

// 확정: Swagger `학습자료`
export function uploadMaterial(input: MaterialUploadInput): Promise<Material> {
  if (USE_MOCK) return mockUploadMaterial(input);
  const body = new FormData();
  body.append('courseId', String(input.courseId));
  body.append('title', input.title);
  body.append('file', input.file);
  return request<Material>({
    method: 'POST',
    url: '/materials',
    data: body,
    // 기본 헤더(application/json)가 남아 있으면 axios가 FormData를 JSON 글자로 바꿔 파일이 빠진다.
    // multipart로 지정하면 브라우저가 boundary를 붙인 헤더로 다시 만든다
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: UPLOAD_TIMEOUT_MS,
  });
}

export function getMaterials(): Promise<MaterialSummary[]> {
  if (USE_MOCK) return mockGetMaterials();
  return request<MaterialSummary[]>({ method: 'GET', url: '/materials' });
}

export function getMaterial(id: number): Promise<MaterialDetail> {
  if (USE_MOCK) return mockGetMaterial(id);
  return request<MaterialDetail>({ method: 'GET', url: `/materials/${id}` });
}

export function deleteMaterial(id: number): Promise<void> {
  if (USE_MOCK) return mockDeleteMaterial(id);
  return request<void>({ method: 'DELETE', url: `/materials/${id}` });
}
