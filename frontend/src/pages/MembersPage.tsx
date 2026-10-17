import { useMemo, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Activity, ChevronRight, CircleUserRound, Mail, Phone, Plus, Search, UsersRound } from 'lucide-react';
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
      <PageHeader
        title="Members"
        actions={can('canEditMembers') && (
          <button className="btn primary" onClick={() => setCreating(true)}>
            <Plus size={17} /> Add member
          </button>
        )}
      />

      <section className="member-directory">
        <div className="member-directory-head">
          <div>
            <div className="eyebrow"><UsersRound size={14} /> MEMBER DIRECTORY</div>
            <h2>Everyone in your gym, in one place.</h2>
            <p className="muted">Search members, review membership status, and jump into a profile in one click.</p>
          </div>
          <div className="member-directory-icon"><UsersRound size={28} /></div>
        </div>

        <div className="member-toolbar">
          <div className="member-search">
            <Search size={18} aria-hidden="true" />
            <input
              aria-label="Search members"
              placeholder="Search by name, phone or member code"
              value={q}
              onChange={(e) => { setQ(e.target.value); setPage(0); }}
            />
            {q && <button className="search-clear" type="button" onClick={() => { setQ(''); setPage(0); }} aria-label="Clear search">×</button>}
          </div>
          <div className="member-status-filter">
            <label htmlFor="member-status">Status</label>
            <select id="member-status" value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }}>
              <option value="">All members</option>
              <option value="ACTIVE">Active</option>
              <option value="INACTIVE">Inactive</option>
            </select>
          </div>
        </div>
      </section>

      <QueryState
        q={query}
        isEmpty={(p) => p.items.length === 0}
        empty={
          <div className="member-empty">
            <div className="empty-icon"><UsersRound size={25} /></div>
            <Empty
              title={dq ? 'No members match your search' : 'No members yet'}
              hint={dq ? 'Try a different name, phone number or member code.' : 'Add your first member to start building your gym directory.'}
            >
              {can('canEditMembers') && !dq && (
                <button className="btn primary" onClick={() => setCreating(true)}><Plus size={16} /> Add your first member</button>
              )}
            </Empty>
          </div>
        }
      >
        {(p) => {
          const activeOnPage = p.items.filter((m) => m.membershipStatus === 'ACTIVE').length;
          const attentionOnPage = p.items.filter((m) => ['EXPIRING_SOON', 'EXPIRED'].includes(m.membershipStatus ?? '')).length;

          return (
            <>
              <div className="member-results-meta">
                <div>
                  <strong>{p.items.length}</strong> member{p.items.length === 1 ? '' : 's'} on this page
                  {dq && <span className="muted"> matching “{dq}”</span>}
                </div>
                <div className="member-mini-stats">
                  <span><i className="dot good" /> {activeOnPage} active</span>
                  {attentionOnPage > 0 && <span><i className="dot warn" /> {attentionOnPage} need attention</span>}
                </div>
              </div>

              <div className="member-table-wrap">
                <div className="member-table-head">
                  <span>Member</span>
                  <span>Contact</span>
                  <span>Membership</span>
                  <span>Status</span>
                  <span aria-hidden="true" />
                </div>

                <ul className="member-table">
                  {p.items.map((m) => <MemberRow key={m.id} member={m} />)}
                </ul>
              </div>

              <Pager page={p.page} totalPages={p.totalPages} onPage={setPage} />
            </>
          );
        }}
      </QueryState>

      {creating && <MemberFormModal onClose={() => setCreating(false)} />}
    </>
  );
}

function MemberRow({ member: m }: { member: MemberSummary }) {
  const initials = m.fullName.split(/\\s+/).filter(Boolean).slice(0, 2).map((part) => part[0]).join('').toUpperCase();
  const membershipAttention = ['EXPIRING_SOON', 'EXPIRED'].includes(m.membershipStatus ?? '');

  return (
    <li className="member-table-row">
      <Link className="member-main" to={`/members/${m.id}`}>
        <span className="member-avatar">{initials || <CircleUserRound size={21} />}</span>
        <span className="member-identity">
          <strong>{m.fullName}</strong>
          <small>{m.memberCode}</small>
        </span>
      </Link>

      <div className="member-contact">
        <span><Phone size={14} /> {m.phone || 'No phone'}</span>
        {m.email && <span><Mail size={14} /> {m.email}</span>}
      </div>

      <div className="member-plan">
        <strong>{m.planName || 'No membership'}</strong>
        {m.membershipEndDate && <small>{membershipAttention ? 'Ends ' : 'Until '}{formatDate(m.membershipEndDate)}</small>}
      </div>

      <div className="member-status">
        <StatusBadge status={m.membershipStatus} />
        {m.status === 'INACTIVE' && <StatusBadge status="INACTIVE" />}
      </div>

      <Link className="member-open" to={`/members/${m.id}`} aria-label={`Open ${m.fullName}`}>
        <ChevronRight size={18} />
      </Link>
    </li>
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
        <div className="modal-form-intro">
          <div className="modal-form-icon"><Activity size={20} /></div>
          <div><strong>{memberId ? 'Keep the profile up to date' : 'Create a new member profile'}</strong><p className="muted">Basic details are enough to get started. You can manage membership separately.</p></div>
        </div>
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
