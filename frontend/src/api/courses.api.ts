import type { Course, CourseInput } from '@/types';
import {
  mockCreateCourse,
  mockDeleteCourse,
  mockGetCourses,
  mockUpdateCourse,
} from '@/mocks';
// 스토어 배럴(화면 모드 등 window 사용)까지 불러오지 않도록 파일을 직접 가져온다
import { isMockData } from '@/store/useDemoStore';
import { request } from './request';

// React Query에서 내 과목 목록을 담는 키. 과목을 추가·수정·삭제하면 이 키를 새로 고친다
export const COURSES_QUERY_KEY = ['courses'] as const;

// 확정: Swagger `과목` /api/courses
export function getCourses(): Promise<Course[]> {
  if (isMockData()) return mockGetCourses();
  return request<Course[]>({ method: 'GET', url: '/courses' });
}

export function createCourse(input: CourseInput): Promise<Course> {
  if (isMockData()) return mockCreateCourse(input);
  return request<Course>({ method: 'POST', url: '/courses', data: input });
}

export function updateCourse(id: number, input: CourseInput): Promise<Course> {
  if (isMockData()) return mockUpdateCourse(id, input);
  return request<Course>({
    method: 'PATCH',
    url: `/courses/${id}`,
    data: input,
  });
}

export function deleteCourse(id: number): Promise<void> {
  if (isMockData()) return mockDeleteCourse(id);
  return request<void>({ method: 'DELETE', url: `/courses/${id}` });
}
