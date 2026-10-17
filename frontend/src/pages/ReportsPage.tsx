import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { get, qs } from '../api/client';
import type { AttendanceReport, Collections, ExpiryReport, OutstandingReport } from '../api/types';
import { formatDate, formatDateTime, formatMoney, titleCase } from '../lib/format';
import { Empty, PageHeader, Pager, QueryState, Stat } from '../components/ui';

type Tab = 'attendance' | 'expiry' | 'collections' | 'outstanding';

export function ReportsPage() {
  const [tab, setTab] = useState<Tab>('attendance');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [page, setPage] = useState(0);
  const rangeless = tab === 'outstanding';
  const params = { from: rangeless ? '' : from, to: rangeless ? '' : to, page, size: 25 };
  const exportHref = `/api/v1/reports/${tab}/export` + qs({ from: params.from, to: params.to });
  const tabs: [Tab, string][] = [['attendance', 'Attendance'], ['expiry', 'Expiry'], ['collections', 'Collections'], ['outstanding', 'Outstanding dues']];
  return (
    <>
      <PageHeader title="Reports" actions={<a className="btn" href={exportHref}>Export CSV</a>} />
      <div className="tabs" role="tablist">
        {tabs.map(([k, l]) => (
          <button key={k} role="tab" aria-selected={tab === k} className={tab === k ? 'active' : ''} onClick={() => { setTab(k); setPage(0); }}>{l}</button>
        ))}
      </div>
      {!rangeless && (
        <div className="filters">
          <label>From <input type="date" value={from} onChange={(e) => { setFrom(e.target.value); setPage(0); }} /></label>
          <label>To <input type="date" value={to} onChange={(e) => { setTo(e.target.value); setPage(0); }} /></label>
        </div>
      )}
      {tab === 'attendance' && <AttendanceTab params={params} setPage={setPage} />}
      {tab === 'expiry' && <ExpiryTab params={params} setPage={setPage} />}
      {tab === 'collections' && <CollectionsTab params={params} setPage={setPage} />}
      {tab === 'outstanding' && <OutstandingTab params={params} setPage={setPage} />}
    </>
  );
}

interface TabProps { params: { from: string; to: string; page: number; size: number }; setPage: (p: number) => void }

function AttendanceTab({ params, setPage }: TabProps) {
  const q = useQuery({ queryKey: ['rep-att', params], queryFn: () => get<AttendanceReport>('/api/v1/reports/attendance' + qs(params)) });
  return (
    <QueryState q={q} isEmpty={(r) => r.totalCheckIns === 0} empty={<Empty title="No check-ins in this period" />}>
      {(r) => (
        <>
          <p className="muted">{formatDate(r.from)} – {formatDate(r.to)}</p>
          <section className="stats"><Stat label="Check-ins" value={r.totalCheckIns} /><Stat label="Unique members" value={r.uniqueMembers} /></section>
          <ul className="list">
            {r.rows.items.map((x, i) => (
              <li key={i} className="row"><div><strong>{x.memberName}</strong> <span className="muted">{x.memberCode}</span></div><span className="muted">{formatDateTime(x.checkedInAt)} · {x.source}</span></li>
            ))}
          </ul>
          <Pager page={r.rows.page} totalPages={r.rows.totalPages} onPage={setPage} />
        </>
      )}
    </QueryState>
  );
}

function ExpiryTab({ params, setPage }: TabProps) {
  const q = useQuery({ queryKey: ['rep-exp', params], queryFn: () => get<ExpiryReport>('/api/v1/reports/expiry' + qs(params)) });
  return (
    <QueryState q={q} isEmpty={(r) => r.totalMemberships === 0} empty={<Empty title="No memberships ending in this period" />}>
      {(r) => (
        <>
          <p className="muted">{r.totalMemberships} memberships end between {formatDate(r.from)} and {formatDate(r.to)}</p>
          <ul className="list">
            {r.rows.items.map((x) => (
              <li key={`${x.memberId}-${x.endDate}`} className="row">
                <div><Link to={`/members/${x.memberId}`}><strong>{x.memberName}</strong></Link> <span className="muted">{x.memberCode} · {x.phone}</span>
                  <div className="muted">{x.planName} · ends {formatDate(x.endDate)}{x.renewed ? ' · renewed' : ''}</div></div>
                <span className="badge info">{titleCase(x.status)}</span>
              </li>
            ))}
          </ul>
          <Pager page={r.rows.page} totalPages={r.rows.totalPages} onPage={setPage} />
        </>
      )}
    </QueryState>
  );
}

function CollectionsTab({ params, setPage }: TabProps) {
  const q = useQuery({ queryKey: ['rep-col', params], queryFn: () => get<Collections>('/api/v1/reports/collections' + qs(params)) });
  return (
    <QueryState q={q} isEmpty={(r) => r.paymentCount === 0 && r.voidedCount === 0} empty={<Empty title="No payments in this period" />}>
      {(r) => (
        <>
          <p className="muted">{formatDate(r.from)} – {formatDate(r.to)} · the total equals the sum of the rows below; voided payments are excluded.</p>
          <section className="stats">
            <Stat label="Collected" value={formatMoney(r.totalPaise)} hint={`${r.paymentCount} payments`} />
            {r.voidedCount > 0 && <Stat label="Voided" value={formatMoney(r.voidedPaise)} hint={`${r.voidedCount} payments`} />}
            {r.byMethod.map((m) => <Stat key={m.method} label={titleCase(m.method)} value={formatMoney(m.paise)} hint={`${m.count} payments`} />)}
          </section>
          <ul className="list">
            {r.rows.items.map((x) => (
              <li key={x.paymentId} className="row"><div><strong>{x.memberName}</strong> <span className="muted">{x.memberCode}</span>
                <div className="muted">{formatDateTime(x.paidAt)} · {titleCase(x.method)} · {x.receiptNo}</div></div><strong>{formatMoney(x.amountPaise)}</strong></li>
            ))}
          </ul>
          <Pager page={r.rows.page} totalPages={r.rows.totalPages} onPage={setPage} />
        </>
      )}
    </QueryState>
  );
}

function OutstandingTab({ params, setPage }: TabProps) {
  const q = useQuery({ queryKey: ['rep-out', params.page], queryFn: () => get<OutstandingReport>('/api/v1/reports/outstanding' + qs({ page: params.page, size: params.size })) });
  return (
    <QueryState q={q} isEmpty={(r) => r.memberCount === 0} empty={<Empty title="No outstanding dues" hint="Everyone is paid up." />}>
      {(r) => (
        <>
          <section className="stats"><Stat label="Total outstanding" value={formatMoney(r.totalDuesPaise)} hint={`${r.memberCount} members`} /></section>
          <ul className="list">
            {r.rows.items.map((x) => (
              <li key={x.memberId} className="row"><div><Link to={`/members/${x.memberId}`}><strong>{x.memberName}</strong></Link> <span className="muted">{x.memberCode} · {x.phone}</span>
                <div className="muted">Billed {formatMoney(x.billedPaise)} · Paid {formatMoney(x.paidPaise)}</div></div><strong>{formatMoney(x.duesPaise)}</strong></li>
            ))}
          </ul>
          <Pager page={r.rows.page} totalPages={r.rows.totalPages} onPage={setPage} />
        </>
      )}
    </QueryState>
  );
}
