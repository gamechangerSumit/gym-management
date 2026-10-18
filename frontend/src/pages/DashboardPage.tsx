import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import type { CSSProperties } from 'react';
import { ArrowUpRight, CalendarCheck2, CreditCard, HeartPulse, UserPlus, Users, WalletCards } from 'lucide-react';
import { get } from '../api/client';
import type { ActionList, DashboardSummary, HealthScore, Leakage } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { formatMoney, formatDate, titleCase } from '../lib/format';
import { Empty, ErrorBox, PageHeader, QueryState, Spinner, Stat } from '../components/ui';

const TYPE_LABEL: Record<string, string> = { EXPIRING: 'Expiring soon', EXPIRED: 'Recently expired', OVERDUE_DUES: 'Dues outstanding', NO_RECENT_VISIT: 'Stopped visiting' };

export function DashboardPage() {
  const { isOwner } = useAuth();
  const summary = useQuery({ queryKey: ['dash-summary'], queryFn: () => get<DashboardSummary>('/api/v1/dashboard/summary') });
  const actions = useQuery({ queryKey: ['dash-actions'], queryFn: () => get<ActionList>('/api/v1/dashboard/action-list') });
  return (
    <>
      <PageHeader title="Dashboard" actions={<div className="actions"><Link className="btn" to="/attendance"><CalendarCheck2 size={16}/> Attendance</Link><Link className="btn" to="/members"><Users size={16}/> Members</Link>{isOwner&&<Link className="btn primary" to="/payments"><CreditCard size={16}/> Payments</Link>}</div>} />
      <QueryState q={summary}>{(s)=><section className="stats dashboard-stats"><Stat label="Active members" value={s.activeMembers}/><Stat label={`Expiring in ${s.expiringSoonDays} days`} value={s.expiringSoon}/><Stat label="Expired" value={s.expired}/><Stat label="Check-ins today" value={s.todaysCheckIns}/>{s.collections&&<Stat label="Collected this month" value={formatMoney(s.collections.totalPaise)} hint={`${s.collections.paymentCount} payments · ${s.collections.rule}`}/>}</section>}</QueryState>
      <QueryState q={summary}>{(s)=><>
        <div className="dashboard-section-head"><div><span className="eyebrow">RETENTION</span><h2>Member health and renewals</h2></div></div>
        <section className="stats dashboard-stats">
          <Stat label="Active in 7 days" value={s.activeMembers7d}/>
          <Stat label="Open no-show cases" value={s.openNoShowCases}/>
          <Stat label="Returned in 7 days" value={s.membersReturned7d}/>
          <Stat label="Renewals due in 7 days" value={s.renewalsDue7d}/>
          <Stat label="Renewal collection this month" value={formatMoney(s.renewalCollectedMonthPaise)}/>
        </section>
      </>}</QueryState>
      <div className="dashboard-welcome"><div><span className="eyebrow">TODAY AT A GLANCE</span><h2>Keep the floor moving.</h2><p className="muted">Focus on the members and tasks that need attention today.</p></div><Link className="quick-action" to="/members"><span><UserPlus size={18}/></span><div><strong>Add member</strong><small>Grow your member base</small></div><ArrowUpRight size={17}/></Link></div>
      <div className="dashboard-section-head"><div><span className="eyebrow">ACTION CENTER</span><h2>Who needs attention</h2></div><Link className="text-link" to="/recovery">Open recovery <ArrowUpRight size={14}/></Link></div>
      <QueryState q={actions} isEmpty={(a)=>a.items.length===0} empty={<Empty title="All clear" hint="No members need follow-up right now."/>}>{(a)=><><ul className="list">{a.items.map((i,idx)=><li key={`${i.type}-${i.memberId}-${idx}`} className="row"><div><Link to={`/members/${i.memberId}`}><strong>{i.memberName}</strong></Link>{' '}<span className="muted">{i.memberCode} · {i.phone}</span><div>{i.detail}</div><small className="muted">Why listed: {i.rule}</small></div><span className="badge info">{TYPE_LABEL[i.type]??titleCase(i.type)}</span></li>)}</ul><details className="muted"><summary>How this list is built</summary><ul>{a.rules.map(r=><li key={r}>{r}</li>)}</ul></details></>}</QueryState>
      {isOwner&&<OwnerInsights/>}
    </>
  );
}

function OwnerInsights() {
  const health=useQuery({queryKey:['dash-health'],queryFn:()=>get<HealthScore>('/api/v1/dashboard/health')});
  const leak=useQuery({queryKey:['dash-leak'],queryFn:()=>get<Leakage>('/api/v1/dashboard/leakage')});
  return <>
    <div className="dashboard-section-head"><div><span className="eyebrow">PERFORMANCE</span><h2>Gym health</h2></div><HeartPulse size={19}/></div>
    {health.isLoading?<Spinner/>:health.isError?<ErrorBox error={health.error}/>:health.data&&<div className="health-dashboard-card">
      <div className="health-score"><div className="health-ring" style={{'--score':`${health.data.score??0}%`} as CSSProperties}><strong>{health.data.score===null?'—':health.data.score}</strong><span>/100</span></div><div><span className="eyebrow">OVERALL HEALTH</span><h3>{health.data.score===null?'Not enough data yet':health.data.label}</h3><p className="muted">{health.data.disclaimer}</p></div></div>
      <div className="health-factors">{health.data.factors.map(f=><div className="health-factor" key={f.key}><div><strong>{f.name}</strong><small>{f.detail}</small></div><div className="factor-meter"><span style={{width:`${f.value??0}%`}}/></div><b>{f.value===null?'—':`${f.value}%`}</b></div>)}</div>
    </div>}
    <div className="dashboard-section-head"><div><span className="eyebrow">RISK SIGNALS</span><h2>Revenue leakage</h2></div><Link className="text-link" to="/reports"><WalletCards size={14}/> Reports</Link></div>
    {leak.isLoading?<Spinner/>:leak.isError?<ErrorBox error={leak.error}/>:leak.data&&<div className="leakage-grid">{leak.data.indicators.map((i,idx)=><div key={i.key} className="leakage-card"><div className="leakage-top"><strong>{i.title}</strong><span>{i.count}</span></div><div className="leakage-bar"><span style={{width:`${Math.min(100,i.count*12)}%`}}/></div><small className="muted">{i.rule}</small>{i.amountPaise!==null&&<strong className="leakage-money">{formatMoney(i.amountPaise)}</strong>}<span className="leakage-index">{idx+1}</span></div>)}</div>}
    <p className="muted">As of {formatDate(leak.data?.asOf)}</p>
  </>;
}
