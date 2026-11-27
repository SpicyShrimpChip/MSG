import { useParams } from 'react-router-dom';
import { useNotice } from '@/hooks';
import { AsyncBoundary, Button, EmptyState, SubPage } from '@/components';
import { formatDate } from '@/utils';

function NoticeContent({ noticeId }: { noticeId: number }) {
  const { data: notice } = useNotice(noticeId);
  const updatedAt = formatDate(notice.updatedAt);

  return (
    <article className="border-t-2 border-heading">
      <header className="flex flex-col gap-2 border-b border-line px-2 py-5">
        <h2 className="text-xl font-semibold break-words text-heading">
          {notice.title}
        </h2>
        <p className="flex flex-wrap gap-x-3 text-caption text-ink-muted">
          <span>작성일 {notice.createdAt}</span>
          {updatedAt && <span>수정됨 {updatedAt}</span>}
        </p>
      </header>
      {/* 본문은 글자로만 보여 준다. 줄바꿈만 살리고 HTML로 해석하지 않는다 */}
      <p className="px-2 py-8 text-body leading-relaxed break-words whitespace-pre-line text-ink">
        {notice.content || '본문이 없는 공지예요.'}
      </p>
    </article>
  );
}

function NoticeDetailPage() {
  const { noticeId } = useParams();
  const id = Number(noticeId);

  return (
    <SubPage title="공지사항" description="서비스 소식과 안내를 알려 드려요">
      {Number.isInteger(id) && id > 0 ? (
        <AsyncBoundary
          notFoundAction={{ label: '공지사항 목록으로', to: '/notices' }}
        >
          <NoticeContent noticeId={id} />
        </AsyncBoundary>
      ) : (
        <EmptyState
          title="공지사항을 찾을 수 없어요"
          description="삭제됐거나 주소가 잘못됐을 수 있어요. 목록에서 다시 골라 주세요."
        />
      )}
      <div>
        <Button to="/notices" variant="secondary">
          목록으로
        </Button>
      </div>
    </SubPage>
  );
}

export default NoticeDetailPage;
