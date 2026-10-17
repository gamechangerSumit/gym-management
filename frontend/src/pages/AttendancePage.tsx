import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { get, qs } from '../api/client';
import type { AttendanceRecord, Page } from '../api/types';
import { formatDateTime } from '../lib/format';
import { Empty, PageHeader, Pager, QueryState } from '../components/ui';

export function AttendancePage() {
  const [date, setDate] = useState('');
  const [page, setPage] = useState(0);
  const q = useQuery({
    queryKey: ['attendance', date, page],
    queryFn: () => get<Page<AttendanceRecord>>('/api/v1/attendance' + qs({ from: date, to: date, page, size: 30 })),
    placeholderData: (prev) => prev,
  });
  return (
    <>
      <PageHeader title="Attendance" />
      <div className="filters">
        <input type="date" aria-label="Date" value={date} onChange={(e) => { setDate(e.target.value); setPage(0); }} />
        {date && <button className="btn small ghost" onClick={() => setDate('')}>Clear</button>}
      </div>
      <QueryState q={q} isEmpty={(p) => p.items.length === 0} empty={<Empty title="No check-ins found" hint="Check-ins appear here as members arrive." />}>
        {(p) => (
          <>
            <ul className="list">
              {p.items.map((a) => (
                <li key={a.id} className="row">
                  <div><strong>{a.memberName}</strong> <span className="muted">{a.memberCode}</span></div>
                  <div className="muted right">{formatDateTime(a.checkedInAt)} · {a.source === 'QR' ? 'QR' : 'Manual'}</div>
                </li>
              ))}
            </ul>
            <Pager page={p.page} totalPages={p.totalPages} onPage={setPage} />
          </>
        )}
      </QueryState>
    </>
  );
}
