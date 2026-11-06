import { useState, type SubmitEvent } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router-dom';
import { LoaderCircle } from 'lucide-react';
import { AnimatePresence } from 'motion/react';
import * as m from 'motion/react-m';
import { MATERIALS_QUERY_KEY, uploadMaterial } from '@/api';
import {
  AsyncBoundary,
  Button,
  FileDropZone,
  FormRow,
  SelectInput,
  SubPage,
  TextInput,
} from '@/components';
import {
  FADE_UP,
  MATERIAL_EXTENSIONS,
  MATERIAL_MAX_SIZE_MB,
  MATERIAL_TITLE_MAX_LENGTH,
} from '@/constants';
import { useCourses } from '@/hooks';
import {
  MATERIAL_FORMAT_LABEL,
  formatSemester,
  normalizeKeyword,
  validateMaterialFile,
} from '@/utils';

const ACCEPT = MATERIAL_EXTENSIONS.map((ext) => `.${ext}`).join(',');
const ACCEPT_LABEL = `${MATERIAL_FORMAT_LABEL} · ${MATERIAL_MAX_SIZE_MB}MB까지`;

function MaterialUploadForm() {
  const { data: courses } = useCourses();
  const [courseId, setCourseId] = useState('');
  const [title, setTitle] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [fileError, setFileError] = useState<string>();
  const [agreed, setAgreed] = useState(false);
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  // 올리면 목록을 새로 받고, 요약이 만들어지는 과정을 볼 수 있게 자료 상세로 옮겨 간다
  const mutation = useMutation({
    mutationFn: uploadMaterial,
    onSuccess: (material) => {
      void queryClient.invalidateQueries({ queryKey: MATERIALS_QUERY_KEY });
      navigate(`/materials/${material.id}`);
    },
  });

  const hasCourses = courses.length > 0;

  const handleFileChange = (picked: File | undefined) => {
    mutation.reset();
    if (!picked) {
      setFile(null);
      setFileError(undefined);
      return;
    }
    const error = validateMaterialFile(picked);
    setFileError(error);
    setFile(error ? null : picked);
    // 제목이 비어 있으면 파일 이름을 제안한다
    if (!error && !title) {
      setTitle(
        picked.name.replace(/\.[^.]+$/, '').slice(0, MATERIAL_TITLE_MAX_LENGTH),
      );
    }
  };

  const handleSubmit = (e: SubmitEvent<HTMLFormElement>) => {
    e.preventDefault();
    const trimmed = normalizeKeyword(title);
    if (!file || !agreed || !courseId || !trimmed) return;
    mutation.mutate({ courseId: Number(courseId), title: trimmed, file });
  };

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-8">
      <div className="flex flex-col gap-6">
        <FormRow label="과목" htmlFor="material-course" required>
          {!hasCourses ? (
            <p className="text-body text-ink-soft">
              등록한 과목이 없어요.{' '}
              <Link to="/courses" className="text-primary underline">
                내 과목에서 먼저 등록해 주세요
              </Link>
            </p>
          ) : (
            <SelectInput
              id="material-course"
              required
              value={courseId}
              onChange={(e) => setCourseId(e.target.value)}
              className="w-full"
            >
              <option value="">과목을 골라 주세요</option>
              {courses.map((course) => (
                <option key={course.id} value={course.id}>
                  {course.name} ({formatSemester(course.semester)})
                </option>
              ))}
            </SelectInput>
          )}
        </FormRow>
        <FormRow
          label="자료 파일"
          htmlFor="material-file"
          required
          error={fileError ?? ''}
        >
          <FileDropZone
            id="material-file"
            accept={ACCEPT}
            title={file ? file.name : '파일을 끌어다 놓거나 눌러서 고르세요'}
            description={
              file ? '다른 파일을 고르려면 다시 눌러 주세요' : ACCEPT_LABEL
            }
            aria-invalid={fileError ? true : undefined}
            aria-describedby="material-file-error"
            onFiles={(files) => handleFileChange(files[0])}
          />
        </FormRow>
        <FormRow label="자료 제목" htmlFor="material-title" required>
          <TextInput
            id="material-title"
            type="text"
            required
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            maxLength={MATERIAL_TITLE_MAX_LENGTH}
            placeholder="운영체제 3주차 정리 노트"
          />
        </FormRow>
      </div>

      <section className="rounded-2xl border border-line p-5">
        <h2 className="text-body font-bold text-heading">
          본인이 이용 권한이 있는 자료만 올려 주세요
        </h2>
        <ul className="mt-2 list-disc pl-5 text-body text-ink-soft">
          <li>이런 자료를 권해요: 직접 쓴 필기, 정리 노트, 연습문제 풀이</li>
          <li>
            올린 자료와 그로부터 만든 요약·퀴즈는 올린 본인만 볼 수 있고, 다른
            사람과 공유되지 않아요.
          </li>
        </ul>
        <label className="mt-3 flex items-start gap-2 text-body text-ink">
          <input
            type="checkbox"
            checked={agreed}
            onChange={(e) => setAgreed(e.target.checked)}
            className="mt-1"
          />
          <span>이 자료를 올릴 권한이 있어요.</span>
        </label>
      </section>

      <AnimatePresence>
        {mutation.isError && (
          <m.p {...FADE_UP} role="alert" className="text-body text-danger">
            {mutation.error.message}
          </m.p>
        )}
      </AnimatePresence>

      <div className="flex">
        <Button
          type="submit"
          size="lg"
          className="w-full"
          disabled={mutation.isPending || !agreed || !file || !hasCourses}
        >
          {mutation.isPending && (
            <LoaderCircle
              size={18}
              aria-hidden="true"
              className="animate-spin"
            />
          )}
          {mutation.isPending ? '올리는 중…' : '자료 올리기'}
        </Button>
      </div>
    </form>
  );
}

function MaterialUploadPage() {
  return (
    <SubPage
      narrow="form"
      title="자료 올리기"
      description="공부할 자료를 올리면 요약과 핵심 개념을 정리해 드려요"
    >
      <AsyncBoundary>
        <MaterialUploadForm />
      </AsyncBoundary>
    </SubPage>
  );
}

export default MaterialUploadPage;
