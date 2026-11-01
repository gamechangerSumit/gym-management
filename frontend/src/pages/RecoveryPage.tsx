import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { CheckCircle2, Phone, RefreshCw, UserRoundCheck } from 'lucide-react';
import { get, post, put, qs } from '../api/client';
import type { Followup } from '../api/types';
import { formatDate, titleCase } from '../lib/format';
import { Empty, FormError, PageHeader, QueryState, Stat } from '../components/ui';

const STATUSES = ['NOT_CONTACTED', 'CONTACTED', 'NO_ANSWER', 'WILL_RENEW', 'NOT_INTERESTED'];

export function RecoveryPage() {
  const qc = useQueryClient();
  const [filter, setFilter] = useState('');
  const q = useQuery({ queryKey: ['followups', filter], queryFn: () => get<Followup[]>('/api/v1/followups' + qs({ status: filter })) });
  const update = useMutation({ mutationFn: (a:{membershipId:number;contactStatus:string;note?:string}) => put('/api/v1/followups', a), onSuccess:()=>qc.invalidateQueries({queryKey:['followups']}) });
  const renew = useMutation({ mutationFn:(id:number)=>post(`/api/v1/memberships/${id}/renew`,{}), onSuccess:()=>qc.invalidateQueries({queryKey:['followups']}) });
  const list=q.data??[];
  return (
    <>
      <PageHeader title="Renewal recovery" />
      <div className="recovery-hero"><div><span className="eyebrow">RETENTION CENTER</span><h2>Bring members back.</h2><p className="muted">Prioritize expired memberships, track outreach and renew in one flow.</p></div><div className="recovery-icon"><RefreshCw size={24}/></div></div>
      <section className="stats"><Stat label="Needs contact" value={list.filter(x=>x.contactStatus==='NOT_CONTACTED').length}/><Stat label="Contacted" value={list.filter(x=>x.contactStatus==='CONTACTED').length}/><Stat label="Will renew" value={list.filter(x=>x.contactStatus==='WILL_RENEW').length}/><Stat label="Total follow-ups" value={list.length}/></section>
      <div className="recovery-toolbar"><label>Status<select aria-label="Contact status" value={filter} onChange={(e)=>setFilter(e.target.value)}><option value="">All statuses</option>{STATUSES.map(s=><option key={s} value={s}>{titleCase(s)}</option>)}</select></label><span className="muted"><UserRoundCheck size={15}/> Personal outreach only</span></div>
      <FormError error={update.error ?? renew.error}/>
      <QueryState q={q} isEmpty={(l)=>l.length===0} empty={<Empty title="Nobody to follow up" hint="Expired memberships will appear here."/>}>
        {(list)=> <div className="recovery-list">{list.map(f=><div className="recovery-card" key={f.membershipId}>
          <div className="recovery-person"><div className="recovery-avatar">{f.memberName.split(' ').map(n=>n[0]).slice(0,2).join('').toUpperCase()}</div><div><Link to={`/members/${f.memberId}`}><strong>{f.memberName}</strong></Link><span>{f.memberCode} · {f.planName}</span><small>Ended {formatDate(f.endDate)}</small></div></div>
          <a className="recovery-phone" href={`tel:${f.phone}`}><Phone size={15}/>{f.phone}</a>
          <div className="recovery-actions"><select aria-label={`Contact status for ${f.memberName}`} value={f.contactStatus} onChange={(e)=>update.mutate({membershipId:f.membershipId,contactStatus:e.target.value,note:f.note??undefined})}>{STATUSES.map(s=><option key={s} value={s}>{titleCase(s)}</option>)}</select><button className="btn small ghost" onClick={()=>{const note=window.prompt('Add a short note',f.note??'');if(note!==null)update.mutate({membershipId:f.membershipId,contactStatus:f.contactStatus,note:note.trim()});}}>Note</button><button className="btn small primary" disabled={renew.isPending} onClick={()=>renew.mutate(f.membershipId)}><CheckCircle2 size={14}/> Renew</button></div>
          {f.note && <div className="recovery-note">{f.note}</div>}
        </div>)}</div>}
      </QueryState>
    </>
  );
}
