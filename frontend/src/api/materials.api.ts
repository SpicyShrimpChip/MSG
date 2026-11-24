import { queryOptions } from '@tanstack/react-query';
import type {
  ConceptExtractInput,
  ConceptStudyNoteInput,
  Material,
  MaterialConcept,
  MaterialDetail,
  MaterialSummary,
  MaterialUploadInput,
  StudySummary,
  StudySummaryInput,
  StudySummaryPurpose,
} from '@/types';
import {
  mockCreateConceptStudyNote,
  mockDeleteMaterial,
  mockExtractMaterialConcepts,
  mockGetMaterial,
  mockGetMaterialConcepts,
  mockGenerateStudySummary,
  mockGetMaterials,
  mockGetStudySummary,
  mockUploadMaterial,
} from '@/mocks';
// 스토어 배럴(화면 모드 등 window 사용)까지 불러오지 않도록 파일을 직접 가져온다
import { isMockData } from '@/store/useDemoStore';
import { AI_REQUEST_TIMEOUT_MS, request } from './request';

// React Query에서 내 학습자료를 담는 키. 목록은 materialsQuery, 상세는 materialQuery, 핵심 개념은 materialConceptsQuery,
// 요약본은 [...MATERIALS_QUERY_KEY, id, 'summary', purpose](StudySummaryPanel)
export const MATERIALS_QUERY_KEY = ['materials'] as const;

// 내 자료 목록
export const materialsQuery = () =>
  queryOptions({ queryKey: MATERIALS_QUERY_KEY, queryFn: getMaterials });

// 자료 하나의 상세
export const materialQuery = (id: number) =>
  queryOptions({
    queryKey: [...MATERIALS_QUERY_KEY, id],
    queryFn: () => getMaterial(id),
  });

// 자료의 핵심 개념 목록
export const materialConceptsQuery = (id: number) =>
  queryOptions({
    queryKey: [...MATERIALS_QUERY_KEY, id, 'concepts'],
    queryFn: () => getMaterialConcepts(id),
  });

// 20MB 파일을 느린 망에서 올려도 끊기지 않게 기본 5초보다 길게 기다린다
const UPLOAD_TIMEOUT_MS = 120_000;

// 확정: Swagger `학습자료`
export function uploadMaterial(input: MaterialUploadInput): Promise<Material> {
  if (isMockData()) return mockUploadMaterial(input);
  const body = new FormData();
  body.append('courseId', String(input.courseId));
  body.append('title', input.title);
  body.append('requestKey', input.requestKey);
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
  if (isMockData()) return mockGetMaterials();
  return request<MaterialSummary[]>({ method: 'GET', url: '/materials' });
}

export function getMaterial(id: number): Promise<MaterialDetail> {
  if (isMockData()) return mockGetMaterial(id);
  return request<MaterialDetail>({ method: 'GET', url: `/materials/${id}` });
}

export function deleteMaterial(id: number): Promise<void> {
  if (isMockData()) return mockDeleteMaterial(id);
  return request<void>({ method: 'DELETE', url: `/materials/${id}` });
}

// 자료에서 뽑아 근거를 확인한 핵심 개념 (안 뽑았으면 빈 목록)
export function getMaterialConcepts(id: number): Promise<MaterialConcept[]> {
  if (isMockData()) return mockGetMaterialConcepts(id);
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
  if (isMockData()) return mockExtractMaterialConcepts(id);
  return request<MaterialConcept[]>({
    method: 'POST',
    url: `/materials/${id}/concepts`,
    data: input,
    timeout: AI_REQUEST_TIMEOUT_MS,
  });
}

// 개념 하나의 공부용 설명 만들기: 자료 본문 발췌를 외부 AI로 보낸다(조건은 개념 뽑기와 같다). 개념 이름·근거는 바뀌지 않는다
export function createConceptStudyNote(
  id: number,
  input: ConceptStudyNoteInput,
): Promise<MaterialConcept> {
  if (isMockData()) return mockCreateConceptStudyNote(id, input);
  return request<MaterialConcept>({
    method: 'POST',
    url: `/materials/${id}/concepts/study-notes`,
    data: input,
    timeout: AI_REQUEST_TIMEOUT_MS,
  });
}

// 학습 목적별 저장한 요약본 (없으면 status none). 모델을 부르지 않는다
export function getStudySummary(
  id: number,
  purpose: StudySummaryPurpose,
): Promise<StudySummary> {
  if (isMockData()) return mockGetStudySummary(id, purpose);
  return request<StudySummary>({
    method: 'GET',
    url: `/materials/${id}/summary`,
    params: { purpose },
  });
}

// 요약본 만들기: 자료 본문(서버가 고른 부분)을 외부 AI로 보낸다(운영 설정이 꺼져 있으면 403).
// 같은 파일·같은 목적의 요약이 있으면 모델을 부르지 않고 저장한 요약을 돌려준다(reused)
export function generateStudySummary(
  id: number,
  input: StudySummaryInput,
): Promise<StudySummary> {
  if (isMockData()) return mockGenerateStudySummary(id, input);
  return request<StudySummary>({
    method: 'POST',
    url: `/materials/${id}/summary`,
    data: input,
    timeout: AI_REQUEST_TIMEOUT_MS,
  });
}
