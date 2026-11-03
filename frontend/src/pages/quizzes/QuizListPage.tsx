import { ListChecks } from 'lucide-react';
import { AsyncBoundary, Button, EmptyState, SubPage } from '@/components';
import { useCourses, useQuizzes } from '@/hooks';
import { formatSemester } from '@/utils';
import QuizCard from './components/QuizCard';

function QuizList() {
  const { data: quizzes } = useQuizzes();
  const { data: courses } = useCourses();

  if (quizzes.length === 0) {
    return (
      <EmptyState
        icon={<ListChecks size={24} aria-hidden="true" />}
        title="아직 만든 퀴즈가 없어요"
        description="시험 대비·자기개발·업무 활용 중 목적을 골라 AI로 퀴즈를 만들어 보세요."
        action={{ label: '퀴즈 만들기', to: '/quizzes/new' }}
      />
    );
  }

  // 내 과목 순서대로 묶는다. 과목을 지운 퀴즈는 맨 뒤에 모은다
  const groups = [
    ...courses.map((course) => ({
      key: String(course.id),
      title: `${course.name} · ${formatSemester(course.semester)}`,
      quizzes: quizzes.filter((quiz) => quiz.courseId === course.id),
    })),
    {
      key: 'orphaned',
      title: '과목을 지운 퀴즈',
      quizzes: quizzes.filter(
        (quiz) => !courses.some((course) => course.id === quiz.courseId),
      ),
    },
  ].filter((group) => group.quizzes.length > 0);

  return (
    <div className="flex flex-col gap-8">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-body text-ink-soft">
          퀴즈 {quizzes.length}개 · 개념별 결과는 학습 대시보드에서 대화 분석과
          나란히 볼 수 있어요
        </p>
        <Button to="/quizzes/new">퀴즈 만들기</Button>
      </div>
      {groups.map((group) => (
        <section
          key={group.key}
          aria-labelledby={`quiz-group-${group.key}`}
          className="flex flex-col gap-3"
        >
          <h2
            id={`quiz-group-${group.key}`}
            className="text-lg font-extrabold text-heading"
          >
            {group.title}{' '}
            <span className="text-body font-medium text-ink-muted">
              {group.quizzes.length}개
            </span>
          </h2>
          <ul className="flex flex-col gap-3">
            {group.quizzes.map((quiz) => (
              <QuizCard key={quiz.id} quiz={quiz} />
            ))}
          </ul>
        </section>
      ))}
    </div>
  );
}

function QuizListPage() {
  return (
    <SubPage
      title="퀴즈"
      description="공부하는 목적에 맞춰 만든 퀴즈를 풀고 결과를 확인해요"
    >
      <AsyncBoundary>
        <QuizList />
      </AsyncBoundary>
    </SubPage>
  );
}

export default QuizListPage;
