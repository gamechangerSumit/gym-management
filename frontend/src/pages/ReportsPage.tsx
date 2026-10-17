import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { BarChart3, CalendarDays, Download, IndianRupee, TrendingUp, UsersRound } from 'lucide-react';
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
  const tabs: [Tab, string, typeof BarChart3][] = [
    ['attendance', 'Attendance', CalendarDays],
    ['expiry', 'Expiry', TrendingUp],
    ['collections', 'Collections', IndianRupee],
    ['outstanding', 'Outstanding dues', UsersRound],
  ];

  return (
    <div className="reports-page">
      <PageHeader title="Reports" actions={
        <a className="btn" href={exportHref}><Download size={16} /> Export CSV</a>
      } />
      <div className="report-hero">
        <div>
          <span className="eyebrow">ANALYTICS CENTER</span>
          <h2>See how the gym is performing.</h2>
          <p className="muted">Turn attendance, renewals and collections into a clear operating picture.</p>
        </div>
        <div className="report-hero-icon"><BarChart3 size={25} /></div>
      </div>
      <div className="report-tabs" role="tablist">
        {tabs.map(([k, l, Icon]) => (
          <button key={k} role="tab" aria-selected={tab === k} className={tab === k ? 'active' : ''} onClick={() => { setTab(k); setPage(0); }}>
            <Icon size={16} /> {l}
          </button>
        ))}
      </div>
      {!rangeless && (
        <div className="report-filters">
          <label>From <input type="date" value={from} onChange={(e) => { setFrom(e.target.value); setPage(0); }} /></label>
          <label>To <input type="date" value={to} onChange={(e) => { setTo(e.target.value); setPage(0); }} /></label>
        </div>
      )}
      {tab === 'attendance' && <AttendanceTab params={params} setPage={setPage} />}
      {tab === 'expiry' && <ExpiryTab params={params} setPage={setPage} />}
      {tab === 'collections' && <CollectionsTab params={params} setPage={setPage} />}
      {tab === 'outstanding' && <OutstandingTab params={params} setPage={setPage} />}
    </div>
  );
}

interface TabProps { params: { from: string; to: string; page: number; size: number }; setPage: (p: number) => void }

function AttendanceTab({ params, setPage }: TabProps) {
  const q = useQuery({ queryKey: ['rep-att', params], queryFn: () => get<AttendanceReport>('/api/v1/reports/attendance' + qs(params)) });
  return (
    <QueryState q={q} isEmpty={(r) => r.totalCheckIns === 0} empty={<Empty title="No check-ins in this period" />}>
      {(r) => (
        <>
          <section className="stats">
            <Stat label="Total check-ins" value={r.totalCheckIns} />
            <Stat label="Unique members" value={r.uniqueMembers} />
            <Stat label="Average / day" value={r.byDay.length ? (r.totalCheckIns / r.byDay.length).toFixed(1) : '0'} />
          </section>
          {r.byDay.length > 0 && (
            <div className="chart-card">
              <div className="chart-card-head"><div><span className="eyebrow">TRAFFIC</span><h3>Daily check-ins</h3></div><span className="muted">{formatDate(r.from)} – {formatDate(r.to)}</span></div>
              <div className="bar-chart" aria-label="Daily check-ins chart">
                {r.byDay.map((d) => {
                  const max = Math.max(...r.byDay.map((x) => x.count), 1);
                  return <div className="bar-column" key={d.date} title={`${formatDate(d.date)}: ${d.count}`}>
                    <div className="bar-value">{d.count}</div><div className="bar-track"><div className="bar-fill" style={{ height: `${Math.max(6, (d.count / max) * 100)}%` }} /></div>
                    <span>{new Date(d.date + 'T00:00:00').toLocaleDateString(undefined, { day: '2-digit', month: 'short' })}</span>
                  </div>;
                })}
              </div>
            </div>
          )}
          <div className="report-table-card">
            <div className="report-table-head"><span>Member</span><span>Check-in</span><span>Source</span></div>
            {r.rows.items.map((x, i) => (
              <div className="report-table-row" key={i}><div><strong>{x.memberName}</strong><small>{x.memberCode}</small></div><span>{formatDateTime(x.checkedInAt)}</span><span className="badge info">{x.source}</span></div>
            ))}
          </div>
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
          <div className="insight-banner"><TrendingUp size={18} /><div><strong>{r.totalMemberships} memberships</strong><span> end between {formatDate(r.from)} and {formatDate(r.to)}</span></div></div>
          <div className="report-table-card">
            <div className="report-table-head"><span>Member</span><span>Plan</span><span>End date</span><span>Status</span></div>
            {r.rows.items.map((x) => (
              <div className="report-table-row expiry-row" key={`${x.memberId}-${x.endDate}`}>
                <div><Link to={`/members/${x.memberId}`}><strong>{x.memberName}</strong></Link><small>{x.memberCode} · {x.phone}</small></div>
                <span>{x.planName}</span><span>{formatDate(x.endDate)}</span><span className="badge info">{titleCase(x.status)}{x.renewed ? ' · renewed' : ''}</span>
              </div>
            ))}
          </div>
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
      {(r) => {
        const total = r.byMethod.reduce((sum, x) => sum + x.paise, 0);
        let cursor = 0;
        const stops = r.byMethod.map((m) => {
          const start = total ? (cursor / total) * 100 : 0;
          cursor += m.paise;
          return `${m.paise === 0 ? 'transparent' : 'var(--brand)'} ${start}% ${total ? (cursor / total) * 100 : 0}%`;
        }).join(', ');
        return (
          <>
            <section className="stats">
              <Stat label="Collected" value={formatMoney(r.totalPaise)} hint={`${r.paymentCount} payments`} />
              <Stat label="Voided" value={formatMoney(r.voidedPaise)} hint={`${r.voidedCount} payments`} />
              {r.byMethod.slice(0, 2).map((m) => <Stat key={m.method} label={titleCase(m.method)} value={formatMoney(m.paise)} hint={`${m.count} payments`} />)}
            </section>
            <div className="chart-split">
              <div className="chart-card donut-card">
                <div className="chart-card-head"><div><span className="eyebrow">PAYMENT MIX</span><h3>Collections by method</h3></div></div>
                <div className="donut-wrap"><div className="donut" style={{ background: total ? `conic-gradient(var(--brand) 0 ${(r.byMethod[0]?.paise ?? 0) / total * 360}deg, var(--cyan) ${(r.byMethod[0]?.paise ?? 0) / total * 360}deg 360deg)` : 'var(--surface-strong)' }}><div className="donut-hole"><strong>{formatMoney(r.totalPaise)}</strong><span>Total</span></div></div>
                  <div className="donut-legend">{r.byMethod.map((m, i) => <div key={m.method}><span className={`legend-dot ${i % 2 ? 'cyan' : ''}`} /><span>{titleCase(m.method)}</span><strong>{formatMoney(m.paise)}</strong></div>)}</div>
                </div>
              </div>
              <div className="chart-card">
                <div className="chart-card-head"><div><span className="eyebrow">PERFORMANCE</span><h3>Collection summary</h3></div></div>
                <div className="collection-summary"><div><strong>{r.paymentCount}</strong><span>recorded payments</span></div><div><strong>{r.voidedCount}</strong><span>voided payments</span></div><div><strong>{formatMoney(r.voidedPaise)}</strong><span>voided value</span></div></div>
                <div className="progress-line"><span style={{ width: `${r.paymentCount + r.voidedCount ? (r.paymentCount / (r.paymentCount + r.voidedCount)) * 100 : 0}%` }} /></div>
                <p className="muted small-print">Voided payments are excluded from collected totals.</p>
              </div>
            </div>
            <div className="report-table-card">
              <div className="report-table-head"><span>Member</span><span>Paid at</span><span>Method</span><span>Amount</span></div>
              {r.rows.items.map((x) => <div className="report-table-row" key={x.paymentId}><div><strong>{x.memberName}</strong><small>{x.memberCode}</small></div><span>{formatDateTime(x.paidAt)}</span><span>{titleCase(x.method)}</span><strong>{formatMoney(x.amountPaise)}</strong></div>)}
            </div>
            <Pager page={r.rows.page} totalPages={r.rows.totalPages} onPage={setPage} />
          </>
        );
      }}
    </QueryState>
  );
}

function OutstandingTab({ params, setPage }: TabProps) {
  const q = useQuery({ queryKey: ['rep-out', params.page], queryFn: () => get<OutstandingReport>('/api/v1/reports/outstanding' + qs({ page: params.page, size: params.size })) });
  return (
    <QueryState q={q} isEmpty={(r) => r.memberCount === 0} empty={<Empty title="No outstanding dues" hint="Everyone is paid up." />}>
      {(r) => (
        <>
          <div className="outstanding-hero"><div><span className="eyebrow">OUTSTANDING</span><h2>{formatMoney(r.totalDuesPaise)}</h2><p className="muted">{r.memberCount} members currently have dues.</p></div><div className="outstanding-icon"><IndianRupee size={24} /></div></div>
          <div className="report-table-card">
            <div className="report-table-head"><span>Member</span><span>Billed</span><span>Paid</span><span>Due</span></div>
            {r.rows.items.map((x) => <div className="report-table-row" key={x.memberId}><div><Link to={`/members/${x.memberId}`}><strong>{x.memberName}</strong></Link><small>{x.memberCode} · {x.phone}</small></div><span>{formatMoney(x.billedPaise)}</span><span>{formatMoney(x.paidPaise)}</span><strong className="due-value">{formatMoney(x.duesPaise)}</strong></div>)}
          </div>
          <Pager page={r.rows.page} totalPages={r.rows.totalPages} onPage={setPage} />
        </>
      )}
    </QueryState>
  );
}
