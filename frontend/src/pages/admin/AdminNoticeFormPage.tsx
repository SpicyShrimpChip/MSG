import { useState, type SubmitEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { LoaderCircle } from 'lucide-react';
import { NOTICES_QUERY_KEY, createNotice, updateNotice } from '@/api';
import {
  AsyncBoundary,
  Button,
  EmptyState,
  FIELD_CLASS,
  FormRow,
  SubPage,
  TextInput,
  ErrorText,
} from '@/components';
import {
  NOTICE_CONTENT_MAX_LENGTH,
  NOTICE_TITLE_MAX_LENGTH,
} from '@/constants';
import { useNotice } from '@/hooks';
import type { NoticeDetail } from '@/types';

// 새로 쓰기(notice 없음)와 고치기(notice 있음)가 같은 폼을 쓴다
function NoticeForm({ notice }: { notice?: NoticeDetail }) {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [title, setTitle] = useState(notice?.title ?? '');
  const [content, setContent] = useState(notice?.content ?? '');

  const mutation = useMutation({
    mutationFn: () => {
      const input = { title, content };
      return notice ? updateNotice(notice.id, input) : createNotice(input);
    },
    onSuccess: async () => {
      // 목록과 상세 모두 새로 받은 뒤 관리 목록으로 돌아간다
      await queryClient.invalidateQueries({ queryKey: NOTICES_QUERY_KEY });
      navigate('/admin/notices');
    },
  });

  const empty = !title.trim() || !content.trim();

  const handleSubmit = (e: SubmitEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (empty) return;
    mutation.mutate();
  };

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-6">
      <FormRow label="제목" htmlFor="notice-title" required>
        <TextInput
          id="notice-title"
          type="text"
          required
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          maxLength={NOTICE_TITLE_MAX_LENGTH}
          placeholder="공지 제목"
        />
      </FormRow>
      <FormRow
        label="본문"
        htmlFor="notice-content"
        required
        hint="글자로만 보여 줘요. 줄바꿈은 그대로 반영되고, HTML이나 마크다운 표시는 해석되지 않아요."
      >
        <textarea
          id="notice-content"
          required
          rows={12}
          value={content}
          onChange={(e) => setContent(e.target.value)}
          maxLength={NOTICE_CONTENT_MAX_LENGTH}
          aria-describedby="notice-content-count"
          placeholder="공지 내용을 입력해 주세요"
          className={`w-full resize-y px-3 py-3 leading-relaxed placeholder:text-ink-muted ${FIELD_CLASS}`}
        />
        <p
          id="notice-content-count"
          className="text-right text-caption text-ink-muted"
        >
          {content.length} / {NOTICE_CONTENT_MAX_LENGTH}자
        </p>
      </FormRow>

      <ErrorText error={mutation.error} />

      <div className="flex flex-wrap gap-2">
        <Button type="submit" size="lg" disabled={mutation.isPending || empty}>
          {mutation.isPending && (
            <LoaderCircle
              size={18}
              aria-hidden="true"
              className="animate-spin"
            />
          )}
          {mutation.isPending
            ? '저장하는 중…'
            : notice
              ? '수정 저장하기'
              : '공지 올리기'}
        </Button>
        <Button to="/admin/notices" variant="secondary" size="lg">
          취소
        </Button>
      </div>
    </form>
  );
}

function EditNotice({ noticeId }: { noticeId: number }) {
  const { data: notice } = useNotice(noticeId);
  return <NoticeForm notice={notice} />;
}

function AdminNoticeFormPage() {
  const { noticeId } = useParams();
  const editing = noticeId !== undefined;
  const id = Number(noticeId);
  const validId = Number.isInteger(id) && id > 0;

  return (
    <SubPage
      narrow="reading"
      title={editing ? '공지 고치기' : '새 공지 쓰기'}
      description="올린 공지는 모든 사용자가 볼 수 있어요 (관리자 전용)"
    >
      {!editing ? (
        <NoticeForm />
      ) : validId ? (
        <AsyncBoundary>
          <EditNotice noticeId={id} />
        </AsyncBoundary>
      ) : (
        <EmptyState
          title="공지사항을 찾을 수 없어요"
          description="주소가 잘못됐을 수 있어요. 관리 목록에서 다시 골라 주세요."
          action={{ label: '공지 관리로 가기', to: '/admin/notices' }}
        />
      )}
    </SubPage>
  );
}

export default AdminNoticeFormPage;
