import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { CalendarClock, CheckCircle2, CreditCard, RefreshCw } from 'lucide-react';
import { get, post } from '../api/client';
import type { MeMembership, RenewalOrder, RenewalPlan } from '../api/types';
import { formatDateTime, formatMoney, titleCase } from '../lib/format';
import { useAuth } from '../auth/AuthContext';
import { Empty, FormError, PageHeader, QueryState, StatusBadge } from '../components/ui';

const METHODS=['CASH','UPI','CARD','BANK_TRANSFER','OTHER'];

export function MemberRenewalPage() {
  const qc=useQueryClient();
  const [selected,setSelected]=useState<number|null>(null);
  const options=useQuery({queryKey:['me-renewal-options'],queryFn:()=>get<RenewalPlan[]>('/api/v1/me/renewal-options')});
  const membership=useQuery({queryKey:['me-ms'],queryFn:()=>get<MeMembership>('/api/v1/me/membership')});
  const orders=useQuery({queryKey:['me-renewal-orders'],queryFn:()=>get<RenewalOrder[]>('/api/v1/me/renewal-orders')});
  const create=useMutation({mutationFn:(planId:number)=>post<RenewalOrder>('/api/v1/me/renewal-orders',{planId}),onSuccess:()=>{setSelected(null);void qc.invalidateQueries({queryKey:['me-renewal-orders']});}});
  const cancel=useMutation({mutationFn:(id:number)=>post('/api/v1/me/renewal-orders/'+id+'/cancel',{}),onSuccess:()=>qc.invalidateQueries({queryKey:['me-renewal-orders']})});
  const pending=orders.data?.some(o=>o.status==='PAYMENT_PENDING');
  return <>
    <PageHeader title="Renew membership"/>
    <div className="recovery-hero"><div><span className="eyebrow">RENEWAL OPTIONS</span><h2>Keep your training going.</h2><p className="muted">Review the exact plan price and duration. Your membership is extended only after reception records the payment.</p></div><div className="recovery-icon"><RefreshCw size={24}/></div></div>
    {membership.data?.hasMembership&&<div className="panel renewal-current"><span className="eyebrow">CURRENT MEMBERSHIP</span><strong>{membership.data.planName}</strong><span>Ends {membership.data.endDate}</span><StatusBadge status={membership.data.status??'UNKNOWN'}/></div>}
    <FormError error={create.error??cancel.error}/>
    <section className="panel"><div className="section-heading"><div><span className="eyebrow">AVAILABLE PLANS</span><h2>Choose a renewal plan</h2></div></div>
      <QueryState q={options} isEmpty={p=>p.length===0} empty={<Empty title="No plans available" hint="Please ask reception to configure an active plan."/>}>
        {plans=><div className="addon-grid">{plans.map(p=><article className={'addon-tile '+(selected===p.id?'selected':'')} key={p.id}><div className="addon-icon"><CalendarClock size={18}/></div><strong>{p.name}</strong><p>{p.description||'Continue your gym membership with this plan.'}</p><strong className="addon-price">{formatMoney(p.pricePaise)}</strong><small>{p.durationDays} days · final price shown</small><button className="btn primary" disabled={create.isPending||pending} onClick={()=>{setSelected(p.id);if(window.confirm('Create a payment-pending renewal request for '+p.name+' at '+formatMoney(p.pricePaise)+'?'))create.mutate(p.id);}}>{pending?'Renewal pending':'Choose plan'}</button></article>)}</div>}
      </QueryState>
      {pending&&<p className="muted">You already have a renewal request awaiting payment. Cancel it before choosing a different plan.</p>}
    </section>
    <section className="panel"><div className="section-heading"><div><span className="eyebrow">YOUR REQUESTS</span><h2>Renewal history</h2></div></div>
      <QueryState q={orders} isEmpty={p=>p.length===0} empty={<Empty title="No renewal requests yet"/>}>
        {list=><div className="recovery-list">{list.map(o=><article className="recovery-card" key={o.id}><div className="recovery-person"><div className="recovery-avatar"><RefreshCw size={17}/></div><div><strong>{o.planName}</strong><span>Order #{o.id} · {o.durationDays} days</span><small>{formatDateTime(o.createdAt)}</small></div></div><div className="addon-order-total"><strong>{formatMoney(o.pricePaise)}</strong><StatusBadge status={o.status}/></div>{o.status==='PAYMENT_PENDING'&&<button className="btn small ghost" disabled={cancel.isPending} onClick={()=>cancel.mutate(o.id)}>Cancel request</button>}{o.status==='PAID'&&<p className="muted">Payment recorded. Membership #{o.paidMembershipId} activated/extended.</p>}</article>)}</div>}
      </QueryState>
    </section>
  </>;
}

export function RenewalOrdersPage() {
  const { can }=useAuth();
  const qc=useQueryClient();
  const [methods,setMethods]=useState<Record<number,string>>({});
  const [activePayment,setActivePayment]=useState<number|null>(null);
  const q=useQuery({queryKey:['renewal-orders'],queryFn:()=>get<RenewalOrder[]>('/api/v1/renewal-orders')});
  const record=useMutation({mutationFn:(o:RenewalOrder)=>post('/api/v1/payments',{
    memberId:o.memberId,membershipId:null,amountPaise:o.pricePaise,method:methods[o.id]??'UPI',
    note:'Verified renewal order #'+o.id,renewalOrderId:o.id
  }),onSuccess:()=>{setActivePayment(null);void qc.invalidateQueries({queryKey:['renewal-orders']});void qc.invalidateQueries({queryKey:['payments']});void qc.invalidateQueries({queryKey:['dash-summary']});void qc.invalidateQueries({queryKey:['me-ms']});}});
  const cancel=useMutation({mutationFn:(id:number)=>post('/api/v1/renewal-orders/'+id+'/cancel',{}),onSuccess:()=>qc.invalidateQueries({queryKey:['renewal-orders']})});
  return <>
    <PageHeader title="Renewal order queue" actions={<span className="badge info"><CreditCard size={13}/> Payment-gated renewals</span>}/>
    <div className="recovery-hero"><div><span className="eyebrow">FRONT DESK WORKFLOW</span><h2>Verify payment before extending.</h2><p className="muted">Only confirm after checking the cash receipt or payment provider. Successful recording creates the renewal exactly once.</p></div><div className="recovery-icon"><CreditCard size={24}/></div></div>
    <FormError error={record.error??cancel.error}/>
    <QueryState q={q} isEmpty={list=>list.length===0} empty={<Empty title="No renewal orders" hint="Member renewal requests will appear here."/>}>
      {list=><div className="recovery-list">{list.map(o=><article className="recovery-card" key={o.id}><div className="recovery-person"><div className="recovery-avatar"><RefreshCw size={17}/></div><div><strong>{o.memberName}</strong><span>{o.memberCode} · {o.planName}</span><small>Order #{o.id} · {o.durationDays} days · {formatDateTime(o.createdAt)}</small></div></div><div className="addon-order-total"><strong>{formatMoney(o.pricePaise)}</strong><StatusBadge status={o.status}/></div>
        {o.status==='PAYMENT_PENDING'&&can('canRecordPayments')&&<div className="addon-order-controls"><label>Payment method<select value={methods[o.id]??'UPI'} onChange={e=>setMethods(v=>({...v,[o.id]:e.target.value}))}>{METHODS.map(m=><option key={m} value={m}>{titleCase(m)}</option>)}</select></label><button className="btn small primary" disabled={record.isPending&&activePayment===o.id} onClick={()=>{if(window.confirm('Confirm '+formatMoney(o.pricePaise)+' was received and verified for '+o.memberName+'?')){setActivePayment(o.id);record.mutate(o);}}}><CheckCircle2 size={14}/> Confirm payment</button>{can('canManageMemberships')&&<button className="btn small ghost" disabled={cancel.isPending} onClick={()=>cancel.mutate(o.id)}>Cancel request</button>}</div>}
        {o.status==='PAYMENT_PENDING'&&!can('canRecordPayments')&&<p className="muted">Awaiting verified payment.</p>}
        {o.status==='PAID'&&<p className="muted">Membership #{o.paidMembershipId} created after payment verification.</p>}
      </article>)}</div>}
    </QueryState>
  </>;
}
