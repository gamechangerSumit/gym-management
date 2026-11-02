import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { AlertTriangle, CheckCircle2, Phone, RefreshCw, UserRoundCheck, Users, MessageCircle, ScanSearch } from 'lucide-react';
import { get, post, put, qs } from '../api/client';
import type { Followup } from '../api/types';
import { formatDate, titleCase } from '../lib/format';
import { Empty, FormError, PageHeader, QueryState, Stat } from '../components/ui';

const STATUSES = ['NOT_CONTACTED', 'CONTACTED', 'NO_ANSWER', 'WILL_RENEW', 'NOT_INTERESTED'];
const OUTCOMES = ['WILL_RETURN', 'INJURED', 'TRAVELLING', 'TIMING_ISSUE', 'UNHAPPY', 'NO_RESPONSE', 'CANCELLED'];

type FollowupEvent = { id:number; outcome:string; note:string|null; nextActionDate:string|null; recordedBy:string|null; createdAt:string };
type NoShowCase = {
  id: number; memberId: number; memberCode: string; memberName: string; phone: string;
  planName: string | null; membershipEnd: string | null; lastVisit: string | null;
  absentDays: number; outcome: string | null; nextActionDate: string | null;
};

export function RecoveryPage() {
  const qc = useQueryClient();
  const [filter, setFilter] = useState('');
  const [threshold, setThreshold] = useState(10);
  const [outcomes, setOutcomes] = useState<Record<number, string>>({});
  const [notes, setNotes] = useState<Record<number, string>>({});
  const [nextActions, setNextActions] = useState<Record<number, string>>({});
  const [historyFor, setHistoryFor] = useState<number | null>(null);
  const history = useQuery({ queryKey:['no-show-history',historyFor], queryFn:()=>get<FollowupEvent[]>(`/api/v1/no-shows/${historyFor}/history`), enabled:historyFor !== null });
  const q = useQuery({ queryKey: ['followups', filter], queryFn: () => get<Followup[]>('/api/v1/followups' + qs({ status: filter })) });
  const noShows = useQuery({ queryKey: ['no-shows'], queryFn: () => get<NoShowCase[]>('/api/v1/no-shows') });
  const update = useMutation({ mutationFn: (a:{membershipId:number;contactStatus:string;note?:string}) => put('/api/v1/followups', a), onSuccess:()=>qc.invalidateQueries({queryKey:['followups']}) });
  const renew = useMutation({ mutationFn:(id:number)=>post(`/api/v1/memberships/${id}/renewal-order`,{}), onSuccess:()=>{void qc.invalidateQueries({queryKey:['followups']});void qc.invalidateQueries({queryKey:['renewal-orders']});} });
  const scan = useMutation({ mutationFn:()=>post<{affected:number;thresholdDays:number}>(`/api/v1/no-shows/scan?threshold=${threshold}`,{}), onSuccess:()=>qc.invalidateQueries({queryKey:['no-shows']}) });
  const saveOutcome = useMutation({
    mutationFn:(id:number)=>post(`/api/v1/no-shows/${id}/outcome`,{
      outcome:outcomes[id] ?? 'NO_RESPONSE', note:notes[id] ?? '',
      nextActionDate:nextActions[id] || null
    }),
    onSuccess:()=>qc.invalidateQueries({queryKey:['no-shows']})
  });
  const list=q.data??[];
  const redList=noShows.data??[];
  return (
    <>
      <PageHeader title="Retention & recovery" />
      <div className="recovery-hero"><div><span className="eyebrow">RETENTION CENTER</span><h2>Spot risk. Reach out early.</h2><p className="muted">Track members who have stopped visiting, record outcomes and manage renewal follow-ups.</p></div><div className="recovery-icon"><RefreshCw size={24}/></div></div>
      <section className="stats">
        <Stat label="No-show cases" value={redList.length}/>
        <Stat label="Absent 15+ days" value={redList.filter(x=>x.absentDays>=15).length}/>
        <Stat label="Needs renewal contact" value={list.filter(x=>x.contactStatus==='NOT_CONTACTED').length}/>
        <Stat label="Will renew" value={list.filter(x=>x.contactStatus==='WILL_RENEW').length}/>
      </section>

      <section className="panel" style={{marginBottom:24}}>
        <div className="section-heading"><div><span className="eyebrow">ATTENDANCE-BASED RISK</span><h2><AlertTriangle size={20}/> No-show red list</h2><p className="muted">Active members with a valid membership who have missed visits past the threshold.</p></div>
          <div className="attendance-toolbar"><label>Absent days <input aria-label="No-show threshold" type="number" min={1} max={365} value={threshold} onChange={e=>setThreshold(Math.max(1,Math.min(365,Number(e.target.value)||1)))}/></label><button className="btn primary" disabled={scan.isPending} onClick={()=>scan.mutate()}><ScanSearch size={15}/>{scan.isPending?'Scanning…':'Run scan'}</button></div>
        </div>
        <FormError error={scan.error ?? noShows.error ?? saveOutcome.error}/>
        {noShows.isLoading ? <p className="muted">Loading no-show cases…</p> : redList.length===0 ? <Empty title="No open no-show cases" hint="Run a scan to find eligible members who have been absent."/> :
          <div className="recovery-list">{redList.map(m=><article className="recovery-card" key={m.id}>
            <div className="recovery-person"><div className="recovery-avatar">{m.memberName.split(' ').map(n=>n[0]).slice(0,2).join('').toUpperCase()}</div><div><Link to={`/members/${m.memberId}`}><strong>{m.memberName}</strong></Link><span>{m.memberCode} · {m.planName ?? 'Membership'}</span><small>Last visit: {m.lastVisit ? formatDate(m.lastVisit) : 'No recorded visit'} · Expires: {m.membershipEnd ? formatDate(m.membershipEnd) : '—'}</small></div></div>
            <div className="no-show-days"><strong>{m.absentDays}</strong><span>days absent</span></div>
            <div className="recovery-actions"><a className="btn small ghost" href={`tel:${m.phone}`}><Phone size={14}/> Call</a><a className="btn small ghost" href={`https://wa.me/${m.phone.replace(/\D/g,'')}`} target="_blank" rel="noreferrer"><MessageCircle size={14}/> WhatsApp</a></div>
            <button className="btn small ghost" onClick={()=>setHistoryFor(historyFor===m.id?null:m.id)}>{historyFor===m.id?'Hide follow-up history':'View follow-up history'}</button>
            {historyFor===m.id && <div className="no-show-history">{history.isLoading?'Loading history…':history.isError?'Could not load history.':history.data?.length ? history.data.map(h=><div className="no-show-history-row" key={h.id}><strong>{titleCase(h.outcome)}</strong><span>{new Date(h.createdAt).toLocaleString()}</span>{h.note&&<p>{h.note}</p>}{h.nextActionDate&&<small>Next action: {formatDate(h.nextActionDate)}</small>}<small>Recorded by: {h.recordedBy??'System'}</small></div>):<p className="muted">No follow-up history recorded yet.</p>}</div>}
            <div className="no-show-outcome">
              <label>Outcome<select value={outcomes[m.id] ?? m.outcome ?? 'NO_RESPONSE'} onChange={e=>setOutcomes(v=>({...v,[m.id]:e.target.value}))}>{OUTCOMES.map(s=><option key={s} value={s}>{titleCase(s)}</option>)}</select></label>
              <label>Next action<input type="date" value={nextActions[m.id] ?? m.nextActionDate ?? ''} onChange={e=>setNextActions(v=>({...v,[m.id]:e.target.value}))}/></label>
              <label>Note<input value={notes[m.id] ?? ''} maxLength={500} placeholder="Optional follow-up note" onChange={e=>setNotes(v=>({...v,[m.id]:e.target.value}))}/></label>
              <button className="btn small primary" disabled={saveOutcome.isPending} onClick={()=>saveOutcome.mutate(m.id)}><CheckCircle2 size={14}/> Save outcome</button>
            </div>
          </article>)}</div>}
      </section>

      <section className="panel">
        <div className="section-heading"><div><span className="eyebrow">MEMBERSHIP EXPIRY</span><h2><Users size={20}/> Renewal recovery</h2><p className="muted">Contact tracking for expired memberships.</p></div></div>
        <div className="recovery-toolbar"><label>Status<select aria-label="Contact status" value={filter} onChange={(e)=>setFilter(e.target.value)}><option value="">All statuses</option>{STATUSES.map(s=><option key={s} value={s}>{titleCase(s)}</option>)}</select></label><span className="muted"><UserRoundCheck size={15}/> Personal outreach only</span></div>
        <FormError error={update.error ?? renew.error}/>{renew.isSuccess&&<p className="muted">Payment-pending renewal order created. Open Renewal orders to verify payment before extending the membership.</p>}
        <QueryState q={q} isEmpty={(l)=>l.length===0} empty={<Empty title="Nobody to follow up" hint="Expired memberships will appear here."/>}>
          {(renewals)=> <div className="recovery-list">{renewals.map(f=><div className="recovery-card" key={f.membershipId}>
            <div className="recovery-person"><div className="recovery-avatar">{f.memberName.split(' ').map(n=>n[0]).slice(0,2).join('').toUpperCase()}</div><div><Link to={`/members/${f.memberId}`}><strong>{f.memberName}</strong></Link><span>{f.memberCode} · {f.planName}</span><small>Ended {formatDate(f.endDate)}</small></div></div>
            <a className="recovery-phone" href={`tel:${f.phone}`}><Phone size={15}/>{f.phone}</a>
            <div className="recovery-actions"><select aria-label={`Contact status for ${f.memberName}`} value={f.contactStatus} onChange={e=>update.mutate({membershipId:f.membershipId,contactStatus:e.target.value,note:f.note??undefined})}>{STATUSES.map(s=><option key={s} value={s}>{titleCase(s)}</option>)}</select><button className="btn small ghost" onClick={()=>{const note=window.prompt('Add a short note',f.note??'');if(note!==null)update.mutate({membershipId:f.membershipId,contactStatus:f.contactStatus,note:note.trim()});}}>Note</button><button className="btn small primary" disabled={renew.isPending} onClick={()=>renew.mutate(f.membershipId)}><CheckCircle2 size={14}/> Create payment order</button></div>
            {f.note && <div className="recovery-note">{f.note}</div>}
          </div>)}</div>}
        </QueryState>
      </section>
    </>
  );
}
