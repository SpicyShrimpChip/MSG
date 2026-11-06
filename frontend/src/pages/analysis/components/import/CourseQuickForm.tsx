import { useState } from 'react';
import { FormRow, SelectInput } from '@/components';
import CourseForm from '@/pages/courses/components/CourseForm';
import type { Course } from '@/types';
import { formatSemester } from '@/utils';

interface CourseQuickFormProps {
  courses: Course[];
  // 저장하거나 수정을 취소했을 때 (대화 고르기 단계는 폼을 닫는다)
  onDone?: () => void;
}

// 대화 가져오기 화면 안에서 과목을 추가하거나 기존 과목에 키워드를 더한다.
// 읽은 대화는 이 화면 메모리에만 있어서, 내 과목 화면으로 나가면 파일을 다시 올려야 하기 때문이다.
// 저장하면 과목 목록을 다시 불러오고, 부모가 새 키워드로 대화를 다시 나눈다
function CourseQuickForm({ courses, onDone }: CourseQuickFormProps) {
  // 'new' = 새 과목, 숫자 = 키워드를 더할 기존 과목.
  // 빠진 대화는 대개 이미 있는 과목의 키워드가 모자라서이므로 기존 과목부터(키워드가 없는 과목 먼저) 고른다
  const [target, setTarget] = useState<'new' | number>(
    () =>
      (courses.find((course) => course.keywords.length === 0) ?? courses[0])
        ?.id ?? 'new',
  );
  const editing =
    target === 'new'
      ? null
      : (courses.find((course) => course.id === target) ?? null);

  return (
    <div className="flex flex-col gap-5">
      {courses.length > 0 && (
        <FormRow label="어디에 넣을까요?" htmlFor="import-course-target">
          <SelectInput
            id="import-course-target"
            value={String(target)}
            onChange={(e) =>
              setTarget(
                e.target.value === 'new' ? 'new' : Number(e.target.value),
              )
            }
            className="w-full max-w-80"
          >
            <option value="new">새 과목 추가</option>
            {courses.map((course) => (
              <option key={course.id} value={course.id}>
                {course.name} ({formatSemester(course.semester)})에 키워드
                더하기
              </option>
            ))}
          </SelectInput>
        </FormRow>
      )}
      <CourseForm
        key={target}
        courses={courses}
        editing={editing}
        showTitle={false}
        onDone={() => {
          setTarget('new');
          onDone?.();
        }}
      />
    </div>
  );
}

export default CourseQuickForm;
