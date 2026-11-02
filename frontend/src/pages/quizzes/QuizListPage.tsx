import { ListChecks } from 'lucide-react';
import { AsyncBoundary, EmptyState, SubPage } from '@/components';
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
        description="자료를 올리면 그 내용으로 퀴즈를 만들어 드려요."
        action={{ label: '자료 올리기', to: '/materials/upload' }}
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
      <p className="text-body text-ink-soft">
        퀴즈 {quizzes.length}개 · 채점 결과를 개념별 숙련도에 반영하는 기능은
        준비 중이에요
      </p>
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
    <SubPage title="퀴즈" description="올린 자료로 만든 퀴즈를 풀어 보세요">
      <AsyncBoundary>
        <QuizList />
      </AsyncBoundary>
    </SubPage>
  );
}

export default QuizListPage;
