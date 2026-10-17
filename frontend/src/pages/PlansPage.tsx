import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { get, post, put } from '../api/client';
import type { Plan } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { formatMoney, parseRupeesToPaise } from '../lib/format';
import { Empty, Field, FormError, Modal, PageHeader, QueryState } from '../components/ui';

export function PlansPage() {
  const { isOwner } = useAuth();
  const [editing, setEditing] = useState<Plan | 'new' | null>(null);
  const q = useQuery({ queryKey: ['plans', 'all'], queryFn: () => get<Plan[]>('/api/v1/plans?includeInactive=true') });
  return (
    <>
      <PageHeader title="Membership plans" actions={isOwner && <button className="btn primary" onClick={() => setEditing('new')}>New plan</button>} />
      <QueryState q={q} isEmpty={(l) => l.length === 0} empty={<Empty title="No plans yet" hint={isOwner ? 'Create your first plan, e.g. Monthly — 30 days.' : 'Ask the owner to create plans.'} />}>
        {(plans) => (
          <ul className="list">
            {plans.map((p) => (
              <li key={p.id} className="row">
                <div>
                  <strong>{p.name}</strong> {!p.active && <span className="badge muted">Inactive</span>}
                  <div className="muted">{p.durationDays} days · {formatMoney(p.pricePaise)}</div>
                  {p.description && <small className="muted">{p.description}</small>}
                </div>
                {isOwner && <button className="btn small" onClick={() => setEditing(p)}>Edit</button>}
              </li>
            ))}
          </ul>
        )}
      </QueryState>
      {editing && <PlanModal plan={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />}
    </>
  );
}

function PlanModal({ plan, onClose }: { plan: Plan | null; onClose: () => void }) {
  const qc = useQueryClient();
  const [name, setName] = useState(plan?.name ?? '');
  const [days, setDays] = useState(String(plan?.durationDays ?? 30));
  const [price, setPrice] = useState(plan ? String(plan.pricePaise / 100) : '');
  const [desc, setDesc] = useState(plan?.description ?? '');
  const [active, setActive] = useState(plan?.active ?? true);
  const [errs, setErrs] = useState<Record<string, string>>({});
  const m = useMutation({
    mutationFn: () => {
      const body = { name: name.trim(), durationDays: Number(days), pricePaise: parseRupeesToPaise(price), description: desc.trim() || null, active };
      return plan ? put(`/api/v1/plans/${plan.id}`, body) : post('/api/v1/plans', body);
    },
    onSuccess: () => { void qc.invalidateQueries({ queryKey: ['plans'] }); onClose(); },
  });
  return (
    <Modal title={plan ? 'Edit plan' : 'New plan'} onClose={onClose}>
      <form noValidate onSubmit={(e) => {
        e.preventDefault();
        const er: Record<string, string> = {};
        if (!name.trim()) er.name = 'Enter a plan name.';
        const d = Number(days);
        if (!Number.isInteger(d) || d < 1 || d > 3660) er.days = 'Duration must be between 1 and 3660 days.';
        if (parseRupeesToPaise(price) === null) er.price = 'Enter a valid price, e.g. 1500 or 0.';
        setErrs(er);
        if (Object.keys(er).length === 0) m.mutate();
      }}>
        <Field label="Name" htmlFor="pl-name" error={errs.name}><input id="pl-name" value={name} onChange={(e) => setName(e.target.value)} /></Field>
        <Field label="Duration (days)" htmlFor="pl-days" error={errs.days}><input id="pl-days" inputMode="numeric" value={days} onChange={(e) => setDays(e.target.value)} /></Field>
        <Field label="Price (₹)" htmlFor="pl-price" error={errs.price}><input id="pl-price" inputMode="decimal" value={price} onChange={(e) => setPrice(e.target.value)} /></Field>
        <Field label="Description (optional)" htmlFor="pl-desc"><textarea id="pl-desc" rows={2} value={desc} onChange={(e) => setDesc(e.target.value)} /></Field>
        <label className="check"><input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} /> Active (can be assigned)</label>
        <FormError error={m.error} />
        <div className="modal-actions"><button type="button" className="btn ghost" onClick={onClose}>Cancel</button><button className="btn primary" disabled={m.isPending}>Save</button></div>
      </form>
    </Modal>
  );
}
