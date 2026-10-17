import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { get, qs } from '../api/client';
import type { Page, Payment } from '../api/types';
import { formatDateTime, formatMoney, titleCase } from '../lib/format';
import { Empty, PageHeader, Pager, QueryState, StatusBadge } from '../components/ui';

export function PaymentsPage() {
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const q = useQuery({
    queryKey: ['payments', page, status, from, to],
    queryFn: () => get<Page<Payment>>('/api/v1/payments' + qs({ page, size: 20, status, from, to })),
    placeholderData: (prev) => prev,
  });
  return (
    <>
      <PageHeader title="Payments" />
      <div className="filters">
        <input type="date" aria-label="From date" value={from} onChange={(e) => { setFrom(e.target.value); setPage(0); }} />
        <input type="date" aria-label="To date" value={to} onChange={(e) => { setTo(e.target.value); setPage(0); }} />
        <select aria-label="Status" value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }}>
          <option value="">All</option><option value="RECORDED">Recorded</option><option value="VOID">Void</option>
        </select>
      </div>
      <QueryState q={q} isEmpty={(p) => p.items.length === 0} empty={<Empty title="No payments found" hint="Record payments from a member's page." />}>
        {(p) => (
          <>
            <ul className="list">
              {p.items.map((x) => (
                <li key={x.id} className="row">
                  <div>
                    <strong>{formatMoney(x.amountPaise)}</strong> <StatusBadge status={x.status} />
                    <div><Link to={`/members/${x.memberId}`}>{x.memberName}</Link> <span className="muted">{x.memberCode}</span></div>
                    <small className="muted">{formatDateTime(x.paidAt)} · {titleCase(x.method)} · {x.receiptNo}</small>
                  </div>
                  <a className="btn small" href={`/api/v1/payments/${x.id}/receipt.html`} target="_blank" rel="noreferrer">Receipt</a>
                </li>
              ))}
            </ul>
            <Pager page={p.page} totalPages={p.totalPages} onPage={setPage} />
          </>
        )}
      </QueryState>
    </>
  );
}
