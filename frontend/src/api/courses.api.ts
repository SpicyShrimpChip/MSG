import type { Course, CourseInput } from '@/types';
import {
  mockCreateCourse,
  mockDeleteCourse,
  mockGetCourses,
  mockUpdateCourse,
} from '@/mocks';
import { USE_MOCK } from './axios';
import { request } from './request';

// React Query에서 내 과목 목록을 담는 키. 과목을 추가·수정·삭제하면 이 키를 새로 고친다
export const COURSES_QUERY_KEY = ['courses'] as const;

// 확정: Swagger `과목` /api/courses
export function getCourses(): Promise<Course[]> {
  if (USE_MOCK) return mockGetCourses();
  return request<Course[]>({ method: 'GET', url: '/courses' });
}

export function createCourse(input: CourseInput): Promise<Course> {
  if (USE_MOCK) return mockCreateCourse(input);
  return request<Course>({ method: 'POST', url: '/courses', data: input });
}

export function updateCourse(id: number, input: CourseInput): Promise<Course> {
  if (USE_MOCK) return mockUpdateCourse(id, input);
  return request<Course>({
    method: 'PATCH',
    url: `/courses/${id}`,
    data: input,
  });
}

export function deleteCourse(id: number): Promise<void> {
  if (USE_MOCK) return mockDeleteCourse(id);
  return request<void>({ method: 'DELETE', url: `/courses/${id}` });
}
