import { describe, expect, it } from 'vitest';
import {
  ASSESSMENTS_QUERY_KEY,
  CURRICULUM_QUERY_KEY,
  MATERIALS_QUERY_KEY,
  QUIZZES_QUERY_KEY,
  STUDY_QUERY_KEY,
  assessmentComparisonQuery,
  conceptStudiesQuery,
  curriculumQuery,
  materialAssessmentComparisonQuery,
  materialConceptsQuery,
  materialQuery,
  materialsQuery,
  quizAiStatusQuery,
  quizQuery,
  quizResultQuery,
} from '@/api';

// 무효화(invalidateQueries)는 접두 키로, 캐시 넣기(setQueryData)는 같은 키로 찾는다.
// 키 모양이 바뀌면 다시 부르지 않던 조회가 두 번 나가거나 무효화가 빠지므로 모양을 고정해 둔다
describe('queryOptions 키 모양', () => {
  it('화면이 쓰던 키와 같다', () => {
    expect(assessmentComparisonQuery(3).queryKey).toEqual([
      'assessments',
      'comparison',
      3,
    ]);
    expect(materialAssessmentComparisonQuery(7).queryKey).toEqual([
      'assessments',
      'comparison',
      'material',
      7,
    ]);
    expect(quizAiStatusQuery().queryKey).toEqual(['assessments', 'ai-status']);
    expect(curriculumQuery(3).queryKey).toEqual(['curriculum', 3]);
    expect(materialsQuery().queryKey).toEqual(['materials']);
    expect(materialQuery(7).queryKey).toEqual(['materials', 7]);
    expect(materialConceptsQuery(7).queryKey).toEqual([
      'materials',
      7,
      'concepts',
    ]);
    expect(conceptStudiesQuery(7).queryKey).toEqual(['concept-study', 7]);
    expect(quizQuery(5).queryKey).toEqual(['quizzes', 5]);
    expect(quizResultQuery(5).queryKey).toEqual(['quizzes', 5, 'result']);
  });

  it('무효화에 쓰는 접두 키로 시작한다', () => {
    const cases: [readonly unknown[], readonly unknown[]][] = [
      [ASSESSMENTS_QUERY_KEY, assessmentComparisonQuery(1).queryKey],
      [CURRICULUM_QUERY_KEY, curriculumQuery(1).queryKey],
      [MATERIALS_QUERY_KEY, materialConceptsQuery(1).queryKey],
      [STUDY_QUERY_KEY, conceptStudiesQuery(1).queryKey],
      [QUIZZES_QUERY_KEY, quizResultQuery(1).queryKey],
    ];
    for (const [prefix, key] of cases) {
      expect(key.slice(0, prefix.length)).toEqual([...prefix]);
    }
  });
});
