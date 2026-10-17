import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { get, post, put, qs } from '../api/client';
import type { AuditEntry, LinkResponse, Page, Settings, StaffPermissions, StaffUser } from '../api/types';
import { formatDateTime, titleCase } from '../lib/format';
import { validatePassword } from '../lib/validation';
import { Empty, Field, FormError, Modal, PageHeader, Pager, QueryState, Spinner, StatusBadge } from '../components/ui';

const PERMS: [keyof StaffPermissions, string][] = [
  ['canRecordPayments', 'Record payments & view receipts'],
  ['canManageMemberships', 'Assign, renew & hold memberships'],
  ['canEditMembers', 'Add & edit members'],
  ['canViewReports', 'View reports'],
];

export function StaffPage() {
  const qc = useQueryClient();
  const [creating, setCreating] = useState(false);
  const [link, setLink] = useState<LinkResponse | null>(null);
  const q = useQuery({ queryKey: ['staff'], queryFn: () => get<StaffUser[]>('/api/v1/staff') });
  const refresh = () => qc.invalidateQueries({ queryKey: ['staff'] });
  const perms = useMutation({ mutationFn: (a: { id: number; p: StaffPermissions }) => put(`/api/v1/staff/${a.id}/permissions`, a.p), onSuccess: refresh });
  const status = useMutation({ mutationFn: (a: { id: number; status: string }) => post(`/api/v1/staff/${a.id}/status`, { status: a.status }), onSuccess: refresh });
  const reset = useMutation({ mutationFn: (id: number) => post<LinkResponse>(`/api/v1/users/${id}/reset-link`, {}), onSuccess: setLink });
  return (
    <>
      <PageHeader title="Staff" actions={<button className="btn primary" onClick={() => setCreating(true)}>Add staff</button>} />
      <FormError error={perms.error ?? status.error ?? reset.error} />
      {link && (
        <div className="linkbox"><p>One-time reset link for <strong>{link.loginId}</strong> (expires {formatDateTime(link.expiresAt)}):</p>
          <input readOnly aria-label="Reset link" value={link.url} onFocus={(e) => e.target.select()} /></div>
      )}
      <QueryState q={q} isEmpty={(l) => l.length === 0} empty={<Empty title="No staff accounts" hint="Add a staff account for reception." />}>
        {(list) => (
          <ul className="list">
            {list.map((s) => (
              <li key={s.id} className="card">
                <div className="row">
                  <div><strong>{s.loginId}</strong> <StatusBadge status={s.status} />
                    <div className="muted">Last login {s.lastLoginAt ? formatDateTime(s.lastLoginAt) : 'never'}</div></div>
                  <div className="btn-row">
                    <button className="btn small" onClick={() => reset.mutate(s.id)}>Reset link</button>
                    <button className="btn small ghost" onClick={() => status.mutate({ id: s.id, status: s.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE' })}>
                      {s.status === 'ACTIVE' ? 'Disable' : 'Enable'}
                    </button>
                  </div>
                </div>
                <fieldset className="perms"><legend>Permissions</legend>
                  {PERMS.map(([k, label]) => (
                    <label key={k} className="check">
                      <input type="checkbox" checked={s.permissions[k]} disabled={perms.isPending}
                        onChange={(e) => perms.mutate({ id: s.id, p: { ...s.permissions, [k]: e.target.checked } })} /> {label}
                    </label>
                  ))}
                </fieldset>
              </li>
            ))}
          </ul>
        )}
      </QueryState>
      {creating && <CreateStaffModal onClose={() => { setCreating(false); void refresh(); }} />}
    </>
  );
}

function CreateStaffModal({ onClose }: { onClose: () => void }) {
  const [loginId, setLoginId] = useState('');
  const [pw, setPw] = useState('');
  const [p, setP] = useState<StaffPermissions>({ canRecordPayments: false, canManageMemberships: false, canEditMembers: false, canViewReports: false });
  const [errs, setErrs] = useState<{ loginId?: string; pw?: string }>({});
  const m = useMutation({ mutationFn: () => post('/api/v1/staff', { loginId: loginId.trim(), temporaryPassword: pw, permissions: p }), onSuccess: onClose });
  return (
    <Modal title="Add staff account" onClose={onClose}>
      <form noValidate onSubmit={(e) => {
        e.preventDefault();
        const er = { loginId: loginId.trim().length >= 3 ? undefined : 'Login ID must be at least 3 characters.', pw: validatePassword(pw) ?? undefined };
        setErrs(er);
        if (!er.loginId && !er.pw) m.mutate();
      }}>
        <Field label="Login ID" htmlFor="st-login" error={errs.loginId}><input id="st-login" autoCapitalize="none" value={loginId} onChange={(e) => setLoginId(e.target.value)} /></Field>
        <Field label="Temporary password" htmlFor="st-pw" error={errs.pw} hint="They must change it at first sign-in."><input id="st-pw" type="password" autoComplete="new-password" value={pw} onChange={(e) => setPw(e.target.value)} /></Field>
        <fieldset className="perms"><legend>Permissions</legend>
          {PERMS.map(([k, label]) => (
            <label key={k} className="check"><input type="checkbox" checked={p[k]} onChange={(e) => setP({ ...p, [k]: e.target.checked })} /> {label}</label>
          ))}
        </fieldset>
        <FormError error={m.error} />
        <div className="modal-actions"><button type="button" className="btn ghost" onClick={onClose}>Cancel</button><button className="btn primary" disabled={m.isPending}>Create</button></div>
      </form>
    </Modal>
  );
}

export function SettingsPage() {
  const qc = useQueryClient();
  const q = useQuery({ queryKey: ['settings'], queryFn: () => get<Settings>('/api/v1/settings') });
  const [form, setForm] = useState<Settings | null>(null);
  const [saved, setSaved] = useState(false);
  const [errs, setErrs] = useState<Record<string, string>>({});
  const m = useMutation({
    mutationFn: (s: Settings) => put<Settings>('/api/v1/settings', s),
    onSuccess: (s) => { setForm(s); setSaved(true); void qc.invalidateQueries({ queryKey: ['settings'] }); void qc.invalidateQueries({ queryKey: ['session'] }); },
  });
  if (q.isLoading) return <Spinner />;
  const s = form ?? q.data;
  return (
    <>
      <PageHeader title="Settings" />
      <QueryState q={q}>
        {() => s && (
          <form className="card" noValidate onSubmit={(e) => {
            e.preventDefault();
            const er: Record<string, string> = {};
            if (!s.gymName.trim()) er.gymName = 'Enter the gym name.';
            if (!(s.duplicateWindowMinutes >= 0 && s.duplicateWindowMinutes <= 1440)) er.dup = 'Between 0 and 1440 minutes.';
            if (!(s.dropOffDays >= 1 && s.dropOffDays <= 365)) er.drop = 'Between 1 and 365 days.';
            if (!(s.expiringSoonDays >= 0 && s.expiringSoonDays <= 90)) er.exp = 'Between 0 and 90 days.';
            setErrs(er); setSaved(false);
            if (Object.keys(er).length === 0) m.mutate(s);
          }}>
            <Field label="Gym name" htmlFor="s-name" error={errs.gymName}><input id="s-name" value={s.gymName} onChange={(e) => setForm({ ...s, gymName: e.target.value })} /></Field>
            <Field label="Time zone" htmlFor="s-tz" hint="IANA name, e.g. Asia/Kolkata. Decides what 'today' means."><input id="s-tz" value={s.timezone} onChange={(e) => setForm({ ...s, timezone: e.target.value })} /></Field>
            <Field label="Duplicate check-in window (minutes)" htmlFor="s-dup" error={errs.dup} hint="A second scan inside this window is shown as already checked in."><input id="s-dup" inputMode="numeric" value={s.duplicateWindowMinutes} onChange={(e) => setForm({ ...s, duplicateWindowMinutes: Number(e.target.value) })} /></Field>
            <Field label="Drop-off threshold (days)" htmlFor="s-drop" error={errs.drop} hint="Members with no visit for this many days appear on the dashboard."><input id="s-drop" inputMode="numeric" value={s.dropOffDays} onChange={(e) => setForm({ ...s, dropOffDays: Number(e.target.value) })} /></Field>
            <Field label="Expiring-soon window (days)" htmlFor="s-exp" error={errs.exp}><input id="s-exp" inputMode="numeric" value={s.expiringSoonDays} onChange={(e) => setForm({ ...s, expiringSoonDays: Number(e.target.value) })} /></Field>
            <label className="check"><input type="checkbox" checked={s.memberCanEditEmail} onChange={(e) => setForm({ ...s, memberCanEditEmail: e.target.checked })} /> Members may edit their own email</label>
            <FormError error={m.error} />
            {saved && <p className="ok" role="status">Settings saved.</p>}
            <button className="btn primary" disabled={m.isPending}>Save settings</button>
          </form>
        )}
      </QueryState>
    </>
  );
}

export function AuditPage() {
  const [page, setPage] = useState(0);
  const [entityType, setEntityType] = useState('');
  const q = useQuery({ queryKey: ['audit', page, entityType], queryFn: () => get<Page<AuditEntry>>('/api/v1/audit' + qs({ page, size: 30, entityType })), placeholderData: (p) => p });
  return (
    <>
      <PageHeader title="Audit log" />
      <p className="muted">An append-only record of sensitive actions. Entries cannot be edited or deleted.</p>
      <div className="filters">
        <select aria-label="Entity" value={entityType} onChange={(e) => { setEntityType(e.target.value); setPage(0); }}>
          <option value="">All</option>
          {['MEMBER', 'MEMBERSHIP', 'PAYMENT', 'PLAN', 'USER', 'GYM_SETTINGS'].map((x) => <option key={x} value={x}>{titleCase(x)}</option>)}
        </select>
      </div>
      <QueryState q={q} isEmpty={(p) => p.items.length === 0} empty={<Empty title="No audit entries" />}>
        {(p) => (
          <>
            <ul className="list">
              {p.items.map((a) => (
                <li key={a.id} className="row wrap"><div><strong>{titleCase(a.action)}</strong> <span className="muted">{a.entityType}{a.entityId ? ` #${a.entityId}` : ''}</span>
                  <div className="muted">{a.actorLogin ?? 'system'} · {formatDateTime(a.at)}</div>
                  {a.details != null && <code className="details">{JSON.stringify(a.details)}</code>}</div></li>
              ))}
            </ul>
            <Pager page={p.page} totalPages={p.totalPages} onPage={setPage} />
          </>
        )}
      </QueryState>
    </>
  );
}
