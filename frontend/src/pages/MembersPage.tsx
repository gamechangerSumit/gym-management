import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiError, get, post, put as api_put, qs } from '../api/client';
import type { MemberSummary, Page } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { formatDate } from '../lib/format';
import { compact, validateEmail, validateName, validatePhone } from '../lib/validation';
import { Empty, Field, FormError, Modal, PageHeader, Pager, QueryState, StatusBadge, useDebounced } from '../components/ui';

export function MembersPage() {
  const { can } = useAuth();
  const [q, setQ] = useState('');
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const [creating, setCreating] = useState(false);
  const dq = useDebounced(q.trim());
  const query = useQuery({
    queryKey: ['members', dq, status, page],
    queryFn: () => get<Page<MemberSummary>>('/api/v1/members' + qs({ q: dq, status, page, size: 20 })),
    placeholderData: (prev) => prev,
  });
  return (
    <>
      <PageHeader title="Members" actions={can('canEditMembers') && <button className="btn primary" onClick={() => setCreating(true)}>Add member</button>} />
      <div className="filters">
        <input aria-label="Search members" placeholder="Search name, phone or code" value={q} onChange={(e) => { setQ(e.target.value); setPage(0); }} />
        <select aria-label="Status" value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }}>
          <option value="">All</option><option value="ACTIVE">Active</option><option value="INACTIVE">Inactive</option>
        </select>
      </div>
      <QueryState q={query} isEmpty={(p) => p.items.length === 0}
        empty={<Empty title={dq ? 'No members match your search' : 'No members yet'} hint={dq ? 'Try a different name or number.' : 'Add your first member to get started.'} />}>
        {(p) => (
          <>
            <ul className="list">
              {p.items.map((m) => (
                <li key={m.id} className="row">
                  <div>
                    <Link to={`/members/${m.id}`}><strong>{m.fullName}</strong></Link>
                    <div className="muted">{m.memberCode} · {m.phone}</div>
                    <small className="muted">{m.planName ? `${m.planName} · ends ${formatDate(m.membershipEndDate)}` : 'No membership'}</small>
                  </div>
                  <div className="stack-right">
                    <StatusBadge status={m.membershipStatus} />
                    {m.status === 'INACTIVE' && <StatusBadge status="INACTIVE" />}
                  </div>
                </li>
              ))}
            </ul>
            <Pager page={p.page} totalPages={p.totalPages} onPage={setPage} />
          </>
        )}
      </QueryState>
      {creating && <MemberFormModal onClose={() => setCreating(false)} />}
    </>
  );
}

export interface MemberFormValues {
  fullName: string; phone: string; email: string; emergencyContact: string; staffNotes: string;
}

export function MemberFormModal({ onClose, existing, memberId }: {
  onClose: () => void; existing?: MemberFormValues; memberId?: number;
}) {
  const qc = useQueryClient();
  const nav = useNavigate();
  const [v, setV] = useState<MemberFormValues>(existing ?? { fullName: '', phone: '', email: '', emergencyContact: '', staffNotes: '' });
  const [errs, setErrs] = useState<Partial<Record<keyof MemberFormValues, string>>>({});
  const [similar, setSimilar] = useState<string | null>(null);
  const set = (k: keyof MemberFormValues) => (e: { target: { value: string } }) => setV({ ...v, [k]: e.target.value });

  const m = useMutation({
    mutationFn: async (confirmSimilar: boolean) => {
      const body = {
        fullName: v.fullName.trim(), phone: v.phone.trim(), email: v.email.trim() || null,
        emergencyContact: v.emergencyContact.trim() || null, staffNotes: v.staffNotes.trim() || null, confirmSimilar,
      };
      if (memberId) {
        await api_put(`/api/v1/members/${memberId}`, body);
        return memberId;
      }
      const r = await post<{ id: number }>('/api/v1/members', body);
      return r.id;
    },
    onSuccess: (id) => {
      void qc.invalidateQueries({ queryKey: ['members'] });
      void qc.invalidateQueries({ queryKey: ['member', id] });
      onClose();
      if (!memberId) nav(`/members/${id}`);
    },
    onError: (e) => {
      if (e instanceof ApiError && e.code === 'SIMILAR_MEMBER') setSimilar(e.message);
      else if (e instanceof ApiError && Object.keys(e.fieldErrors).length) setErrs(e.fieldErrors as typeof errs);
    },
  });

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const found = compact({
      fullName: validateName(v.fullName) ?? undefined, phone: validatePhone(v.phone) ?? undefined,
      email: validateEmail(v.email.trim()) ?? undefined,
    });
    setErrs(found);
    setSimilar(null);
    if (Object.keys(found).length === 0) m.mutate(false);
  };

  return (
    <Modal title={memberId ? 'Edit member' : 'Add member'} onClose={onClose}>
      <form onSubmit={submit} noValidate>
        <Field label="Full name" htmlFor="mf-name" error={errs.fullName}><input id="mf-name" value={v.fullName} onChange={set('fullName')} /></Field>
        <Field label="Phone" htmlFor="mf-phone" error={errs.phone} hint="10-digit mobile number"><input id="mf-phone" inputMode="tel" value={v.phone} onChange={set('phone')} /></Field>
        <Field label="Email (optional)" htmlFor="mf-email" error={errs.email}><input id="mf-email" type="email" value={v.email} onChange={set('email')} /></Field>
        <Field label="Emergency contact (optional)" htmlFor="mf-em"><input id="mf-em" value={v.emergencyContact} onChange={set('emergencyContact')} /></Field>
        <Field label="Staff notes (never shown to the member)" htmlFor="mf-notes"><textarea id="mf-notes" rows={3} value={v.staffNotes} onChange={set('staffNotes')} /></Field>
        {similar && (
          <div className="form-warn" role="alert">
            <p>{similar}</p>
            <button type="button" className="btn" onClick={() => m.mutate(true)}>This is a different person — save anyway</button>
          </div>
        )}
        {!similar && <FormError error={m.error} />}
        <div className="modal-actions">
          <button type="button" className="btn ghost" onClick={onClose}>Cancel</button>
          <button className="btn primary" disabled={m.isPending}>{m.isPending ? 'Saving…' : 'Save'}</button>
        </div>
      </form>
    </Modal>
  );
}
