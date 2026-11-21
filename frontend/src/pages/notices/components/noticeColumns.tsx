import { Link } from 'react-router-dom';
import type { BoardColumn } from '@/components';
import type { Notice } from '@/types';

// 공지 목록 표의 기본 열 (공지사항 목록·관리자 공지 관리가 함께 쓴다)
export const NOTICE_COLUMNS: BoardColumn<Notice>[] = [
  {
    key: 'id',
    label: '번호',
    widthClass: 'w-18',
    render: (row) => row.id,
  },
  {
    key: 'title',
    label: '제목',
    render: (row) => (
      <Link to={`/notices/${row.id}`} className="hover:underline">
        {row.title}
      </Link>
    ),
  },
  {
    key: 'createdAt',
    label: '작성일',
    widthClass: 'w-36',
    render: (row) => row.createdAt,
  },
];
