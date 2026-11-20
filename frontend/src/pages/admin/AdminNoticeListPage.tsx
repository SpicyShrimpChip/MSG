import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { NOTICES_QUERY_KEY, deleteNotice } from '@/api';
import { useNotices } from '@/hooks';
import {
  AsyncBoundary,
  BoardTable,
  Button,
  ConfirmDialog,
  SubPage,
  type BoardColumn,
  ErrorText,
} from '@/components';
import type { Notice } from '@/types';
import { NOTICE_COLUMNS } from '@/pages/notices/components/noticeColumns';

function NoticeAdminTable() {
  const queryClient = useQueryClient();
  const { data: notices } = useNotices();
  const [deleting, setDeleting] = useState<Notice | null>(null);

  const removal = useMutation({
    mutationFn: deleteNotice,
    // 목록과 상세 모두 새로 받는다
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: NOTICES_QUERY_KEY }),
  });

  const columns: BoardColumn<Notice>[] = [
    ...NOTICE_COLUMNS,
    {
      key: 'actions',
      label: '관리',
      widthClass: 'w-40',
      render: (row) => (
        <span className="flex gap-3">
          <Link
            to={`/admin/notices/${row.id}/edit`}
            aria-label={`${row.title} 수정`}
            className="font-medium text-primary hover:underline"
          >
            수정
          </Link>
          <button
            type="button"
            onClick={() => setDeleting(row)}
            disabled={removal.isPending}
            aria-label={`${row.title} 삭제`}
            className="font-medium text-ink-soft hover:text-heading hover:underline disabled:opacity-50"
          >
            삭제
          </button>
        </span>
      ),
    },
  ];

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-body text-ink-soft">공지 {notices.length}개</p>
        <Button to="/admin/notices/new">새 공지 쓰기</Button>
      </div>

      <ErrorText error={removal.error} />

      <BoardTable
        columns={columns}
        rows={notices}
        getRowKey={(row) => row.id}
        emptyMessage="등록한 공지가 없어요. 새 공지를 써 보세요."
      />

      <ConfirmDialog
        open={deleting !== null}
        title={`'${deleting?.title ?? ''}' 공지를 삭제할까요?`}
        description="목록과 상세 화면에서 모두 사라지고 되돌릴 수 없어요."
        confirmLabel="삭제하기"
        onConfirm={() => {
          if (deleting) removal.mutate(deleting.id);
          setDeleting(null);
        }}
        onCancel={() => setDeleting(null)}
      />
    </div>
  );
}

function AdminNoticeListPage() {
  return (
    <SubPage
      title="공지 관리"
      description="서비스 공지를 쓰고, 고치고, 지울 수 있어요 (관리자 전용)"
    >
      <AsyncBoundary>
        <NoticeAdminTable />
      </AsyncBoundary>
    </SubPage>
  );
}

export default AdminNoticeListPage;
