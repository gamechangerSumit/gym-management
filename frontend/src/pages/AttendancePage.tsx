import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { CalendarDays, CheckCircle2, Clock3, QrCode, UsersRound } from 'lucide-react';
import { get, qs } from '../api/client';
import type { AttendanceRecord, Page } from '../api/types';
import { formatDateTime } from '../lib/format';
import { Empty, PageHeader, Pager, QueryState, Stat } from '../components/ui';

export function AttendancePage() {
  const [date, setDate] = useState('');
  const [page, setPage] = useState(0);
  const q = useQuery({
    queryKey: ['attendance', date, page],
    queryFn: () => get<Page<AttendanceRecord>>('/api/v1/attendance' + qs({ from: date, to: date, page, size: 30 })),
    placeholderData: (prev) => prev,
  });
  const items = q.data?.items ?? [];
  const qrCount = items.filter((x) => x.source === 'QR').length;
  return (
    <>
      <PageHeader title="Attendance" actions={<a className="btn primary" href="/scan"><QrCode size={16} /> Open scanner</a>} />
      <div className="attendance-hero">
        <div><span className="eyebrow">LIVE FLOOR ACTIVITY</span><h2>Every check-in, in one place.</h2><p className="muted">Track arrivals from QR scans or front-desk check-ins.</p></div>
        <div className="attendance-icon"><CheckCircle2 size={25} /></div>
      </div>
      <section className="stats">
        <Stat label="Check-ins shown" value={items.length} />
        <Stat label="QR scans" value={qrCount} />
        <Stat label="Manual" value={items.length - qrCount} />
        <Stat label="Latest" value={items[0] ? <Clock3 size={19} /> : '—'} hint={items[0] ? formatDateTime(items[0].checkedInAt) : 'No activity'} />
      </section>
      <div className="attendance-toolbar">
        <CalendarDays size={17} />
        <div><span>Date</span><input type="date" aria-label="Date" value={date} onChange={(e) => { setDate(e.target.value); setPage(0); }} /></div>
        {date && <button className="btn small ghost" onClick={() => setDate('')}>All dates</button>}
      </div>
      <QueryState q={q} isEmpty={(p) => p.items.length === 0} empty={<Empty title="No check-ins found" hint="Check-ins appear here as members arrive." />}>
        {(p) => (
          <>
            <div className="attendance-list">
              {p.items.map((a) => (
                <div key={a.id} className="attendance-row">
                  <div className="attendance-avatar">{a.memberName.split(' ').map((x) => x[0]).slice(0,2).join('').toUpperCase()}</div>
                  <div className="attendance-person"><strong>{a.memberName}</strong><span>{a.memberCode}</span></div>
                  <div className="attendance-time"><strong>{new Date(a.checkedInAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</strong><span>{formatDateTime(a.checkedInAt)}</span></div>
                  <span className={`attendance-source ${a.source === 'QR' ? 'qr' : ''}`} title={a.assistedReason ?? undefined}>{a.source === 'QR' ? <QrCode size={13} /> : <UsersRound size={13} />}{a.source === 'QR' ? 'QR' : 'Manual'}{a.assistedReason ? ` · ${a.assistedReason.replaceAll('_', ' ').toLowerCase()}` : ''}</span>
                </div>
              ))}
            </div>
            <Pager page={p.page} totalPages={p.totalPages} onPage={setPage} />
          </>
        )}
      </QueryState>
    </>
  );
}
