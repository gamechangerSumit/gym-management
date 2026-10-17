import { useState } from 'react';
import { ArrowLeft, CreditCard, Edit3, KeyRound, QrCode, ShieldCheck } from 'lucide-react';
import { Link, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { QRCodeSVG } from 'qrcode.react';
import { get, post, qs } from '../api/client';
import type { Dues, LinkResponse, MemberDetail, Membership, Page, Payment, Plan, QrPass } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { formatDate, formatDateTime, formatMoney, parseRupeesToPaise, titleCase } from '../lib/format';
import { Empty, ErrorBox, Field, FormError, Modal, QueryState, Spinner, Stat, StatusBadge } from '../components/ui';
import { MemberFormModal } from './MembersPage';

export function MemberDetailPage() {
  const { id } = useParams();
  const memberId = Number(id);
  const { can, isOwner } = useAuth();
  const qc = useQueryClient();
  const [editing, setEditing] = useState(false);
  const [link, setLink] = useState<LinkResponse | null>(null);
  const [dialog, setDialog] = useState<null | 'assign' | 'pay' | 'qr'>(null);
  const member = useQuery({ queryKey: ['member', memberId], queryFn: () => get<MemberDetail>(`/api/v1/members/${memberId}`), enabled: Number.isFinite(memberId) });
  const refresh = () => {
    void qc.invalidateQueries({ queryKey: ['member', memberId] });
    void qc.invalidateQueries({ queryKey: ['memberships', memberId] });
    void qc.invalidateQueries({ queryKey: ['member-payments', memberId] });
    void qc.invalidateQueries({ queryKey: ['dues', memberId] });
  };
  const invite = useMutation({
    mutationFn: () => post<LinkResponse>(`/api/v1/members/${memberId}/invite`, {}),
    onSuccess: (r) => { setLink(r); refresh(); },
  });
  const toggle = useMutation({
    mutationFn: (active: boolean) => post(`/api/v1/members/${memberId}/${active ? 'reactivate' : 'deactivate'}`, active ? {} : { reason: 'Deactivated from member page' }),
    onSuccess: refresh,
  });

  return (
    <QueryState q={member}>
      {(m) => (
        <>
          <Link className="back-link" to="/members"><ArrowLeft size={15}/> Members</Link>
          <div className="member-detail-head">
            <div className="member-detail-title"><div className="detail-avatar">{m.fullName.split(" ").map((x) => x[0]).slice(0,2).join("").toUpperCase()}</div><div><h1>{m.fullName} {m.status === 'INACTIVE' && <StatusBadge status="INACTIVE" />}</h1><div className="muted">{m.memberCode} · {m.phone}{m.email ? ` · ${m.email}` : ""}</div></div></div>
            <div className="actions">
              {can('canEditMembers') && <button className="btn" onClick={() => setEditing(true)}><Edit3 size={15}/> Edit</button>}
              {can('canEditMembers') && (
                <button className="btn ghost" disabled={toggle.isPending}
                  onClick={() => { if (m.status === 'ACTIVE' ? window.confirm('Deactivate this member? They will not be able to check in.') : true) toggle.mutate(m.status !== 'ACTIVE'); }}>
                  {m.status === 'ACTIVE' ? 'Deactivate' : 'Reactivate'}
                </button>
              )}
            </div>
          </div>
          <FormError error={toggle.error} />
          <section className="stats detail-stats">
            <Stat label="Membership" value={<StatusBadge status={m.membershipStatus} />} hint={m.planName ? `${m.planName} · ends ${formatDate(m.membershipEndDate)}` : undefined} />
            {m.duesPaise !== null && <Stat label="Dues" value={formatMoney(m.duesPaise)} />}
            <Stat label="Joined" value={formatDate(m.joinDate)} />
          </section>

          {isOwner && (
            <div className="detail-login-card"><div className="detail-login-icon"><KeyRound size={18}/></div><div><h2>Member login</h2>
              {m.hasAccount ? <p>Login ID <strong>{m.accountLoginId}</strong> · {titleCase(m.accountStatus)}</p> : <p className="muted">No login yet.</p>}
              {(!m.hasAccount || m.accountStatus === 'INVITED') && (
                <button className="btn" disabled={invite.isPending} onClick={() => invite.mutate()}>
                  {m.hasAccount ? 'Create a new invite link' : 'Create invite link'}
                </button>
              )}
              <FormError error={invite.error} />
              {link && (
                <div className="linkbox">
                  <p>Share this one-time link with the member (it is shown only now and expires {formatDateTime(link.expiresAt)}):</p>
                  <input readOnly aria-label="Invite link" value={link.url} onFocus={(e) => e.target.select()} />
                  <button className="btn small" onClick={() => void navigator.clipboard?.writeText(link.url)}>Copy</button>
                </div>
              )}
            </div></div>
          )}

          <div className="detail-actions">
            {can('canManageMemberships') && m.status === 'ACTIVE' && <button className="btn primary" onClick={() => setDialog('assign')}><ShieldCheck size={15}/> Assign membership</button>}
            {can('canRecordPayments') && <button className="btn primary" onClick={() => setDialog('pay')}><CreditCard size={15}/> Record payment</button>}
            <button className="btn" onClick={() => setDialog('qr')}><QrCode size={15}/> QR pass</button>
          </div>

          {m.staffNotes && (<div className="card detail-notes"><h2>Staff notes</h2><p style={{ whiteSpace: 'pre-wrap' }}>{m.staffNotes}</p></div>)}

          <Memberships memberId={memberId} onChange={refresh} />
          {can('canRecordPayments') && <MemberPayments memberId={memberId} onChange={refresh} />}

          {editing && (
            <MemberFormModal memberId={memberId} onClose={() => setEditing(false)}
              existing={{ fullName: m.fullName, phone: m.phone, email: m.email ?? '', emergencyContact: m.emergencyContact ?? '', staffNotes: m.staffNotes ?? '' }} />
          )}
          {dialog === 'assign' && <AssignModal memberId={memberId} onClose={() => { setDialog(null); refresh(); }} />}
          {dialog === 'pay' && <PaymentModal memberId={memberId} onClose={() => { setDialog(null); refresh(); }} />}
          {dialog === 'qr' && <QrModal memberId={memberId} name={m.fullName} onClose={() => setDialog(null)} />}
        </>
      )}
    </QueryState>
  );
}

function Memberships({ memberId, onChange }: { memberId: number; onChange: () => void }) {
  const { can, isOwner } = useAuth();
  const q = useQuery({ queryKey: ['memberships', memberId], queryFn: () => get<Membership[]>(`/api/v1/members/${memberId}/memberships`) });
  const act = useMutation({
    mutationFn: (a: { id: number; op: 'renew' | 'hold' | 'resume' | 'cancel'; reason?: string }) =>
      post(`/api/v1/memberships/${a.id}/${a.op}`, a.reason ? { reason: a.reason } : {}),
    onSuccess: onChange,
  });
  const ask = (id: number, op: 'hold' | 'cancel') => {
    const reason = window.prompt(op === 'hold' ? 'Reason for putting this membership on hold?' : 'Reason for cancelling this membership?');
    if (reason && reason.trim()) act.mutate({ id, op, reason: reason.trim() });
  };
  return (
    <>
      <h2>Memberships</h2>
      <FormError error={act.error} />
      <QueryState q={q} isEmpty={(l) => l.length === 0} empty={<Empty title="No memberships yet" hint="Assign a plan to start one." />}>
        {(list) => (
          <ul className="list">
            {list.map((ms) => (
              <li key={ms.id} className="row">
                <div>
                  <strong>{ms.planName}</strong> <StatusBadge status={ms.status} />
                  <div className="muted">{formatDate(ms.startDate)} → {formatDate(ms.endDate)} · {formatMoney(ms.pricePaise)}</div>
                  {ms.holdReason && <small className="muted">Hold: {ms.holdReason}</small>}
                  {ms.cancelReason && <small className="muted">Cancelled: {ms.cancelReason}</small>}
                </div>
                {can('canManageMemberships') && ms.storedStatus !== 'CANCELLED' && (
                  <div className="btn-row">
                    {ms.storedStatus === 'ACTIVE' && (ms.status === 'EXPIRING_SOON' || ms.status === 'EXPIRED' || ms.status === 'ACTIVE') && (
                      <button className="btn small" disabled={act.isPending} onClick={() => act.mutate({ id: ms.id, op: 'renew' })}>Renew</button>
                    )}
                    {ms.storedStatus === 'ACTIVE' && ms.status !== 'EXPIRED' && <button className="btn small ghost" onClick={() => ask(ms.id, 'hold')}>Hold</button>}
                    {ms.storedStatus === 'ON_HOLD' && <button className="btn small" onClick={() => act.mutate({ id: ms.id, op: 'resume' })}>Resume</button>}
                    {isOwner && <button className="btn small ghost danger" onClick={() => ask(ms.id, 'cancel')}>Cancel</button>}
                  </div>
                )}
              </li>
            ))}
          </ul>
        )}
      </QueryState>
    </>
  );
}

function MemberPayments({ memberId, onChange }: { memberId: number; onChange: () => void }) {
  const { isOwner } = useAuth();
  const dues = useQuery({ queryKey: ['dues', memberId], queryFn: () => get<Dues>(`/api/v1/members/${memberId}/dues`) });
  const q = useQuery({ queryKey: ['member-payments', memberId], queryFn: () => get<Page<Payment>>('/api/v1/payments' + qs({ memberId, size: 50 })) });
  const voidIt = useMutation({
    mutationFn: (a: { id: number; reason: string }) => post(`/api/v1/payments/${a.id}/void`, { reason: a.reason }),
    onSuccess: onChange,
  });
  return (
    <>
      <h2>Payments</h2>
      {dues.data && (
        <p className="muted">Billed {formatMoney(dues.data.billedPaise)} · Paid {formatMoney(dues.data.paidPaise)} · <strong>Dues {formatMoney(dues.data.duesPaise)}</strong></p>
      )}
      <FormError error={voidIt.error} />
      <QueryState q={q} isEmpty={(p) => p.items.length === 0} empty={<Empty title="No payments recorded" />}>
        {(p) => (
          <ul className="list">
            {p.items.map((pay) => (
              <li key={pay.id} className="row">
                <div>
                  <strong>{formatMoney(pay.amountPaise)}</strong> <StatusBadge status={pay.status} />
                  <div className="muted">{formatDateTime(pay.paidAt)} · {titleCase(pay.method)} · {pay.receiptNo}</div>
                  {pay.voidReason && <small className="muted">Void reason: {pay.voidReason}</small>}
                </div>
                <div className="btn-row">
                  <a className="btn small" href={`/api/v1/payments/${pay.id}/receipt.html`} target="_blank" rel="noreferrer">Receipt</a>
                  {isOwner && pay.status === 'RECORDED' && (
                    <button className="btn small ghost danger" onClick={() => {
                      const reason = window.prompt('Reason for voiding this payment?');
                      if (reason && reason.trim()) voidIt.mutate({ id: pay.id, reason: reason.trim() });
                    }}>Void</button>
                  )}
                </div>
              </li>
            ))}
          </ul>
        )}
      </QueryState>
    </>
  );
}

function AssignModal({ memberId, onClose }: { memberId: number; onClose: () => void }) {
  const plans = useQuery({ queryKey: ['plans'], queryFn: () => get<Plan[]>('/api/v1/plans') });
  const [planId, setPlanId] = useState('');
  const [start, setStart] = useState('');
  const [price, setPrice] = useState('');
  const [err, setErr] = useState<string | undefined>();
  const m = useMutation({
    mutationFn: () => {
      const body: Record<string, unknown> = { planId: Number(planId) };
      if (start) body.startDate = start;
      if (price.trim()) body.pricePaise = parseRupeesToPaise(price);
      return post(`/api/v1/members/${memberId}/memberships`, body);
    },
    onSuccess: onClose,
  });
  const active = (plans.data ?? []).filter((p) => p.active);
  return (
    <Modal title="Assign membership" onClose={onClose}>
      {plans.isLoading ? <Spinner /> : plans.isError ? <ErrorBox error={plans.error} /> : active.length === 0 ? (
        <Empty title="No active plans" hint="Create a plan first (owner)." />
      ) : (
        <form noValidate onSubmit={(e) => {
          e.preventDefault();
          if (!planId) return setErr('Choose a plan.');
          if (price.trim() && parseRupeesToPaise(price) === null) return setErr('Enter a valid price, e.g. 1500 or 1500.50.');
          setErr(undefined); m.mutate();
        }}>
          <Field label="Plan" htmlFor="as-plan" error={err && !planId ? err : undefined}>
            <select id="as-plan" value={planId} onChange={(e) => setPlanId(e.target.value)}>
              <option value="">Select…</option>
              {active.map((p) => <option key={p.id} value={p.id}>{p.name} — {p.durationDays} days — {formatMoney(p.pricePaise)}</option>)}
            </select>
          </Field>
          <Field label="Start date (optional)" htmlFor="as-start" hint="Defaults to today."><input id="as-start" type="date" value={start} onChange={(e) => setStart(e.target.value)} /></Field>
          <Field label="Price override in ₹ (optional)" htmlFor="as-price" error={err && planId ? err : undefined}><input id="as-price" inputMode="decimal" value={price} onChange={(e) => setPrice(e.target.value)} /></Field>
          <FormError error={m.error} />
          <div className="modal-actions"><button type="button" className="btn ghost" onClick={onClose}>Cancel</button><button className="btn primary" disabled={m.isPending}>Assign</button></div>
        </form>
      )}
    </Modal>
  );
}

export function PaymentModal({ memberId, onClose }: { memberId: number; onClose: () => void }) {
  const { data: ms } = useQuery({ queryKey: ['memberships', memberId], queryFn: () => get<Membership[]>(`/api/v1/members/${memberId}/memberships`) });
  const dues = useQuery({ queryKey: ['dues', memberId], queryFn: () => get<Dues>(`/api/v1/members/${memberId}/dues`) });
  const [amount, setAmount] = useState('');
  const [method, setMethod] = useState('CASH');
  const [note, setNote] = useState('');
  const [membershipId, setMembershipId] = useState('');
  const [err, setErr] = useState<string | undefined>();
  const [receiptId, setReceiptId] = useState<number | null>(null);
  const m = useMutation({
    mutationFn: (allowOverpayment: boolean) => post<Payment>('/api/v1/payments', {
      memberId, amountPaise: parseRupeesToPaise(amount), method, note: note.trim() || null,
      membershipId: membershipId ? Number(membershipId) : null, allowOverpayment,
    }),
    onSuccess: (p) => setReceiptId(p.id),
  });
  const overpay = m.error && (m.error as { code?: string }).code === 'OVERPAYMENT';
  if (receiptId !== null) {
    return (
      <Modal title="Payment recorded" onClose={onClose}>
        <p>The payment was saved.</p>
        <div className="modal-actions">
          <a className="btn" href={`/api/v1/payments/${receiptId}/receipt.html`} target="_blank" rel="noreferrer">Open receipt</a>
          <button className="btn primary" onClick={onClose}>Done</button>
        </div>
      </Modal>
    );
  }
  return (
    <Modal title="Record payment" onClose={onClose}>
      {dues.data && <p className="muted">Current dues: <strong>{formatMoney(dues.data.duesPaise)}</strong></p>}
      <form noValidate onSubmit={(e) => {
        e.preventDefault();
        const p = parseRupeesToPaise(amount);
        if (p === null || p <= 0) return setErr('Enter an amount greater than zero, e.g. 1500 or 1500.50.');
        setErr(undefined); m.mutate(false);
      }}>
        <Field label="Amount (₹)" htmlFor="pm-amt" error={err}><input id="pm-amt" inputMode="decimal" value={amount} onChange={(e) => setAmount(e.target.value)} /></Field>
        <Field label="Method" htmlFor="pm-method">
          <select id="pm-method" value={method} onChange={(e) => setMethod(e.target.value)}>
            {['CASH', 'UPI', 'CARD', 'BANK_TRANSFER', 'OTHER'].map((x) => <option key={x} value={x}>{titleCase(x)}</option>)}
          </select>
        </Field>
        {ms && ms.length > 0 && (
          <Field label="For membership (optional)" htmlFor="pm-ms">
            <select id="pm-ms" value={membershipId} onChange={(e) => setMembershipId(e.target.value)}>
              <option value="">General</option>
              {ms.filter((x) => x.storedStatus !== 'CANCELLED').map((x) => <option key={x.id} value={x.id}>{x.planName} · {formatDate(x.startDate)}</option>)}
            </select>
          </Field>
        )}
        <Field label="Note (optional)" htmlFor="pm-note"><input id="pm-note" value={note} maxLength={300} onChange={(e) => setNote(e.target.value)} /></Field>
        <p className="muted small-print">Do not enter card numbers or bank details — they are never stored.</p>
        {overpay ? (
          <div className="form-warn" role="alert">
            <p>{(m.error as Error).message}</p>
            <button type="button" className="btn" onClick={() => m.mutate(true)}>Record as advance anyway</button>
          </div>
        ) : <FormError error={m.error} />}
        <div className="modal-actions"><button type="button" className="btn ghost" onClick={onClose}>Cancel</button><button className="btn primary" disabled={m.isPending}>Record</button></div>
      </form>
    </Modal>
  );
}

function QrModal({ memberId, name, onClose }: { memberId: number; name: string; onClose: () => void }) {
  const qc = useQueryClient();
  const q = useQuery({ queryKey: ['qr', memberId], queryFn: () => get<QrPass>(`/api/v1/members/${memberId}/qr`), gcTime: 0 });
  const reissue = useMutation({
    mutationFn: (passId: number) => post<QrPass>(`/api/v1/qr/${passId}/reissue`, {}),
    onSuccess: (p) => qc.setQueryData(['qr', memberId], p),
  });
  return (
    <Modal title={`QR pass — ${name}`} onClose={onClose}>
      <QueryState q={q}>
        {(p) => (
          <div className="qr-box">
            <QRCodeSVG value={p.payload} size={220} marginSize={2} level="M" />
            <p className="muted small-print">Opening this pass is recorded in the audit log. Reissue it if it was lost or shared.</p>
            <FormError error={reissue.error} />
            <div className="modal-actions">
              <button className="btn" onClick={() => window.print()}>Print</button>
              <button className="btn ghost danger" disabled={reissue.isPending}
                onClick={() => { if (window.confirm('Reissue the pass? The old QR code will stop working immediately.')) reissue.mutate(p.passId); }}>Reissue</button>
            </div>
          </div>
        )}
      </QueryState>
    </Modal>
  );
}
