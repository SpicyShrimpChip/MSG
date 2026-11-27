import type { ReactNode } from 'react';

export interface BoardColumn<T> {
  key: string;
  label: string;
  // Tailwind 너비 클래스. 예: 'w-18'
  widthClass?: string;
  render: (row: T) => ReactNode;
}

interface BoardTableProps<T> {
  columns: BoardColumn<T>[];
  rows: T[];
  getRowKey: (row: T) => string | number;
  emptyMessage: string;
}

// 게시판 표: 진한 상단선 2px, 넓은 표는 상자 안에서 가로 스크롤
function BoardTable<T>({
  columns,
  rows,
  getRowKey,
  emptyMessage,
}: BoardTableProps<T>) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-130 border-collapse border-t-2 border-heading text-body">
        <thead>
          <tr className="bg-surface-muted">
            {columns.map((column) => (
              <th
                key={column.key}
                scope="col"
                className={`border-b border-line px-4 py-3 text-left text-caption font-semibold text-ink-soft ${column.widthClass ?? ''}`}
              >
                {column.label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.length === 0 ? (
            <tr>
              <td
                colSpan={columns.length}
                className="border-b border-line px-4 py-10 text-center text-ink-muted"
              >
                {emptyMessage}
              </td>
            </tr>
          ) : (
            rows.map((row) => (
              <tr key={getRowKey(row)}>
                {columns.map((column) => (
                  <td
                    key={column.key}
                    className="border-b border-line px-4 py-3.5"
                  >
                    {column.render(row)}
                  </td>
                ))}
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  );
}

export default BoardTable;
