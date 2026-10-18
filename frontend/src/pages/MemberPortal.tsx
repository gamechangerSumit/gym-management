import { useState, type CSSProperties, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { QRCodeSVG } from 'qrcode.react';
import { Activity, CalendarCheck2, CreditCard, Download, Flame, ShieldCheck, Trophy, UserRound, WalletCards } from 'lucide-react';
import { get, patch, post, qs } from '../api/client';
import type { MeAttendancePage, MeMembership, MePayment, MeProfile, MeQr, Page, Receipt } from '../api/types';
import { formatDate, formatDateTime, formatMoney, titleCase } from '../lib/format';
import { validateEmail } from '../lib/validation';
import { Empty, Field, FormError, Modal, Pager, QueryState, Stat, StatusBadge } from '../components/ui';

export function MyHomePage() {
  const profile = useQuery({ queryKey: ['me'], queryFn: () => get<MeProfile>('/api/v1/me') });
  const ms = useQuery({ queryKey: ['me-ms'], queryFn: () => get<MeMembership>('/api/v1/me/membership') });
  return (
    <>
      <div className="portal-welcome"><div><span className="eyebrow">MEMBER SPACE</span><h1>{profile.data ? `Hi, ${profile.data.fullName.split(' ')[0]} 👋` : 'My Gym'}</h1><p className="muted">Your membership, visits and payments at a glance.</p></div><div className="portal-avatar"><UserRound size={22}/></div></div>
      <QueryState q={ms}>
        {(m) => !m.hasMembership ? <Empty title="No membership yet" hint="Ask the front desk to set up your plan." /> : (
          <>
            <div className="member-hero-card">
              <div><span className="eyebrow">CURRENT PLAN</span><h2>{m.planName}</h2><div className="member-status-line"><StatusBadge status={m.status}/><span>{m.daysRemaining !== null && m.daysRemaining >= 0 ? `${m.daysRemaining} days left` : 'Membership ended'}</span></div></div>
              <div className="membership-ring" style={{'--progress': `${Math.max(0, Math.min(100, m.daysRemaining === null ? 0 : Math.min(100, m.daysRemaining / 30 * 100)))}%`} as CSSProperties}><strong>{m.daysRemaining ?? 0}</strong><span>days</span></div>
            </div>
            <div className="portal-stats"><Stat label="Visits this week" value={m.weeklyGoalPaused?'Paused':`${m.visitsThisWeek}/${m.weeklyVisitGoal}`} hint={m.weeklyGoalPaused?'Approved pause — streak protected':'Weekly goal'}/><Stat label="Weekly streak" value={m.currentStreakWeeks} hint="Weeks meeting goal"/><Stat label="Visits this month" value={m.visitsThisMonth}/><Stat label="Dues" value={formatMoney(m.duesPaise)} hint={m.duesPaise > 0 ? 'Please pay at the front desk' : 'All paid up'}/><Stat label="Ends" value={formatDate(m.endDate)}/></div>
            <div className="portal-actions"><a className="btn primary" href="/me/qr"><Activity size={16}/> Show QR pass</a><a className="btn" href="/me/renewal"><CalendarCheck2 size={16}/> Renew membership</a><a className="btn" href="/me/payments"><WalletCards size={16}/> View payments</a></div>
          </>
        )}
      </QueryState>
    </>
  );
}

export function MyQrPage() {
  const qc = useQueryClient();
  const q = useQuery({ queryKey: ['me-qr'], queryFn: () => get<MeQr>('/api/v1/me/qr'), gcTime: 0 });
  const re = useMutation({ mutationFn: () => post<MeQr>('/api/v1/me/qr/reissue', {}), onSuccess: (r) => qc.setQueryData(['me-qr'], r) });
  return <><div className="portal-page-head"><div><span className="eyebrow">ACCESS PASS</span><h1>My QR pass</h1><p className="muted">Use this code at the front desk to check in.</p></div></div><QueryState q={q}>{(p) => <div className="portal-qr-card"><div className="qr-stage"><QRCodeSVG value={p.payload} size={240} marginSize={2} level="M" aria-label="Your gym QR code"/></div><div className="qr-info"><span className="badge good"><ShieldCheck size={12}/> Active pass</span><h2>Ready to scan</h2><p className="muted">Turn your screen brightness up and hold the QR code steady inside the scanner.</p><div className="qr-meta"><span>Issued</span><strong>{formatDate(p.issuedAt)}</strong></div><FormError error={re.error}/><button className="btn ghost danger" disabled={re.isPending} onClick={()=>{if(window.confirm('Get a new QR code? Your current one will stop working immediately.'))re.mutate();}}>Lost or shared it? Get a new code</button></div></div>}</QueryState></>;
}

export function MyAttendancePage() {
  const [page,setPage]=useState(0);
  const q=useQuery({queryKey:['me-att',page],queryFn:()=>get<MeAttendancePage>('/api/v1/me/attendance'+qs({page,size:20})),placeholderData:(p)=>p});
  return <><div className="portal-page-head"><div><span className="eyebrow">ACTIVITY</span><h1>My visits</h1><p className="muted">Your recent gym check-in history.</p></div></div><QueryState q={q} isEmpty={(a)=>a.totalItems===0} empty={<Empty title="No visits yet" hint="Your check-ins will show up here."/>}>{(a)=><><div className="portal-stats"><Stat label="This month" value={a.visitsThisMonth}/><Stat label="Total visits" value={a.totalItems}/><Stat label="Current streak" value={`${a.currentStreakDays} day${a.currentStreakDays===1?'':'s'}`} hint="Consecutive attendance days"/><Stat label="Longest streak" value={`${a.longestStreakDays} day${a.longestStreakDays===1?'':'s'}`} hint="Personal best"/></div>{a.monthly.length>0&&<div className="portal-chart"><div className="chart-card-head"><div><span className="eyebrow">MONTHLY</span><h3>Visit consistency</h3></div></div><div className="portal-bars">{a.monthly.map((m)=><div key={m.month}><span>{m.visits}</span><i style={{height:`${Math.max(10,Math.min(100,m.visits/Math.max(...a.monthly.map(x=>x.visits),1)*100))}%`}}/><small>{new Date(`${m.month}-01T00:00:00`).toLocaleDateString('en-IN',{month:'short'})}</small></div>)}</div></div>}<h2>History</h2><div className="portal-list">{a.items.map(v=><div className="portal-list-row" key={v.id}><div className="portal-list-icon"><CalendarCheck2 size={16}/></div><div><strong>{formatDateTime(v.checkedInAt)}</strong><span>{v.source==='QR'?'QR check-in':'Front desk'}</span></div><span className="badge info">{v.source==='QR'?'QR':'Manual'}</span></div>)}</div><Pager page={a.page} totalPages={a.totalPages} onPage={setPage}/></>}</QueryState></>;
}

export function MyPaymentsPage() {
  const [page,setPage]=useState(0); const [open,setOpen]=useState<number|null>(null);
  const dues=useQuery({queryKey:['me-dues'],queryFn:()=>get<{billedPaise:number;paidPaise:number;duesPaise:number}>('/api/v1/me/dues')});
  const q=useQuery({queryKey:['me-pay',page],queryFn:()=>get<Page<MePayment>>('/api/v1/me/payments'+qs({page,size:20}))});
  return <><div className="portal-page-head"><div><span className="eyebrow">FINANCES</span><h1>Payments</h1><p className="muted">Receipts and payment history.</p></div></div>{dues.data&&<div className="portal-stats"><Stat label="Billed" value={formatMoney(dues.data.billedPaise)}/><Stat label="Paid" value={formatMoney(dues.data.paidPaise)}/><Stat label="Dues" value={formatMoney(dues.data.duesPaise)}/></div>}<QueryState q={q} isEmpty={(p)=>p.items.length===0} empty={<Empty title="No payments yet"/>}>{(p)=><><div className="portal-list">{p.items.map(x=><div className="portal-list-row" key={x.id}><div className="portal-list-icon"><CreditCard size={16}/></div><div><strong>{formatMoney(x.amountPaise)} <StatusBadge status={x.status}/></strong><span>{formatDateTime(x.paidAt)} · {titleCase(x.method)}</span></div><button className="btn small" onClick={()=>setOpen(x.id)}>Receipt</button></div>)}</div><Pager page={p.page} totalPages={p.totalPages} onPage={setPage}/></>}</QueryState>{open!==null&&<ReceiptModal id={open} onClose={()=>setOpen(null)}/>}</>;
}

function ReceiptModal({id,onClose}:{id:number;onClose:()=>void}){const q=useQuery({queryKey:['me-receipt',id],queryFn:()=>get<Receipt>(`/api/v1/me/payments/${id}/receipt`)});return <Modal title="Receipt" onClose={onClose}><QueryState q={q}>{(r)=><div className="receipt"><h3>{r.gymName}</h3><p>Receipt <strong>{r.receiptNo}</strong> {r.status==='VOID'&&<span className="badge bad">Void</span>}</p><p>{r.paidAtText}</p><p>{r.memberName} ({r.memberCode})</p>{r.planName&&<p>Plan: {r.planName}</p>}<p className="big">{r.amountText}</p><p className="muted">Paid by {titleCase(r.method)}</p>{r.voidReason&&<p className="muted">Void reason: {r.voidReason}</p>}<button className="btn" onClick={()=>window.print()}><Download size={15}/> Print receipt</button></div>}</QueryState></Modal>}

export function MyProfilePage(){const qc=useQueryClient();const q=useQuery({queryKey:['me'],queryFn:()=>get<MeProfile>('/api/v1/me')});return <><div className="portal-page-head"><div><span className="eyebrow">ACCOUNT</span><h1>My profile</h1><p className="muted">Keep your contact information up to date.</p></div></div><QueryState q={q}>{(p)=><ProfileForm profile={p} onSaved={(np)=>qc.setQueryData(['me'],np)}/>}</QueryState><p className="muted small-print"><a href="/change-password">Change password</a></p></>}

function ProfileForm({profile,onSaved}:{profile:MeProfile;onSaved:(p:MeProfile)=>void}){const[email,setEmail]=useState(profile.email??'');const[emergency,setEmergency]=useState(profile.emergencyContact??'');const[transactional,setTransactional]=useState(profile.transactionalMessagesEnabled);const[marketing,setMarketing]=useState(profile.marketingConsent);const[err,setErr]=useState<string|undefined>();const[done,setDone]=useState(false);const m=useMutation({mutationFn:()=>patch<MeProfile>('/api/v1/me',profile.canEditEmail?{email:email.trim()||null,emergencyContact:emergency.trim()||null,transactionalMessagesEnabled:transactional,marketingConsent:marketing}:{emergencyContact:emergency.trim()||null,transactionalMessagesEnabled:transactional,marketingConsent:marketing}),onSuccess:(p)=>{onSaved(p);setDone(true)}});const submit=(e:FormEvent)=>{e.preventDefault();setDone(false);const ee=profile.canEditEmail?validateEmail(email.trim())??undefined:undefined;setErr(ee);if(!ee)m.mutate()};return <form className="portal-profile-card" onSubmit={submit} noValidate><div className="profile-card-head"><div className="portal-profile-avatar"><UserRound size={22}/></div><div><h2>{profile.fullName}</h2><p className="muted">{profile.memberCode} · {profile.phone}</p></div></div><p className="muted small-print">To change your name or phone number, please ask the front desk.</p><Field label="Email" htmlFor="me-email" error={err} hint={profile.canEditEmail?undefined:'The gym has turned off email changes.'}><input id="me-email" type="email" value={email} disabled={!profile.canEditEmail} onChange={(e)=>setEmail(e.target.value)}/></Field><Field label="Emergency contact" htmlFor="me-em"><input id="me-em" value={emergency} maxLength={200} onChange={(e)=>setEmergency(e.target.value)}/></Field><fieldset className="perms"><legend>Communication preferences</legend><label className="check"><input type="checkbox" checked={transactional} onChange={e=>setTransactional(e.target.checked)}/> Membership, payment and service reminders</label><label className="check"><input type="checkbox" checked={marketing} onChange={e=>setMarketing(e.target.checked)}/> Promotional offers and marketing messages</label><small className="muted">Marketing consent is separate. Turning off service reminders cancels queued reminders.</small></fieldset><FormError error={m.error}/>{done&&<p className="ok" role="status">Saved.</p>}<button className="btn primary" disabled={m.isPending}>Save profile</button></form>}
