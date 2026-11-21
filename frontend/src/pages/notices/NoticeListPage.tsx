import { AsyncBoundary, BoardTable, SubPage } from '@/components';
import { useNotices } from '@/hooks';
import { NOTICE_COLUMNS } from './components/noticeColumns';

function NoticeTable() {
  const { data: notices } = useNotices();

  return (
    <BoardTable
      columns={NOTICE_COLUMNS}
      rows={notices}
      getRowKey={(row) => row.id}
      emptyMessage="아직 등록된 공지사항이 없어요."
    />
  );
}

function NoticeListPage() {
  return (
    <SubPage title="공지사항" description="서비스 소식과 안내를 알려 드려요">
      <AsyncBoundary>
        <NoticeTable />
      </AsyncBoundary>
    </SubPage>
  );
}

export default NoticeListPage;
