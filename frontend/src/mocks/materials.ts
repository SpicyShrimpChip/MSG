import type {
  ConceptStudyNoteInput,
  Material,
  MaterialConcept,
  MaterialDetail,
  MaterialSummary,
  MaterialUploadInput,
} from '@/types';
import { ApiError } from '@/api/request';
import { mockDelay } from './delay';

// 목업 전용 저장소. 새로고침해도 올린 자료가 남도록 브라우저에 저장한다 (실제 데이터는 서버에 저장)
const STORAGE_KEY = 'ai-lms-mock-materials';

// 시연 과목(정보보호개론 1, 운영체제 2)에 맞춘 샘플. 요약은 직접 쓴 정리 노트를 가정한 예시이고
// 강의자료·시험 문제 원문은 넣지 않는다. conceptId는 mocks/quizzes.ts와 같은 값
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
        evidenceLocation: null,
        evidenceQuote: '받는 사람의 공개키로 잠그고',
        courseKeyword: false,
        studyNote: {
          explanation:
            '공개키 암호는 서로 짝을 이루는 두 개의 키를 쓰는 암호 방식이다. 공개키는 누구에게나 알려도 되고, 개인키는 주인만 가진다. 한 키로 잠근 것은 짝이 되는 다른 키로만 열린다. 그래서 누군가에게 비밀을 보내려면 그 사람의 공개키로 잠그면 되고, 열 수 있는 사람은 개인키를 가진 받는 사람뿐이다. 대칭키 암호처럼 미리 비밀 키를 몰래 주고받을 필요가 없다는 점이 가장 큰 차이다. 헷갈리기 쉬운 점은 "내 공개키"가 아니라 "받는 사람의 공개키"로 잠근다는 것이다.',
          materialContext:
            '이 노트는 공개키 암호를 먼저 설명한 뒤, 같은 키 쌍을 반대 방향으로 쓰는 전자서명과 공개키의 주인을 보증하는 인증서로 이어진다.',
          contextQuote: '받는 사람만 자기 개인키로 연다',
          contextLocation: null,
          importance:
            '전자서명과 인증서(PKI)는 모두 공개키 암호 위에서 동작한다. 키를 어느 쪽에 쓰는지 이해해야 뒤의 개념이 자연스럽게 연결된다.',
          example:
            '자물쇠(공개키)를 여러 개 만들어 나눠 주고 열쇠(개인키)는 나만 갖고 있다고 생각해 보자. 누구든 자물쇠로 상자를 잠가 보낼 수 있지만 열 수 있는 사람은 나뿐이다.',
          generatedBy: 'gpt',
          createdAt: '2026-10-21T09:00:00',
        },
      },
      {
        conceptId: 'digital-signature',
        name: '전자서명',
        aiDescription:
          '서명은 개인키, 검증은 공개키로 한다는 점이 암호화와 반대라고 짚었어요.',
        evidenceLocation: null,
        evidenceQuote: '보내는 사람이 개인키로 서명하고',
        courseKeyword: false,
        studyNote: {
          explanation:
            '전자서명은 이 글을 정말 그 사람이 썼고 중간에 바뀌지 않았는지를 확인하는 방법이다. 보내는 사람은 자기 개인키로 서명값을 만들고, 받는 사람은 보내는 사람의 공개키로 그 서명을 검증한다. 개인키는 주인만 갖고 있으므로 검증에 성공하면 그 사람이 서명했다고 볼 수 있다. 보통은 글 전체가 아니라 글의 해시값에 서명해서 빠르게 처리한다. 암호화와 키를 쓰는 방향이 반대라는 점이 핵심이다.',
          materialContext:
            '노트에서는 공개키 암호 다음에 "반대로"라는 말로 전자서명을 소개해, 두 개념의 키 사용 방향을 비교하도록 정리되어 있다.',
          contextQuote: '누구나 공개키로 검증한다',
          contextLocation: null,
          importance:
            '전자서명은 위조 방지(무결성)와 누가 보냈는지 확인(인증), 보낸 사실을 부인하지 못하게 하는 것(부인 방지)을 함께 제공한다. 인증서도 인증 기관의 전자서명으로 만들어진다.',
          example:
            '도장을 찍는 것과 비슷하다. 도장(개인키)은 나만 갖고 있고, 누구나 등록된 인감(공개키)과 비교해 진짜 내 도장인지 확인할 수 있다.',
          generatedBy: 'gpt',
          createdAt: '2026-10-21T09:00:00',
        },
      },
      {
        conceptId: 'pki',
        name: '인증서(PKI)',
        aiDescription:
          '공개키의 주인을 인증 기관이 보증한다는 흐름만 짧게 있어요.',
        evidenceLocation: null,
        evidenceQuote: '인증 기관이 발급한 인증서로 확인한다',
        courseKeyword: false,
        studyNote: null,
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
        evidenceLocation: null,
        evidenceQuote: '같은 비밀번호도 다른 값이 되도록 솔트를 붙인다',
        courseKeyword: false,
        studyNote: {
          explanation:
            '해시 함수는 어떤 길이의 입력이든 정해진 길이의 값(해시값)으로 바꾸는 함수다. 같은 입력은 언제나 같은 해시값이 나오지만, 해시값만 보고 원래 입력을 알아내기는 매우 어렵다(일방향성). 또 서로 다른 두 입력이 같은 해시값을 갖는 경우(충돌)를 찾기 어려워야 안전하다. 해시는 암호화가 아니므로 되돌려 복호화하는 과정이 없다는 점을 헷갈리지 않아야 한다.',
          materialContext:
            '이 풀이 노트는 해시의 성질을 비밀번호 저장 문제에 적용한다. 같은 비밀번호가 같은 해시값이 되는 약점을 솔트로 막는 흐름이다.',
          contextQuote: '값에서 원래 입력을 되돌리기 어렵다',
          contextLocation: null,
          importance:
            '비밀번호 저장, 파일이 바뀌었는지 확인하기, 전자서명 등 많은 보안 기능이 해시에 기대고 있다. 성질 하나가 깨지면 그 위의 기능도 함께 약해진다.',
          example:
            '두 사용자가 모두 "1234"를 비밀번호로 써도, 각자 다른 솔트를 붙여 해시하면 저장된 값이 달라져 한 사람의 값이 알려져도 다른 사람이 함께 드러나지 않는다.',
          generatedBy: 'claude',
          createdAt: '2026-10-19T09:00:00',
        },
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
        evidenceLocation: null,
        evidenceQuote: '페이지 테이블이 페이지 번호를 프레임 번호로 바꿔 준다',
        courseKeyword: false,
        studyNote: {
          explanation:
            '페이징은 실제 메모리를 같은 크기의 칸(프레임)으로 나누고, 프로그램의 주소 공간도 같은 크기의 조각(페이지)으로 나눠 빈 프레임 어디에나 넣는 메모리 관리 방식이다. 프로그램이 쓰는 주소는 페이지 번호와 페이지 안의 위치(오프셋)로 나뉘고, 페이지 테이블이 페이지 번호를 프레임 번호로 바꿔 실제 주소를 만든다. 프로그램을 연속된 공간에 넣지 않아도 되므로 빈 공간이 조각나는 외부 단편화가 생기지 않는다. 대신 마지막 페이지의 남는 공간(내부 단편화)은 생길 수 있다.',
          materialContext:
            '필기는 주소 변환(페이지 테이블)을 먼저 정리하고, 필요한 페이지가 메모리에 없을 때의 페이지 부재와 교체 알고리즘으로 이어진다.',
          contextQuote: '필요한 페이지가 메모리에 없으면 페이지 부재가 나고',
          contextLocation: null,
          importance:
            '가상 메모리와 페이지 교체 알고리즘(FIFO, LRU)은 모두 페이징을 바탕으로 한다. 주소 변환 과정을 이해해야 TLB나 다단계 페이지 테이블도 따라갈 수 있다.',
          example:
            '책(프로그램)을 같은 크기의 장(페이지)으로 뜯어 도서관 빈 칸(프레임) 아무 데나 꽂고, 목차(페이지 테이블)에 "3장은 17번 칸"처럼 적어 두는 것과 비슷하다.',
          generatedBy: 'gpt',
          createdAt: '2026-10-22T09:00:00',
        },
      },
    ],
  },
];

// 예전에 저장된 목업 자료에는 개념 설명(studyNote, 11/5 추가)이 없다. 같은 예시 자료면 예시 설명을 채우고, 나머지는 null로 맞춘다
function withStudyNotes(materials: MaterialDetail[]): MaterialDetail[] {
  return materials.map((material) => {
    const sample = SAMPLE.find((item) => item.id === material.id);
    return {
      ...material,
      concepts: (material.concepts ?? []).map((concept) => ({
        ...concept,
        studyNote:
          concept.studyNote ??
          sample?.concepts.find((item) => item.conceptId === concept.conceptId)
            ?.studyNote ??
          null,
      })),
    };
  });
}

function load(): MaterialDetail[] {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    return saved
      ? withStudyNotes(JSON.parse(saved) as MaterialDetail[])
      : SAMPLE;
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

export function mockGetMaterialConcepts(id: number) {
  return mockGetMaterial(id).then((material) => material.concepts);
}

// 검색·커리큘럼 목업이 읽는 지금 목업 자료 전체 (요약·개념 포함)
export function mockMaterialsSnapshot(): MaterialDetail[] {
  return getAll();
}

// 목업은 AI를 부르지 않는다. 예시 자료에는 미리 쓴 예시 설명이 있고, 없는 개념은 서버 기본값(본문 전송 꺼짐)처럼 거절한다
export function mockCreateConceptStudyNote(
  id: number,
  input: ConceptStudyNoteInput,
): Promise<MaterialConcept> {
  const concept = getAll()
    .find((item) => item.id === id)
    ?.concepts.find((item) => item.conceptId === input.conceptId);
  if (concept?.studyNote) return mockDelay(concept);
  return Promise.reject(
    new ApiError(
      '목업에서는 AI를 부르지 않아 개념 설명을 새로 만들지 않아요.',
      403,
    ),
  );
}

// 목업은 AI를 부르지 않는다. 가짜 개념을 만들지 않고 서버 기본값(본문 전송 꺼짐)처럼 거절한다
export function mockExtractMaterialConcepts(id: number): Promise<never> {
  void id;
  return Promise.reject(
    new ApiError(
      '목업에서는 AI를 부르지 않아 핵심 개념을 새로 뽑지 않아요.',
      403,
    ),
  );
}
