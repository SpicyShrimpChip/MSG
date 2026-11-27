import type { Course } from '@/types';
import { Button, Card, Chip } from '@/components';
import { formatSemester } from '@/utils';

interface CourseCardProps {
  course: Course;
  onEdit: () => void;
  onDelete: () => void;
  deleting: boolean;
}

function CourseCard({ course, onEdit, onDelete, deleting }: CourseCardProps) {
  return (
    <Card as="li" className="flex flex-col gap-3 p-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex flex-col gap-1">
          <h3 className="text-lg font-semibold text-heading">{course.name}</h3>
          <p className="text-caption text-ink-muted">
            {formatSemester(course.semester)}
          </p>
        </div>
        <div className="flex gap-2">
          <Button
            variant="secondary"
            onClick={onEdit}
            aria-label={`${course.name} 수정`}
          >
            수정
          </Button>
          <Button
            variant="secondary"
            onClick={onDelete}
            disabled={deleting}
            aria-label={`${course.name} 삭제`}
          >
            삭제
          </Button>
        </div>
      </div>
      {course.keywords.length > 0 ? (
        <ul aria-label="키워드" className="flex flex-wrap gap-1.5">
          {course.keywords.map((keyword) => (
            <li key={keyword}>
              <Chip>{keyword}</Chip>
            </li>
          ))}
        </ul>
      ) : (
        <p className="text-caption text-ink-muted">
          키워드가 없어요. 과목 이름으로만 대화를 골라요.
        </p>
      )}
    </Card>
  );
}

export default CourseCard;
