// 퀴즈 목록 한 줄 (GET /api/quizzes)
export interface QuizSummary {
  id: number;
  title: string;
  courseId: number;
  // 이 퀴즈를 만든 자료 (자료 상세의 퀴즈 탭에서 묶는다)
  materialId: number | null;
  questionCount: number;
  // 가장 최근 제출 기준. 아직 안 풀었으면 null
  lastCorrectCount: number | null;
}

export interface QuizQuestion {
  id: number;
  text: string;
  choices: string[];
}

// 풀기 화면용 퀴즈 (GET /api/quizzes/{id}). 정답은 들어 있지 않다
export interface Quiz {
  id: number;
  title: string;
  courseId: number;
  materialId: number | null;
  questions: QuizQuestion[];
}

export interface QuizConcept {
  conceptId: string;
  name: string;
}

export interface QuizAnswer {
  questionId: number;
  choiceIndex: number;
}

// 채점 결과 (POST /api/quizzes/{id}/submissions, GET .../submissions/latest).
// 정오는 서버 코드가 채점하고, aiExplanation(해설)만 AI가 쓴다
export interface QuizResult {
  submissionId: number;
  submittedAt: string; // ISO
  correctCount: number;
  totalCount: number;
  results: {
    questionId: number;
    choiceIndex: number; // 내가 고른 답
    correct: boolean;
    correctChoiceIndex: number; // 제출 뒤에만 알려 준다
    // 이 문제가 다루는 개념. conceptId는 백엔드 concept_key(학습 분석의 개념 키와 같은 규칙: 과목 키워드를 소문자로 맞춘 값)
    concepts: QuizConcept[];
    aiExplanation: string | null;
  }[];
}
