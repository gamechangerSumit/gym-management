import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { BellRing, CheckCircle2, MessageCircle, RefreshCw } from 'lucide-react';
import { get, post } from '../api/client';
import type { Reminder } from '../api/types';
import { formatDate, formatDateTime, titleCase } from '../lib/format';
import { Empty, FormError, PageHeader, QueryState } from '../components/ui';
import { useAuth } from '../auth/AuthContext';

export function RemindersPage(){
  const { isOwner }=useAuth();
  const qc=useQueryClient();
  const q=useQuery({queryKey:['reminders'],queryFn:()=>get<Reminder[]>('/api/v1/reminders')});
  const scan=useMutation({mutationFn:()=>post<number>('/api/v1/reminders/scan',{}),onSuccess:()=>qc.invalidateQueries({queryKey:['reminders']})});
  const sent=useMutation({mutationFn:(id:number)=>post('/api/v1/reminders/'+id+'/mark-sent',{}),onSuccess:()=>qc.invalidateQueries({queryKey:['reminders']})});
  return <>
    <PageHeader title="Renewal reminders" actions={isOwner?<button className="btn primary" disabled={scan.isPending} onClick={()=>scan.mutate()}><RefreshCw size={15}/>{scan.isPending?'Scanning…':'Run reminder scan'}</button>:undefined}/>
    <div className="recovery-hero"><div><span className="eyebrow">CONSENT-AWARE QUEUE</span><h2>Send the right reminder at the right time.</h2><p className="muted">The daily job queues reminders at 14, 7, 3 and 0 days before expiry. This app does not send messages automatically; staff sends the prepared message and marks it sent.</p></div><div className="recovery-icon"><BellRing size={24}/></div></div>
    <FormError error={scan.error??sent.error}/>
    <QueryState q={q} isEmpty={items=>items.length===0} empty={<Empty title="No reminders waiting" hint="The daily scan will queue reminders for eligible upcoming expiries."/>}>
      {items=><div className="recovery-list">{items.map(r=><article className="recovery-card reminder-card" key={r.id}>
        <div className="recovery-person"><div className="recovery-avatar"><BellRing size={17}/></div><div><strong>{r.memberName}</strong><span>{r.memberCode} · {r.planName}</span><small>Expires {formatDate(r.expiryDate)} · {r.stageDays===0?'Expiry day':r.stageDays+' days before expiry'}</small></div></div>
        <div className="reminder-message"><p>{r.messageText}</p><small>Queued {formatDateTime(r.scheduledFor)} · {titleCase(r.status)}</small></div>
        <div className="recovery-actions"><a className="btn small ghost" href={'https://wa.me/'+r.phone.replace(/\D/g,'')+'?text='+encodeURIComponent(r.messageText)} target="_blank" rel="noreferrer"><MessageCircle size={14}/> Open WhatsApp</a><button className="btn small primary" disabled={sent.isPending} onClick={()=>{if(window.confirm('Mark reminder for '+r.memberName+' as sent? Only confirm after sending it.'))sent.mutate(r.id)}}><CheckCircle2 size={14}/> Mark sent</button></div>
      </article>)}</div>}
    </QueryState>
  </>;
}
