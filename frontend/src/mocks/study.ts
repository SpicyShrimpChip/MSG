import type { CheckAttempt, ConceptStudy, MaterialDetail } from '@/types';
import { ApiError } from '@/api/request';
import { judgeCheck, resolveStudyStatus } from '@/utils/conceptStudy';
import { mockDelay } from './delay';
import { mockMaterialsSnapshot } from './materials';

// 체험 모드의 개념 학습 기록. 서버로 보내지 않고 이 브라우저(localStorage)에만 둔다 (실제 기록은 서버 DB)
const STORAGE_KEY = 'ai-lms-mock-study';

interface MockRecord {
  firstCompletedAt: string;
  lastStudiedAt: string;
  studyCount: number;
}

const recordKey = (materialId: number, conceptId: string) =>
  `${materialId}:${conceptId}`;

// 예시 기록: 공개키 암호는 공부하고 확인 퀴즈를 틀려 복습이 필요한 상태, 해시 함수는 이해 확인까지 끝난 상태
const SAMPLE_RECORDS: Record<string, MockRecord> = {
  [recordKey(1, 'public-key')]: {
    firstCompletedAt: '2026-11-01T20:00:00',
    lastStudiedAt: '2026-11-01T20:00:00',
    studyCount: 1,
  },
  [recordKey(2, 'hash')]: {
    firstCompletedAt: '2026-11-01T21:00:00',
    lastStudiedAt: '2026-11-01T21:00:00',
    studyCount: 1,
  },
};

const sampleAttempt = (
  quizId: number,
  submittedAt: string,
  gradedCount: number,
  score: number,
  maxScore: number,
): CheckAttempt => {
  const base = { gradedCount, score, maxScore, waitingCount: 0 };
  return {
    quizId,
    submittedAt,
    rate: Math.round((score / maxScore) * 100),
    result: judgeCheck(base),
    ...base,
  };
};

// 예시 확인 퀴즈 결과 (목업 퀴즈를 풀어도 여기는 바뀌지 않는다)
const SAMPLE_CHECKS: Record<string, CheckAttempt[]> = {
  [recordKey(1, 'public-key')]: [
    sampleAttempt(1, '2026-11-02T09:00:00', 3, 1, 3),
  ],
  [recordKey(2, 'hash')]: [sampleAttempt(2, '2026-11-02T10:00:00', 2, 2, 2)],
};

function load(): Record<string, MockRecord> {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    return saved
      ? {
          ...SAMPLE_RECORDS,
          ...(JSON.parse(saved) as Record<string, MockRecord>),
        }
      : { ...SAMPLE_RECORDS };
  } catch {
    return { ...SAMPLE_RECORDS };
  }
}

let cache: Record<string, MockRecord> | null = null;
const getAll = () => (cache ??= load());
function setRecord(key: string, record: MockRecord) {
  cache = { ...getAll(), [key]: record };
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(cache));
  } catch {
    // 저장소를 쓸 수 없으면 이번 화면에서만 유지한다
  }
}

function reason(study: Omit<ConceptStudy, 'reason'>): string {
  const latest = study.latestCheck;
  switch (study.status) {
    case 'not_studied':
      return '예시 데이터예요. 아직 이 개념의 학습 완료 기록이 없어요.';
    case 'studied':
      return latest
        ? `예시 데이터예요. 다시 공부했어요(학습 ${study.studyCount}번째). 확인 퀴즈를 다시 풀어 보세요.`
        : '예시 데이터예요. 학습을 완료했어요. 확인 퀴즈로 이해했는지 확인해 보세요.';
    case 'understood':
      return `예시 데이터예요. 확인 퀴즈 ${latest?.score}/${latest?.maxScore}점(${latest?.rate}%)으로 기준(60%) 이상이에요.`;
    case 'review_needed':
      return `예시 데이터예요. 확인 퀴즈 ${latest?.score}/${latest?.maxScore}점(${latest?.rate}%)으로 기준(60%)보다 낮아요. 틀린 문제를 확인하고 다시 공부해 보세요.`;
    default:
      return '예시 데이터예요. 채점을 기다리는 답이 있어 아직 판단하지 않았어요.';
  }
}

// 개념 하나의 예시 학습 상태 (서버와 같은 규칙)
export function mockStudyOf(
  material: Pick<MaterialDetail, 'id' | 'courseId'>,
  conceptId: string,
  name: string,
): ConceptStudy {
  const key = recordKey(material.id, conceptId);
  const record = getAll()[key] ?? null;
  const history = SAMPLE_CHECKS[key] ?? [];
  const latestCheck = history[0] ?? null;
  const base = {
    materialId: material.id,
    courseId: material.courseId,
    conceptId,
    name,
    status: resolveStudyStatus(record?.lastStudiedAt ?? null, latestCheck),
    studied: record !== null,
    firstCompletedAt: record?.firstCompletedAt ?? null,
    lastStudiedAt: record?.lastStudiedAt ?? null,
    studyCount: record?.studyCount ?? 0,
    latestCheck,
    checkHistory: history,
    openCheckQuizId: null,
  };
  return { ...base, reason: reason(base) };
}

export function mockGetConceptStudies(
  materialId: number,
): Promise<ConceptStudy[]> {
  const material = mockMaterialsSnapshot().find(
    (item) => item.id === materialId,
  );
  if (!material) {
    return Promise.reject(new ApiError('자료를 찾을 수 없어요.', 404));
  }
  return mockDelay(
    material.concepts.map((concept) =>
      mockStudyOf(material, concept.conceptId, concept.name),
    ),
  );
}

// 서버와 같은 규칙: 처음이면 기록을 만들고, 복습 필요면 다시 공부한 것으로 시각·횟수를 바꾸고, 그 밖에는 그대로 둔다
export function mockCompleteConceptStudy(
  materialId: number,
  conceptId: string,
): Promise<ConceptStudy> {
  const material = mockMaterialsSnapshot().find(
    (item) => item.id === materialId,
  );
  const concept = material?.concepts.find(
    (item) => item.conceptId === conceptId,
  );
  if (!material || !concept) {
    return Promise.reject(
      new ApiError('이 자료에서 그 개념을 찾을 수 없어요.', 404),
    );
  }
  const key = recordKey(materialId, conceptId);
  const now = new Date().toISOString().slice(0, 19);
  const current = mockStudyOf(material, conceptId, concept.name);
  const record = getAll()[key];
  if (!record) {
    setRecord(key, {
      firstCompletedAt: now,
      lastStudiedAt: now,
      studyCount: 1,
    });
  } else if (current.status === 'review_needed') {
    setRecord(key, {
      ...record,
      lastStudiedAt: now,
      studyCount: record.studyCount + 1,
    });
  }
  return mockDelay(mockStudyOf(material, conceptId, concept.name));
}
