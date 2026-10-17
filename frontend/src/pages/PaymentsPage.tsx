import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ArrowUpRight, CreditCard, Download, IndianRupee, ReceiptText } from 'lucide-react';
import { get, qs } from '../api/client';
import type { Page, Payment } from '../api/types';
import { formatDateTime, formatMoney, titleCase } from '../lib/format';
import { Empty, PageHeader, Pager, QueryState, StatusBadge, Stat } from '../components/ui';

export function PaymentsPage() {
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const q = useQuery({ queryKey: ['payments', page, status, from, to], queryFn: () => get<Page<Payment>>('/api/v1/payments' + qs({ page, size: 20, status, from, to })), placeholderData: (prev) => prev });
  const items = q.data?.items ?? [];
  const total = items.reduce((s, x) => s + x.amountPaise, 0);
  return (
    <>
      <PageHeader title="Payments" actions={<button className="btn"><Download size={16} /> Export view</button>} />
      <div className="finance-hero">
        <div><span className="eyebrow">REVENUE DESK</span><h2>Keep every collection accounted for.</h2><p className="muted">Review receipts, payment methods and member balances.</p></div>
        <div className="finance-icon"><IndianRupee size={25} /></div>
      </div>
      <section className="stats">
        <Stat label="Page collection" value={formatMoney(total)} />
        <Stat label="Transactions" value={items.length} />
        <Stat label="Recorded" value={items.filter((x) => x.status === 'RECORDED').length} />
        <Stat label="Voided" value={items.filter((x) => x.status === 'VOID').length} />
      </section>
      <div className="payment-filters">
        <div><span>From</span><input type="date" aria-label="From date" value={from} onChange={(e) => { setFrom(e.target.value); setPage(0); }} /></div>
        <div><span>To</span><input type="date" aria-label="To date" value={to} onChange={(e) => { setTo(e.target.value); setPage(0); }} /></div>
        <div><span>Status</span><select aria-label="Status" value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }}><option value="">All payments</option><option value="RECORDED">Recorded</option><option value="VOID">Void</option></select></div>
      </div>
      <QueryState q={q} isEmpty={(p) => p.items.length === 0} empty={<Empty title="No payments found" hint="Record payments from a member's page." />}>
        {(p) => (
          <>
            <div className="payment-table">
              <div className="payment-table-head"><span>Member</span><span>Payment</span><span>Method</span><span>Status</span><span /></div>
              {p.items.map((x) => (
                <div className="payment-table-row" key={x.id}>
                  <div className="payment-member"><div className="payment-avatar">{x.memberName.split(' ').map((n) => n[0]).slice(0,2).join('').toUpperCase()}</div><div><Link to={`/members/${x.memberId}`}><strong>{x.memberName}</strong></Link><small>{x.memberCode} · {formatDateTime(x.paidAt)}</small></div></div>
                  <div><strong className="payment-amount">{formatMoney(x.amountPaise)}</strong><small>{x.receiptNo}</small></div>
                  <span className="payment-method"><CreditCard size={14} />{titleCase(x.method)}</span>
                  <StatusBadge status={x.status} />
                  <a className="icon-btn" href={`/api/v1/payments/${x.id}/receipt.html`} target="_blank" rel="noreferrer" aria-label="Open receipt"><ReceiptText size={16} /></a>
                </div>
              ))}
            </div>
            <Pager page={p.page} totalPages={p.totalPages} onPage={setPage} />
          </>
        )}
      </QueryState>
    </>
  );
}
