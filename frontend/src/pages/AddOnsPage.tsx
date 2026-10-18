import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { CheckCircle2, ClipboardList, Dumbbell, Package, Plus, ShoppingBag, Utensils } from 'lucide-react';
import { get, post } from '../api/client';
import type { AddOn, AddOnOrder } from '../api/types';
import { formatDateTime, formatMoney, titleCase } from '../lib/format';
import { useAuth } from '../auth/AuthContext';
import { Empty, FormError, PageHeader, QueryState, StatusBadge } from '../components/ui';

const KINDS = ['PT', 'DIET', 'PRODUCT'] as const;
const METHODS = ['CASH', 'UPI', 'CARD', 'BANK_TRANSFER', 'OTHER'];

function kindIcon(kind: string) {
  if (kind === 'PT') return <Dumbbell size={18}/>;
  if (kind === 'DIET') return <Utensils size={18}/>;
  return <Package size={18}/>;
}

export function AddOnsPage() {
  const { isOwner, can } = useAuth();
  const qc = useQueryClient();
  const [kind, setKind] = useState<'PT'|'DIET'|'PRODUCT'>('PT');
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [price, setPrice] = useState('');
  const [validity, setValidity] = useState('');
  const [stock, setStock] = useState('10');
  const [sessions, setSessions] = useState('8');
  const [createError, setCreateError] = useState<string>();
  const [paymentMethods, setPaymentMethods] = useState<Record<number,string>>({});
  const [payingOrder, setPayingOrder] = useState<number|null>(null);
  const catalog = useQuery({ queryKey:['addons',isOwner], queryFn:()=>get<AddOn[]>(isOwner?'/api/v1/admin/addons':'/api/v1/addons') });
  const orders = useQuery({ queryKey:['addon-orders'], queryFn:()=>get<AddOnOrder[]>('/api/v1/addon-orders') });
  const create = useMutation({
    mutationFn:()=>post<AddOn>('/api/v1/addons',{
      kind,name:name.trim(),description:description.trim()||null,pricePaise:Math.round(Number(price)*100),
      validityDays:validity===''?null:Number(validity),stockOnHand:kind==='PRODUCT'?Number(stock):null,
      sessionsIncluded:kind==='PT'?Number(sessions):null,active:true
    }),
    onSuccess:()=>{setName('');setDescription('');setPrice('');setValidity('');setCreateError(undefined);void qc.invalidateQueries({queryKey:['addons']});},
  });
  const recordPayment = useMutation({
    mutationFn:(o:AddOnOrder)=>post('/api/v1/payments',{
      memberId:o.memberId,membershipId:null,amountPaise:o.totalPaise,method:paymentMethods[o.id]??'UPI',
      note:'Add-on order #' + o.id + ': ' + o.addonName,addonOrderId:o.id
    }),
    onSuccess:()=>{setPayingOrder(null);void qc.invalidateQueries({queryKey:['addon-orders']});void qc.invalidateQueries({queryKey:['payments']});void qc.invalidateQueries({queryKey:['dash-summary']});}
  });
  const fulfill = useMutation({mutationFn:(id:number)=>post<AddOnOrder>('/api/v1/addon-orders/'+id+'/fulfill',{}),onSuccess:()=>qc.invalidateQueries({queryKey:['addon-orders']})});
  const session = useMutation({mutationFn:(id:number)=>post<AddOnOrder>('/api/v1/addon-orders/'+id+'/session',{}),onSuccess:()=>qc.invalidateQueries({queryKey:['addon-orders']})});
  const cancel = useMutation({mutationFn:(id:number)=>post('/api/v1/addon-orders/'+id+'/cancel',{}),onSuccess:()=>{void qc.invalidateQueries({queryKey:['addon-orders']});void qc.invalidateQueries({queryKey:['addons']});}});
  const submit = (e:FormEvent) => {
    e.preventDefault();setCreateError(undefined);
    if(!name.trim()){setCreateError('Enter an add-on name.');return;}
    if(!Number.isFinite(Number(price))||Number(price)<=0){setCreateError('Enter a valid price in rupees.');return;}
    if(kind==='PRODUCT'&&(!Number.isInteger(Number(stock))||Number(stock)<0)){setCreateError('Enter a non-negative stock count.');return;}
    if(kind==='PT'&&(!Number.isInteger(Number(sessions))||Number(sessions)<1)){setCreateError('Enter at least one PT session.');return;}
    create.mutate();
  };
  return <>
    <PageHeader title="Add-ons & fulfilment" actions={<span className="badge info"><ShoppingBag size={13}/> PT · Diet · Products</span>}/>
    <div className="recovery-hero"><div><span className="eyebrow">MEMBER SERVICES</span><h2>Transparent offers. Track every order.</h2><p className="muted">Orders stay payment-pending until staff records a received payment. Stock and PT usage are tracked.</p></div><div className="recovery-icon"><ShoppingBag size={24}/></div></div>
    {isOwner&&<section className="panel addon-create-panel">
      <div className="section-heading"><div><span className="eyebrow">OWNER TOOLS</span><h2><Plus size={19}/> Add catalogue item</h2><p className="muted">Publish services with a clear price, validity and availability.</p></div></div>
      <form className="addon-form" onSubmit={submit}>
        <label>Type<select value={kind} onChange={e=>setKind(e.target.value as typeof kind)}>{KINDS.map(k=><option key={k} value={k}>{titleCase(k)}</option>)}</select></label>
        <label>Name<input value={name} maxLength={120} onChange={e=>setName(e.target.value)} placeholder={kind==='PT'?'8-session PT package':kind==='DIET'?'Nutrition plan':'Protein product'}/></label>
        <label>Price (₹)<input type="number" min="0.01" step="0.01" value={price} onChange={e=>setPrice(e.target.value)} placeholder="e.g. 2500"/></label>
        <label>Validity (days, optional)<input type="number" min="1" max="3650" value={validity} onChange={e=>setValidity(e.target.value)}/></label>
        {kind==='PRODUCT'&&<label>Stock on hand<input type="number" min="0" step="1" value={stock} onChange={e=>setStock(e.target.value)}/></label>}
        {kind==='PT'&&<label>Included sessions<input type="number" min="1" max="500" step="1" value={sessions} onChange={e=>setSessions(e.target.value)}/></label>}
        <label className="addon-description">Description<input value={description} maxLength={500} onChange={e=>setDescription(e.target.value)} placeholder="What's included, terms and exclusions"/></label>
        <FormError error={create.error}/>{createError&&<p className="field-error">{createError}</p>}
        <button className="btn primary" disabled={create.isPending}><Plus size={15}/> {create.isPending?'Saving…':'Add item'}</button>
      </form>
    </section>}
    <section className="panel">
      <div className="section-heading"><div><span className="eyebrow">CATALOGUE</span><h2>Available add-ons</h2></div></div>
      <QueryState q={catalog} isEmpty={(a)=>a.length===0} empty={<Empty title="No add-ons yet" hint={isOwner?'Create PT, diet or product items above.':'Ask the owner to publish available add-ons.'}/>}>
        {(items)=><div className="addon-grid">{items.map(a=><article className="addon-tile" key={a.id}><div className="addon-icon">{kindIcon(a.kind)}</div><div className="addon-title"><strong>{a.name}</strong><span className="badge info">{titleCase(a.kind)}</span></div><p>{a.description||'Ask the front desk for details and terms.'}</p><strong className="addon-price">{formatMoney(a.pricePaise)}</strong><small>{a.validityDays?a.validityDays+' days validity · ':''}{a.kind==='PRODUCT'?(a.stockOnHand??0)+' in stock':a.kind==='PT'?(a.sessionsIncluded+' sessions'):'Digital/service add-on'}</small></article>)}</div>}
      </QueryState>
    </section>
    <section className="panel">
      <div className="section-heading"><div><span className="eyebrow">ORDER QUEUE</span><h2><ClipboardList size={19}/> Add-on orders</h2><p className="muted">Record payment only after the money is actually received and verified.</p></div></div>
      <FormError error={recordPayment.error??fulfill.error??session.error??cancel.error}/>
      <QueryState q={orders} isEmpty={(o)=>o.length===0} empty={<Empty title="No add-on orders" hint="Member requests will appear here."/>}>
        {(list)=><div className="recovery-list">{list.map(o=><article className="recovery-card addon-order-card" key={o.id}>
          <div className="recovery-person"><div className="recovery-avatar">{kindIcon(o.kind)}</div><div><strong>{o.addonName}</strong><span>{o.memberName} · {o.memberCode}</span><small>Order #{o.id} · {formatDateTime(o.createdAt)} · Qty {o.quantity}</small>{o.kind==='PT'&&<small>Sessions: {o.sessionsUsed}/{o.sessionsIncluded??0}</small>}</div></div>
          <div className="addon-order-total"><strong>{formatMoney(o.totalPaise)}</strong><StatusBadge status={o.status}/></div>
          {o.status==='PAYMENT_PENDING'&&can('canRecordPayments')&&<div className="addon-order-controls"><label>Payment method<select value={paymentMethods[o.id]??'UPI'} onChange={e=>setPaymentMethods(v=>({...v,[o.id]:e.target.value}))}>{METHODS.map(m=><option key={m} value={m}>{titleCase(m)}</option>)}</select></label><button className="btn small primary" disabled={recordPayment.isPending&&payingOrder===o.id} onClick={()=>{if(window.confirm('Confirm you have received '+formatMoney(o.totalPaise)+' for order #'+o.id+'?')){setPayingOrder(o.id);recordPayment.mutate(o)}}}><CheckCircle2 size={14}/> Confirm received</button><button className="btn small ghost" disabled={cancel.isPending} onClick={()=>cancel.mutate(o.id)}>Cancel order</button></div>}
          {o.status==='PAYMENT_PENDING'&&!can('canRecordPayments')&&<p className="muted">Awaiting payment at the front desk.</p>}
          {o.status==='PAID'&&o.kind!=='PT'&&<button className="btn small primary" disabled={fulfill.isPending} onClick={()=>fulfill.mutate(o.id)}>Mark fulfilled</button>}
          {(o.status==='PAID'||o.status==='IN_PROGRESS')&&o.kind==='PT'&&<button className="btn small primary" disabled={session.isPending} onClick={()=>session.mutate(o.id)}>Record PT session</button>}
          {o.status==='FULFILLED'&&<span className="badge good">Fulfilled {o.fulfilledAt?formatDateTime(o.fulfilledAt):''}</span>}
        </article>)}</div>}
      </QueryState>
    </section>
  </>;
}

export function MemberAddOnsPage() {
  const qc=useQueryClient();
  const [quantity,setQuantity]=useState<Record<number,number>>({});
  const catalog=useQuery({queryKey:['member-addons'],queryFn:()=>get<AddOn[]>('/api/v1/addons')});
  const orders=useQuery({queryKey:['me-addon-orders'],queryFn:()=>get<AddOnOrder[]>('/api/v1/me/addon-orders')});
  const create=useMutation({mutationFn:(a:AddOn)=>post<AddOnOrder>('/api/v1/me/addon-orders',{addonId:a.id,quantity:quantity[a.id]??1}),onSuccess:()=>{void qc.invalidateQueries({queryKey:['me-addon-orders']});void qc.invalidateQueries({queryKey:['member-addons']});}});
  const cancel=useMutation({mutationFn:(id:number)=>post('/api/v1/me/addon-orders/'+id+'/cancel',{}),onSuccess:()=>{void qc.invalidateQueries({queryKey:['me-addon-orders']});void qc.invalidateQueries({queryKey:['member-addons']});}});
  return <>
    <PageHeader title="Add-on marketplace"/>
    <div className="recovery-hero"><div><span className="eyebrow">YOUR OPTIONS</span><h2>Choose what works for you.</h2><p className="muted">Prices and validity are shown before you request an order. Nothing is pre-selected.</p></div><div className="recovery-icon"><ShoppingBag size={24}/></div></div>
    <FormError error={create.error??cancel.error}/>
    <QueryState q={catalog} isEmpty={(a)=>a.length===0} empty={<Empty title="No add-ons available" hint="Check back later or ask the front desk."/>}>
      {(items)=><div className="addon-grid">{items.map(a=><article className="addon-tile" key={a.id}><div className="addon-icon">{kindIcon(a.kind)}</div><div className="addon-title"><strong>{a.name}</strong><span className="badge info">{titleCase(a.kind)}</span></div><p>{a.description||'Ask reception for details and terms.'}</p><strong className="addon-price">{formatMoney(a.pricePaise)}</strong><small>{a.validityDays?a.validityDays+' days validity · ':''}{a.kind==='PRODUCT'?(a.stockOnHand??0)+' in stock':a.kind==='PT'?(a.sessionsIncluded+' sessions'):'Service add-on'}</small><label>Quantity<select value={quantity[a.id]??1} onChange={e=>setQuantity(v=>({...v,[a.id]:Number(e.target.value)}))}>{Array.from({length:Math.min(5,a.kind==='PRODUCT'?(a.stockOnHand??0):5)},(_,i)=>i+1).map(n=><option key={n} value={n}>{n}</option>)}</select></label><button className="btn primary" disabled={create.isPending||(a.kind==='PRODUCT'&&(a.stockOnHand??0)<1)} onClick={()=>create.mutate(a)}><ShoppingBag size={14}/> Request order</button></article>)}</div>}
    </QueryState>
    <section className="panel"><div className="section-heading"><div><span className="eyebrow">YOUR ORDERS</span><h2>Order history</h2></div></div><QueryState q={orders} isEmpty={(o)=>o.length===0} empty={<Empty title="No orders yet"/>}>{(list)=><div className="recovery-list">{list.map(o=><article className="recovery-card" key={o.id}><div className="recovery-person"><div className="recovery-avatar">{kindIcon(o.kind)}</div><div><strong>{o.addonName}</strong><span>Order #{o.id} · Qty {o.quantity}</span><small>{formatDateTime(o.createdAt)}</small></div></div><div className="addon-order-total"><strong>{formatMoney(o.totalPaise)}</strong><StatusBadge status={o.status}/></div>{o.status==='PAYMENT_PENDING'&&<button className="btn small ghost" disabled={cancel.isPending} onClick={()=>cancel.mutate(o.id)}>Cancel request</button>}{o.kind==='PT'&&o.status!=='PAYMENT_PENDING'&&<small>Sessions used: {o.sessionsUsed}/{o.sessionsIncluded??0}</small>}</article>)}</div>}</QueryState></section>
  </>;
}
