import type {
  Material,
  MaterialDetail,
  MaterialSummary,
  MaterialUploadInput,
} from '@/types';
import { ApiError } from '@/api/request';
import { mockDelay } from './delay';

// 목업 전용 저장소. 새로고침해도 올린 자료가 남도록 브라우저에 저장한다 (실제 데이터는 서버에 저장)
const STORAGE_KEY = 'ai-lms-mock-materials';

// 시연 과목(정보보호개론 1, 운영체제 2)에 맞춘 샘플. 요약은 직접 쓴 정리 노트를 가정한 예시이고
// 강의자료·시험 문제 원문은 넣지 않는다. conceptId는 mocks/dashboard.ts와 같은 값
const SAMPLE: MaterialDetail[] = [
  {
    id: 1,
    courseId: 1,
    title: '공개키 암호 정리 노트',
    fileName: '공개키_정리노트.md',
    createdAt: '2026-10-20T21:10:00',
    status: 'ready',
    quizCount: 1,
    aiSummary:
      '공개키 암호는 누구나 아는 공개키와 나만 가진 개인키를 한 쌍으로 쓴다. 암호화할 때는 받는 사람의 공개키로 잠그고, 받는 사람만 자기 개인키로 연다. 전자서명은 반대로 보내는 사람이 개인키로 서명하고, 누구나 공개키로 검증한다. 공개키가 정말 그 사람 것인지는 인증 기관이 발급한 인증서로 확인한다.',
    concepts: [
      {
        conceptId: 'public-key',
        name: '공개키 암호',
        aiDescription:
          '암호화할 때 어느 키를 쓰는지(받는 사람의 공개키)를 중심으로 정리했어요.',
      },
      {
        conceptId: 'digital-signature',
        name: '전자서명',
        aiDescription:
          '서명은 개인키, 검증은 공개키로 한다는 점이 암호화와 반대라고 짚었어요.',
      },
      {
        conceptId: 'pki',
        name: '인증서(PKI)',
        aiDescription:
          '공개키의 주인을 인증 기관이 보증한다는 흐름만 짧게 있어요.',
      },
    ],
  },
  {
    id: 2,
    courseId: 1,
    title: '해시 함수 연습문제 풀이',
    fileName: '해시_연습문제_풀이.pdf',
    createdAt: '2026-10-18T19:40:00',
    status: 'ready',
    quizCount: 1,
    aiSummary:
      '해시 함수는 길이와 상관없이 정해진 길이의 값을 만든다. 같은 입력은 항상 같은 값이 나오고, 값에서 원래 입력을 되돌리기 어렵다. 비밀번호는 해시로 저장하되, 같은 비밀번호도 다른 값이 되도록 솔트를 붙인다. 서로 다른 입력이 같은 값이 되는 충돌을 찾기 어려워야 안전하다.',
    concepts: [
      {
        conceptId: 'hash',
        name: '해시 함수',
        aiDescription:
          '비밀번호 저장과 솔트를 예로 들어 성질(일방향성, 충돌 저항성)을 풀었어요.',
      },
    ],
  },
  {
    id: 3,
    courseId: 2,
    title: '페이징 수업 필기',
    fileName: '페이징_필기.txt',
    createdAt: '2026-10-21T10:05:00',
    status: 'ready',
    quizCount: 1,
    aiSummary:
      '페이징은 메모리를 같은 크기의 칸(프레임)으로 나누고, 프로그램도 같은 크기의 페이지로 나눠 아무 칸에나 넣는다. 페이지 테이블이 페이지 번호를 프레임 번호로 바꿔 준다. 필요한 페이지가 메모리에 없으면 페이지 부재가 나고, 교체 알고리즘(FIFO, LRU 등)이 어떤 페이지를 내보낼지 정한다.',
    concepts: [
      {
        conceptId: 'paging',
        name: '페이징',
        aiDescription:
          '주소 변환과 교체 알고리즘을 이어서 정리했어요. 알고리즘끼리 비교는 아직 없어요.',
      },
    ],
  },
];

function load(): MaterialDetail[] {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    return saved ? (JSON.parse(saved) as MaterialDetail[]) : SAMPLE;
  } catch {
    return SAMPLE;
  }
}

function save(materials: MaterialDetail[]) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(materials));
  } catch {
    // 저장소를 쓸 수 없으면(사생활 보호 모드 등) 이번 화면에서만 유지한다
  }
}

// 처음 쓸 때 읽는다 (VITE_USE_MOCK=false면 앱 시작 때 저장소를 건드리지 않음)
let cache: MaterialDetail[] | null = null;
const getAll = () => (cache ??= load());
const setAll = (next: MaterialDetail[]) => {
  cache = next;
  save(next);
};

// 목록에는 요약·개념 본문을 싣지 않는다
const toSummary = (material: MaterialDetail): MaterialSummary => ({
  id: material.id,
  courseId: material.courseId,
  title: material.title,
  fileName: material.fileName,
  createdAt: material.createdAt,
  status: material.status,
  quizCount: material.quizCount,
});

// 최신 자료가 위로
export function mockGetMaterials(): Promise<MaterialSummary[]> {
  const list = [...getAll()]
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    .map(toSummary);
  return mockDelay(list);
}

export function mockGetMaterial(id: number): Promise<MaterialDetail> {
  const material = getAll().find((item) => item.id === id);
  if (!material) {
    return Promise.reject(new ApiError('자료를 찾을 수 없어요.', 404));
  }
  return mockDelay(material);
}

// 목업은 파일 내용을 읽지 않는다. 서버처럼 올린 자료는 AI 분석 전(uploaded) 상태로 남는다
export function mockUploadMaterial(
  input: MaterialUploadInput,
): Promise<Material> {
  const materials = getAll();
  const material: MaterialDetail = {
    id: Math.max(0, ...materials.map((item) => item.id)) + 1,
    courseId: input.courseId,
    title: input.title,
    fileName: input.file.name,
    createdAt: new Date().toISOString(),
    status: 'uploaded',
    quizCount: 0,
    aiSummary: null,
    concepts: [],
  };
  setAll([...materials, material]);
  return mockDelay(
    { id: material.id, courseId: material.courseId, title: material.title },
    800,
  );
}

// 과목을 지우면 그 과목의 자료도 함께 지운다 (서버와 같은 규칙, mockDeleteCourse가 부른다)
export function removeMockMaterialsOfCourse(courseId: number) {
  setAll(getAll().filter((item) => item.courseId !== courseId));
}

export function mockDeleteMaterial(id: number): Promise<void> {
  setAll(getAll().filter((item) => item.id !== id));
  return mockDelay(undefined);
}
