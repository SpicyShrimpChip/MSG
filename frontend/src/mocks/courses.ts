import type { Course, CourseInput } from '@/types';
import { ApiError } from '@/api/request';
import { mockDelay } from './delay';
import { persisted } from './storage';
import { removeMockMaterialsOfCourse } from './materials';

// 목업 전용 저장소. 새로고침해도 등록한 과목이 남도록 브라우저에 저장한다 (실제 데이터는 서버에 저장)
const STORAGE_KEY = 'msg-mock-courses';

// 처음 열었을 때 보여 줄 시연 과목 (mocks/analysis.ts·materials.ts의 courseId 1·2와 같다).
// 키워드는 대시보드·자료·커리큘럼·퀴즈 목업의 개념 이름과 같게 쓴다. 대시보드·퀴즈 결과의 "확인 퀴즈 만들기"가
// 개념 이름으로 퀴즈 만들기 화면을 열고, 그 화면은 과목 키워드에 있는 개념만 미리 골라 주기 때문이다.
// 한 번이라도 과목을 바꾸면 저장된 목록을 쓴다
const SAMPLE: Course[] = [
  {
    id: 1,
    name: '정보보호개론',
    semester: '2026-2',
    keywords: [
      '공개키 암호',
      '대칭키 암호',
      '해시 함수',
      '전자서명',
      '인증서(PKI)',
    ],
  },
  {
    id: 2,
    name: '운영체제',
    semester: '2026-2',
    keywords: ['프로세스', '스케줄링', '페이징'],
  },
];

// 처음 쓸 때 읽는다 (VITE_USE_MOCK=false면 앱 시작 때 저장소를 건드리지 않음)
const store = persisted<Course[]>(STORAGE_KEY, { initial: () => SAMPLE });
const getAll = () => store.get();
const setAll = (next: Course[]) => store.set(next);

export function mockGetCourses(): Promise<Course[]> {
  return mockDelay([...getAll()]);
}

export function mockCreateCourse(input: CourseInput): Promise<Course> {
  const courses = getAll();
  const course = { ...input, id: Math.max(0, ...courses.map((c) => c.id)) + 1 };
  setAll([...courses, course]);
  return mockDelay(course);
}

export function mockUpdateCourse(
  id: number,
  input: CourseInput,
): Promise<Course> {
  const courses = getAll();
  if (!courses.some((course) => course.id === id)) {
    return Promise.reject(new ApiError('과목을 찾을 수 없어요.', 404));
  }
  const updated = { ...input, id };
  setAll(courses.map((course) => (course.id === id ? updated : course)));
  return mockDelay(updated);
}

// 서버처럼 그 과목의 자료도 함께 지운다
export function mockDeleteCourse(id: number): Promise<void> {
  setAll(getAll().filter((course) => course.id !== id));
  removeMockMaterialsOfCourse(id);
  return mockDelay(undefined);
}
