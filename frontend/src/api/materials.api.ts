import type {
  ConceptExtractInput,
  ConceptStudyNoteInput,
  Material,
  MaterialConcept,
  MaterialDetail,
  MaterialSummary,
  MaterialUploadInput,
} from '@/types';
import {
  mockCreateConceptStudyNote,
  mockDeleteMaterial,
  mockExtractMaterialConcepts,
  mockGetMaterial,
  mockGetMaterialConcepts,
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

// 자료에서 뽑아 근거를 확인한 핵심 개념 (안 뽑았으면 빈 목록)
export function getMaterialConcepts(id: number): Promise<MaterialConcept[]> {
  if (USE_MOCK) return mockGetMaterialConcepts(id);
  return request<MaterialConcept[]>({
    method: 'GET',
    url: `/materials/${id}/concepts`,
  });
}

// 핵심 개념 뽑기: 자료 본문 발췌를 외부 AI로 보낸다(운영 설정이 꺼져 있으면 403). 서버가 근거를 본문에서 확인한 개념만 저장한다
export function extractMaterialConcepts(
  id: number,
  input: ConceptExtractInput,
): Promise<MaterialConcept[]> {
  if (USE_MOCK) return mockExtractMaterialConcepts(id);
  return request<MaterialConcept[]>({
    method: 'POST',
    url: `/materials/${id}/concepts`,
    data: input,
    timeout: 180_000,
  });
}

// 개념 하나의 공부용 설명 만들기: 자료 본문 발췌를 외부 AI로 보낸다(조건은 개념 뽑기와 같다). 개념 이름·근거는 바뀌지 않는다
export function createConceptStudyNote(
  id: number,
  input: ConceptStudyNoteInput,
): Promise<MaterialConcept> {
  if (USE_MOCK) return mockCreateConceptStudyNote(id, input);
  return request<MaterialConcept>({
    method: 'POST',
    url: `/materials/${id}/concepts/study-notes`,
    data: input,
    timeout: 180_000,
  });
}
