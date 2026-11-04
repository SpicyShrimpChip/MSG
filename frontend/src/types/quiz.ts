// 퀴즈 타입. 백엔드 quiz DTO(QuizSummaryResponse·QuizResponse·QuizResultResponse·QuizCreateRequest)와 같은 모양이다.
// 11/3: 학습 모드 3가지와 객관식·주관식·서술형을 더했다. V6 필드는 그대로 두고 더하기만 했다

// 시험 대비 / 자기개발 / 업무 활용. 11/2 이전에 만든 퀴즈는 null
export type QuizMode = 'exam_prep' | 'self_development' | 'work';
export type QuestionType = 'multiple_choice' | 'short_answer' | 'essay';
export type QuizDifficulty = 'easy' | 'medium' | 'hard';

// 퀴즈 목록 한 줄 (GET /api/quizzes)
export interface QuizSummary {
  id: number;
  title: string;
  courseId: number;
  // 이 퀴즈와 연결한 자료 (자료 상세의 퀴즈 탭에서 묶는다)
  materialId: number | null;
  questionCount: number;
  // 가장 최근 제출 기준. 아직 안 풀었으면 null
  lastCorrectCount: number | null;
  mode: QuizMode | null;
  difficulty: QuizDifficulty | null;
  // AI가 만든 퀴즈인지
  aiGenerated: boolean;
  // 가장 최근 제출의 점수·만점 (채점이 끝난 답만)과 AI 채점을 기다리는 답 수. 안 풀었으면 null
  lastScore: number | null;
  lastMaxScore: number | null;
  lastPendingCount: number | null;
  // 가장 최근 제출에서 AI 판단이 엇갈려 확인이 필요한 답 수
  lastReviewCount: number | null;
  mockExam: boolean;
  // 사전·사후평가. 일반 퀴즈는 null
  assessmentKind: AssessmentKind | null;
}

export type AssessmentKind = 'pre' | 'post';

export interface QuizQuestion {
  id: number;
  type: QuestionType;
  text: string;
  // 객관식 보기. 다른 유형은 빈 배열
  choices: string[];
  // 업무 활용 문제의 상황·내 역할
  scenario: string | null;
  role: string | null;
  // 주관식·서술형 답변 작성 안내
  guide: string | null;
  // 무엇을 보고 평가하는지 (업무 활용 서술형만, 기준 이름만)
  criteria: string[];
  maxScore: number;
}

// 풀기 화면용 퀴즈 (GET /api/quizzes/{id}). 정답·허용 답·모범 답안은 들어 있지 않다
export interface Quiz {
  id: number;
  title: string;
  courseId: number;
  materialId: number | null;
  mode: QuizMode | null;
  difficulty: QuizDifficulty | null;
  jobField: string | null;
  jobFieldOther: string | null;
  examScope: string | null;
  // 문제를 만든 AI. 사람이 넣은 퀴즈는 null
  generatedBy: { provider: string; model: string | null } | null;
  // 자료 본문(발췌)을 읽고 만들었는지
  materialContentUsed: boolean;
  // 모의시험 (시험 대비에서 범위 전체를 실제 시험처럼)
  mockExam: boolean;
  assessmentKind: AssessmentKind | null;
  // 사후평가가 비교하는 사전평가
  preAssessmentId: number | null;
  // 자료 본문을 요청했지만 쓰지 못한 이유 (만든 직후 응답에만)
  materialContentNote: MaterialContentNote | null;
  // 평가 개념의 출처: 자료에서 뽑은 개념 / 과목 키워드. 일반 퀴즈는 null
  topicSource: 'material' | 'course' | null;
  // 개념 확인 퀴즈가 다루는 자료 개념 (topicId = 개념 conceptId). 그 밖의 퀴즈는 null
  checkTopic: { materialId: number; topicId: string; name: string } | null;
  questions: QuizQuestion[];
}

// NO_TEXT: 글이 없는 PDF(스캔본, OCR 미지원) / ENCRYPTED: 암호 PDF / DISABLED: 운영 설정으로 본문 전송이 꺼짐
export type MaterialContentNote =
  | 'NOT_SUPPORTED'
  | 'NO_TEXT'
  | 'ENCRYPTED'
  | 'EMPTY'
  | 'FAILED'
  | 'PERSONAL_INFO'
  | 'DISABLED';

export interface QuizConcept {
  conceptId: string;
  name: string;
}

// 문제 하나의 답. 객관식은 choiceIndex(안 고르면 -1), 주관식·서술형은 text
export interface QuizAnswer {
  questionId: number;
  choiceIndex?: number;
  text?: string;
}

export type GradingStatus = 'graded' | 'needs_review' | 'pending';
export type GradingMethod = 'code' | 'match' | 'ai';
export type RubricLevel = 'met' | 'partial' | 'not_met';
export type AssessmentStatus = 'ready' | 'not_ready' | 'needs_check';
export type SuggestionKind = 'next' | 'prerequisite' | 'related';

export interface QuizResultItem {
  questionId: number;
  type: QuestionType;
  choiceIndex: number; // 내가 고른 답 (객관식이 아니면 -1)
  textAnswer: string | null;
  // 만점을 받았는지 (채점이 끝난 답만 true가 될 수 있다)
  correct: boolean;
  correctChoiceIndex: number | null; // 객관식만, 제출 뒤에만 알려 준다
  // 이 문제가 다루는 개념. conceptId는 백엔드 concept_key(학습 분석의 개념 키와 같은 규칙: 과목 키워드를 소문자로 맞춘 값)
  concepts: QuizConcept[];
  aiExplanation: string | null;
  // 점수와 채점 상태는 서버 코드가 정한다
  score: number;
  maxScore: number;
  gradingStatus: GradingStatus;
  gradingMethod: GradingMethod;
  // 채점 대기·확인 필요 이유 (사용자용 문구)
  gradingMessage: string | null;
  aiCalledCount: number;
  aiJudgedCount: number;
  acceptedAnswers: string[];
  aiModelAnswer: string | null;
  rubric: {
    criterion: string;
    points: number;
    // 코드가 계산한 점수. 모델 다수 의견이 없으면 null
    earned: number | null;
    level: RubricLevel | null;
    agreeCount: number;
    judgedCount: number;
    aiReason: string | null;
  }[];
  aiFeedback: {
    strengths: string | null;
    missing: string | null;
    improvements: string | null;
    practical: string | null;
  } | null;
}

export interface QuizConceptStat {
  conceptId: string;
  name: string;
  questionCount: number;
  gradedCount: number;
  // 채점이 끝나고 만점을 받은 문제 수 (대기·확인 필요는 틀린 것으로 세지 않는다)
  correctCount: number;
  score: number;
  maxScore: number;
  pendingCount: number;
  reviewCount: number;
  // 득점률이 기준보다 낮은 보완 개념
  weak: boolean;
}

// 채점 결과 (POST /api/quizzes/{id}/submissions, GET .../submissions/latest).
// 정오·점수·결과 판단은 서버 코드가 정하고, ai로 시작하는 필드와 suggestions만 AI가 쓴다
export interface QuizResult {
  submissionId: number;
  submittedAt: string; // ISO
  correctCount: number;
  totalCount: number;
  score: number;
  maxScore: number;
  pendingCount: number;
  reviewCount: number;
  mode: QuizMode | null;
  results: QuizResultItem[];
  // 코드 규칙으로 판단한 학습 상태와 근거 (숙련도 점수가 아니다)
  assessment: {
    status: AssessmentStatus;
    reasons: string[];
    byType: {
      type: QuestionType;
      questionCount: number;
      gradedCount: number;
      score: number;
      maxScore: number;
    }[];
    byConcept: QuizConceptStat[];
  };
  // AI가 제안한 다음 학습 개념 (자기개발). 실제 결과가 아니라 제안
  suggestions: {
    kind: SuggestionKind;
    concept: string;
    aiReason: string | null;
  }[];
}

// AI로 퀴즈 만들기 (POST /api/quizzes)
export interface QuizCreateInput {
  // 같은 키로 다시 보내면 새로 만들지 않는다 (버튼을 두 번 눌러도 하나만)
  requestKey: string;
  courseId: number;
  mode: QuizMode;
  questionTypes: QuestionType[];
  questionCount: number;
  difficulty: QuizDifficulty;
  concepts?: string[];
  examScope?: string;
  jobField?: string;
  jobFieldOther?: string;
  materialId?: number;
  // 모의시험 (exam_prep만, 20문제까지)
  mockExam?: boolean;
  // 개념 확인 퀴즈: materialId의 자료에서 뽑은 개념 하나만 다룬다 (concepts는 무시)
  checkConceptId?: string;
}

// 만들기 요청 상태 (GET /api/quizzes/generations/{requestKey}). 새로 고친 화면이 같은 요청의 결과를 찾는다
export interface QuizGenerationStatus {
  status: 'running' | 'succeeded' | 'failed' | 'timed_out';
  quizId: number | null;
  message: string | null;
  deadlineAt: string | null;
}

// 과목의 개념별 퀴즈 결과 (GET /api/quizzes/concept-stats?courseId=)
export interface ConceptQuizStats extends QuizConceptStat {
  feedback: 'already_know' | 'want_to_study' | null;
  // 이미 알아요를 남긴 뒤 푼 문제로 확인한 결과
  confirmation:
    'confirmed' | 'not_confirmed' | 'needs_check' | 'not_taken' | null;
  lastSubmittedAt: string | null;
}
