import { describe, expect, it } from 'vitest';
import {
  mockCreateQuiz,
  mockGetAssessmentComparison,
  mockGetLatestQuizResult,
  mockGetQuizzes,
  mockSubmitQuiz,
  nextCreatedQuizId,
} from './quizzes';
import { mockStartAnalysis, NO_EXAMPLE_ANALYSIS_MESSAGE } from './analysis';

const createInput = {
  requestKey: 'test',
  courseId: 1,
  mode: 'exam_prep' as const,
  questionTypes: ['multiple_choice' as const],
  questionCount: 1,
  difficulty: 'medium' as const,
};

describe('체험 모드에서 만든 퀴즈', () => {
  it('새 퀴즈 id는 지금 있는 가장 큰 id 다음이고 100부터 시작한다', () => {
    expect(nextCreatedQuizId([{ id: 1 }, { id: 2 }])).toBe(100);
    expect(nextCreatedQuizId([{ id: 3 }, { id: 104 }, { id: 101 }])).toBe(105);
  });

  it('만든 퀴즈는 목록 맨 앞에 보이고, 다음에 만든 퀴즈는 앞 퀴즈의 결과를 이어받지 않는다', async () => {
    const first = await mockCreateQuiz(createInput);
    expect((await mockGetQuizzes())[0].id).toBe(first.id);

    await mockSubmitQuiz(first.id, [
      { questionId: first.questions[0].id, choiceIndex: 2 },
    ]);
    expect(await mockGetLatestQuizResult(first.id)).not.toBeNull();

    const second = await mockCreateQuiz(createInput);
    expect(second.id).toBeGreaterThan(first.id);
    expect(await mockGetLatestQuizResult(second.id)).toBeNull();
    const ids = (await mockGetQuizzes()).map((quiz) => quiz.id);
    expect(ids.slice(0, 2)).toEqual([second.id, first.id]);
  });
});

describe('체험 모드 분석', () => {
  it('예시가 없는 과목은 정보보호개론 개념을 보여 주지 않고 이유를 알린다', async () => {
    await expect(mockStartAnalysis(2)).rejects.toThrow(
      NO_EXAMPLE_ANALYSIS_MESSAGE,
    );
  });

  it('예시 과목은 다시 분석하면 예시 결과를 돌려준다', async () => {
    const run = await mockStartAnalysis(1);
    expect(run.courseId).toBe(1);
    expect(run.concepts.length).toBeGreaterThan(0);
  });
});

describe('체험 모드 사전·사후평가 비교', () => {
  it('예시가 없는 과목은 정보보호개론 비교를 보여 주지 않는다', async () => {
    const other = await mockGetAssessmentComparison({ courseId: 2 });
    expect(other.pre).toBeNull();
    expect(other.concepts).toEqual([]);

    const example = await mockGetAssessmentComparison({ courseId: 1 });
    expect(example.concepts.length).toBeGreaterThan(0);
  });

  it('커리큘럼 자료는 사후평가 전이고, 과목 비교는 다른 자료(해시 함수 연습문제 풀이)의 평가다', async () => {
    // 커리큘럼 STEP 4 "아직 사후 평가를 보지 않았어요"와 대시보드 비교가 어긋나지 않게
    const curriculumMaterial = await mockGetAssessmentComparison({
      materialId: 1,
    });
    expect(curriculumMaterial.pre?.score).toBe(7);
    expect(curriculumMaterial.pre?.maxScore).toBe(12);
    expect(curriculumMaterial.post).toBeNull();

    const course = await mockGetAssessmentComparison({ courseId: 1 });
    expect(course.material).toEqual({
      id: 2,
      title: '해시 함수 연습문제 풀이',
    });
    expect(course.post).not.toBeNull();
    expect(await mockGetAssessmentComparison({ materialId: 2 })).toEqual(
      course,
    );
  });
});
