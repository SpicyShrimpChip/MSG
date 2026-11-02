import type { Course, CourseInput } from '@/types';
import { ApiError } from '@/api/request';
import { mockDelay } from './delay';
import { removeMockMaterialsOfCourse } from './materials';

// 목업 전용 저장소. 새로고침해도 등록한 과목이 남도록 브라우저에 저장한다 (실제 데이터는 서버에 저장)
const STORAGE_KEY = 'ai-lms-mock-courses';

// 처음 열었을 때 보여 줄 시연 과목 (mocks/analysis.ts·materials.ts의 courseId 1·2와 같다).
// 한 번이라도 과목을 바꾸면 저장된 목록을 쓴다
const SAMPLE: Course[] = [
  {
    id: 1,
    name: '정보보호개론',
    semester: '2026-2',
    keywords: ['암호', '공개키', '해시', '전자서명', '인증서'],
  },
  {
    id: 2,
    name: '운영체제',
    semester: '2026-2',
    keywords: ['프로세스', '스케줄링', '페이징'],
  },
];

function load(): Course[] {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    return saved ? (JSON.parse(saved) as Course[]) : SAMPLE;
  } catch {
    return SAMPLE;
  }
}

function save(courses: Course[]) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(courses));
  } catch {
    // 저장소를 쓸 수 없으면(사생활 보호 모드 등) 이번 화면에서만 유지한다
  }
}

// 처음 쓸 때 읽는다 (VITE_USE_MOCK=false면 앱 시작 때 저장소를 건드리지 않음)
let cache: Course[] | null = null;
const getAll = () => (cache ??= load());
const setAll = (next: Course[]) => {
  cache = next;
  save(next);
};

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
