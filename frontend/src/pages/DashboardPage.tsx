import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { get } from '../api/client';
import type { ActionList, DashboardSummary, HealthScore, Leakage } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { formatMoney, formatDate, titleCase } from '../lib/format';
import { Empty, ErrorBox, PageHeader, QueryState, Spinner, Stat } from '../components/ui';
import { ArrowUpRight, CalendarCheck2, CreditCard, UserPlus, Users } from 'lucide-react';

const TYPE_LABEL: Record<string, string> = {
  EXPIRING: 'Expiring soon', EXPIRED: 'Recently expired', OVERDUE_DUES: 'Dues outstanding', NO_RECENT_VISIT: 'Stopped visiting',
};

export function DashboardPage() {
  const { isOwner } = useAuth();
  const summary = useQuery({ queryKey: ['dash-summary'], queryFn: () => get<DashboardSummary>('/api/v1/dashboard/summary') });
  const actions = useQuery({ queryKey: ['dash-actions'], queryFn: () => get<ActionList>('/api/v1/dashboard/action-list') });
  return (
    <>
      <PageHeader title="Dashboard" actions={
        <div className="actions">
          <Link className="btn" to="/attendance"><CalendarCheck2 size={16} /> Attendance</Link>
          <Link className="btn" to="/members"><Users size={16} /> Members</Link>
          {isOwner && <Link className="btn primary" to="/payments"><CreditCard size={16} /> Payments</Link>}
        </div>
      } />
      <QueryState q={summary}>
        {(s) => (
          <section className="stats">
            <Stat label="Active members" value={s.activeMembers} />
            <Stat label={`Expiring in ${s.expiringSoonDays} days`} value={s.expiringSoon} />
            <Stat label="Expired" value={s.expired} />
            <Stat label="Check-ins today" value={s.todaysCheckIns} />
            {s.collections && (
              <Stat label="Collected this month" value={formatMoney(s.collections.totalPaise)}
                hint={`${s.collections.paymentCount} payments · ${s.collections.rule}`} />
            )}
          </section>
        )}
      </QueryState>

      <div className="dashboard-welcome">
        <div><span className="eyebrow">TODAY AT A GLANCE</span><h2>Keep the floor moving.</h2><p className="muted">Focus on the members and tasks that need attention today.</p></div>
        <Link className="quick-action" to="/members"><span><UserPlus size={18} /></span><div><strong>Add member</strong><small>Grow your member base</small></div><ArrowUpRight size={17} /></Link>
      </div>
      <h2>Who needs attention</h2>
      <QueryState q={actions} isEmpty={(a) => a.items.length === 0}
        empty={<Empty title="All clear" hint="No members need follow-up right now." />}>
        {(a) => (
          <>
            <ul className="list">
              {a.items.map((i, idx) => (
                <li key={`${i.type}-${i.memberId}-${idx}`} className="row">
                  <div>
                    <Link to={`/members/${i.memberId}`}><strong>{i.memberName}</strong></Link>{' '}
                    <span className="muted">{i.memberCode} · {i.phone}</span>
                    <div>{i.detail}</div>
                    <small className="muted">Why listed: {i.rule}</small>
                  </div>
                  <span className="badge info">{TYPE_LABEL[i.type] ?? titleCase(i.type)}</span>
                </li>
              ))}
            </ul>
            <details className="muted"><summary>How this list is built</summary>
              <ul>{a.rules.map((r) => <li key={r}>{r}</li>)}</ul>
            </details>
          </>
        )}
      </QueryState>
      {isOwner && <OwnerInsights />}
    </>
  );
}

function OwnerInsights() {
  const health = useQuery({ queryKey: ['dash-health'], queryFn: () => get<HealthScore>('/api/v1/dashboard/health') });
  const leak = useQuery({ queryKey: ['dash-leak'], queryFn: () => get<Leakage>('/api/v1/dashboard/leakage') });
  return (
    <>
      <h2>Gym health</h2>
      {health.isLoading ? <Spinner /> : health.isError ? <ErrorBox error={health.error} /> : health.data && (
        <div className="card">
          <p className="big">{health.data.score === null ? 'Not enough data yet' : `${health.data.score}/100`} <span className="muted">{health.data.label}</span></p>
          <p className="muted">{health.data.disclaimer}</p>
          <ul className="list">
            {health.data.factors.map((f) => (
              <li key={f.key} className="row"><div><strong>{f.name}</strong> <span className="muted">(weight {f.weight})</span>
                <div>{f.detail}</div><small className="muted">{f.rule}</small></div>
                <span>{f.value === null ? '—' : `${f.value}%`}</span></li>
            ))}
          </ul>
        </div>
      )}
      <h2>Revenue leakage</h2>
      {leak.isLoading ? <Spinner /> : leak.isError ? <ErrorBox error={leak.error} /> : leak.data && (
        <ul className="list">
          {leak.data.indicators.map((i) => (
            <li key={i.key} className="row"><div><strong>{i.title}</strong><div><small className="muted">{i.rule}</small></div></div>
              <span>{i.count}{i.amountPaise !== null ? ` · ${formatMoney(i.amountPaise)}` : ''}</span></li>
          ))}
        </ul>
      )}
      <p className="muted">As of {formatDate(leak.data?.asOf)}</p>
    </>
  );
}
