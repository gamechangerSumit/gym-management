import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { get, post, put, qs } from '../api/client';
import type { Followup } from '../api/types';
import { formatDate, titleCase } from '../lib/format';
import { Empty, FormError, PageHeader, QueryState } from '../components/ui';

const STATUSES = ['NOT_CONTACTED', 'CONTACTED', 'NO_ANSWER', 'WILL_RENEW', 'NOT_INTERESTED'];

/** Renewal Recovery Center: expired memberships with contact tracking. Nothing is sent automatically. */
export function RecoveryPage() {
  const qc = useQueryClient();
  const [filter, setFilter] = useState('');
  const q = useQuery({ queryKey: ['followups', filter], queryFn: () => get<Followup[]>('/api/v1/followups' + qs({ status: filter })) });
  const update = useMutation({
    mutationFn: (a: { membershipId: number; contactStatus: string; note?: string }) => put('/api/v1/followups', a),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['followups'] }),
  });
  const renew = useMutation({
    mutationFn: (id: number) => post(`/api/v1/memberships/${id}/renew`, {}),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['followups'] }),
  });
  return (
    <>
      <PageHeader title="Renewal recovery" />
      <p className="muted">Members whose membership has ended or is about to. Call them yourself and record the outcome here — this app does not send messages.</p>
      <div className="filters">
        <select aria-label="Contact status" value={filter} onChange={(e) => setFilter(e.target.value)}>
          <option value="">All</option>
          {STATUSES.map((s) => <option key={s} value={s}>{titleCase(s)}</option>)}
        </select>
      </div>
      <FormError error={update.error ?? renew.error} />
      <QueryState q={q} isEmpty={(l) => l.length === 0} empty={<Empty title="Nobody to follow up" hint="Expired memberships will appear here." />}>
        {(list) => (
          <ul className="list">
            {list.map((f) => (
              <li key={f.membershipId} className="row wrap">
                <div>
                  <Link to={`/members/${f.memberId}`}><strong>{f.memberName}</strong></Link> <span className="muted">{f.memberCode}</span>
                  <div><a href={`tel:${f.phone}`}>{f.phone}</a> · {f.planName} · ended {formatDate(f.endDate)}</div>
                  {f.note && <small className="muted">Note: {f.note}</small>}
                </div>
                <div className="btn-row">
                  <select aria-label={`Contact status for ${f.memberName}`} value={f.contactStatus}
                    onChange={(e) => update.mutate({ membershipId: f.membershipId, contactStatus: e.target.value, note: f.note ?? undefined })}>
                    {STATUSES.map((s) => <option key={s} value={s}>{titleCase(s)}</option>)}
                  </select>
                  <button className="btn small ghost" onClick={() => {
                    const note = window.prompt('Add a short note', f.note ?? '');
                    if (note !== null) update.mutate({ membershipId: f.membershipId, contactStatus: f.contactStatus, note: note.trim() });
                  }}>Note</button>
                  <button className="btn small primary" disabled={renew.isPending} onClick={() => renew.mutate(f.membershipId)}>Renew</button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </QueryState>
    </>
  );
}
