import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { QRCodeSVG } from 'qrcode.react';
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
      <h1>{profile.data ? `Hi, ${profile.data.fullName.split(' ')[0]}` : 'My Gym'}</h1>
      <QueryState q={ms}>
        {(m) => !m.hasMembership ? (
          <Empty title="No membership yet" hint="Ask the front desk to set up your plan." />
        ) : (
          <>
            <div className="card hero">
              <div className="muted">{m.planName}</div>
              <p className="big"><StatusBadge status={m.status} /></p>
              <p>{m.daysRemaining !== null && m.daysRemaining >= 0 ? `${m.daysRemaining} days left` : 'Membership has ended'} · ends {formatDate(m.endDate)}</p>
            </div>
            <section className="stats">
              <Stat label="Visits this month" value={m.visitsThisMonth} />
              <Stat label="Dues" value={formatMoney(m.duesPaise)} hint={m.duesPaise > 0 ? 'Please pay at the front desk' : 'All paid up'} />
            </section>
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
  return (
    <>
      <h1>My QR pass</h1>
      <QueryState q={q}>
        {(p) => (
          <div className="card qr-box">
            <QRCodeSVG value={p.payload} size={240} marginSize={2} level="M" aria-label="Your gym QR code" />
            <p className="muted">Show this at the front desk. Brightness up helps scanners.</p>
            <p className="muted small-print">Issued {formatDate(p.issuedAt)}. Don't share this code — anyone holding it can check in as you.</p>
            <FormError error={re.error} />
            <button className="btn ghost danger" disabled={re.isPending}
              onClick={() => { if (window.confirm('Get a new QR code? Your current one will stop working immediately.')) re.mutate(); }}>
              Lost or shared it? Get a new code
            </button>
          </div>
        )}
      </QueryState>
    </>
  );
}

export function MyAttendancePage() {
  const [page, setPage] = useState(0);
  const q = useQuery({ queryKey: ['me-att', page], queryFn: () => get<MeAttendancePage>('/api/v1/me/attendance' + qs({ page, size: 20 })), placeholderData: (p) => p });
  return (
    <>
      <h1>My visits</h1>
      <QueryState q={q} isEmpty={(a) => a.totalItems === 0} empty={<Empty title="No visits yet" hint="Your check-ins will show up here." />}>
        {(a) => (
          <>
            <section className="stats"><Stat label="This month" value={a.visitsThisMonth} /></section>
            {a.monthly.length > 0 && (
              <ul className="list compact">{a.monthly.map((m) => <li key={m.month} className="row"><span>{m.month}</span><strong>{m.visits}</strong></li>)}</ul>
            )}
            <h2>History</h2>
            <ul className="list">
              {a.items.map((v) => <li key={v.id} className="row"><span>{formatDateTime(v.checkedInAt)}</span><span className="muted">{v.source === 'QR' ? 'QR' : 'Front desk'}</span></li>)}
            </ul>
            <Pager page={a.page} totalPages={a.totalPages} onPage={setPage} />
          </>
        )}
      </QueryState>
    </>
  );
}

export function MyPaymentsPage() {
  const [page, setPage] = useState(0);
  const [open, setOpen] = useState<number | null>(null);
  const dues = useQuery({ queryKey: ['me-dues'], queryFn: () => get<{ billedPaise: number; paidPaise: number; duesPaise: number }>('/api/v1/me/dues') });
  const q = useQuery({ queryKey: ['me-pay', page], queryFn: () => get<Page<MePayment>>('/api/v1/me/payments' + qs({ page, size: 20 })) });
  return (
    <>
      <h1>Payments</h1>
      {dues.data && (
        <section className="stats">
          <Stat label="Billed" value={formatMoney(dues.data.billedPaise)} />
          <Stat label="Paid" value={formatMoney(dues.data.paidPaise)} />
          <Stat label="Dues" value={formatMoney(dues.data.duesPaise)} />
        </section>
      )}
      <QueryState q={q} isEmpty={(p) => p.items.length === 0} empty={<Empty title="No payments yet" />}>
        {(p) => (
          <>
            <ul className="list">
              {p.items.map((x) => (
                <li key={x.id} className="row">
                  <div><strong>{formatMoney(x.amountPaise)}</strong> <StatusBadge status={x.status} />
                    <div className="muted">{formatDateTime(x.paidAt)} · {titleCase(x.method)}</div></div>
                  <button className="btn small" onClick={() => setOpen(x.id)}>Receipt</button>
                </li>
              ))}
            </ul>
            <Pager page={p.page} totalPages={p.totalPages} onPage={setPage} />
          </>
        )}
      </QueryState>
      {open !== null && <ReceiptModal id={open} onClose={() => setOpen(null)} />}
    </>
  );
}

function ReceiptModal({ id, onClose }: { id: number; onClose: () => void }) {
  const q = useQuery({ queryKey: ['me-receipt', id], queryFn: () => get<Receipt>(`/api/v1/me/payments/${id}/receipt`) });
  return (
    <Modal title="Receipt" onClose={onClose}>
      <QueryState q={q}>
        {(r) => (
          <div className="receipt">
            <h3>{r.gymName}</h3>
            <p>Receipt <strong>{r.receiptNo}</strong> {r.status === 'VOID' && <span className="badge bad">Void</span>}</p>
            <p>{r.paidAtText}</p>
            <p>{r.memberName} ({r.memberCode})</p>
            {r.planName && <p>Plan: {r.planName}</p>}
            <p className="big">{r.amountText}</p>
            <p className="muted">Paid by {titleCase(r.method)}</p>
            {r.voidReason && <p className="muted">Void reason: {r.voidReason}</p>}
            <button className="btn" onClick={() => window.print()}>Print</button>
          </div>
        )}
      </QueryState>
    </Modal>
  );
}

export function MyProfilePage() {
  const qc = useQueryClient();
  const q = useQuery({ queryKey: ['me'], queryFn: () => get<MeProfile>('/api/v1/me') });
  return (
    <>
      <h1>My profile</h1>
      <QueryState q={q}>
        {(p) => <ProfileForm profile={p} onSaved={(np) => qc.setQueryData(['me'], np)} />}
      </QueryState>
      <p className="muted small-print"><a href="/change-password">Change password</a></p>
    </>
  );
}

function ProfileForm({ profile, onSaved }: { profile: MeProfile; onSaved: (p: MeProfile) => void }) {
  const [email, setEmail] = useState(profile.email ?? '');
  const [emergency, setEmergency] = useState(profile.emergencyContact ?? '');
  const [err, setErr] = useState<string | undefined>();
  const [done, setDone] = useState(false);
  const m = useMutation({
    mutationFn: () => patch<MeProfile>('/api/v1/me', profile.canEditEmail ? { email: email.trim() || null, emergencyContact: emergency.trim() || null } : { emergencyContact: emergency.trim() || null }),
    onSuccess: (p) => { onSaved(p); setDone(true); },
  });
  const submit = (e: FormEvent) => {
    e.preventDefault();
    setDone(false);
    const ee = profile.canEditEmail ? validateEmail(email.trim()) ?? undefined : undefined;
    setErr(ee);
    if (!ee) m.mutate();
  };
  return (
    <form className="card" onSubmit={submit} noValidate>
      <p><strong>{profile.fullName}</strong><br /><span className="muted">{profile.memberCode} · {profile.phone} · joined {formatDate(profile.joinDate)}</span></p>
      <p className="muted small-print">To change your name or phone number, please ask the front desk.</p>
      <Field label="Email" htmlFor="me-email" error={err} hint={profile.canEditEmail ? undefined : 'The gym has turned off email changes.'}>
        <input id="me-email" type="email" value={email} disabled={!profile.canEditEmail} onChange={(e) => setEmail(e.target.value)} />
      </Field>
      <Field label="Emergency contact" htmlFor="me-em"><input id="me-em" value={emergency} maxLength={200} onChange={(e) => setEmergency(e.target.value)} /></Field>
      <FormError error={m.error} />
      {done && <p className="ok" role="status">Saved.</p>}
      <button className="btn primary" disabled={m.isPending}>Save</button>
    </form>
  );
}
