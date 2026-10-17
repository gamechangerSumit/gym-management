import { useEffect, useRef, useState, type ReactNode } from 'react';
import { ApiError, GENERIC_ERROR } from '../api/client';
import { titleCase } from '../lib/format';

export function Spinner({ label = 'Loading…' }: { label?: string }) {
  return <div className="state" role="status" aria-live="polite">{label}</div>;
}

export function ErrorBox({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const msg = error instanceof ApiError ? error.message : GENERIC_ERROR;
  return (
    <div className="state error" role="alert">
      <p>{msg}</p>
      {onRetry && <button className="btn small" onClick={onRetry}>Try again</button>}
    </div>
  );
}

export function Empty({ title, hint, children }: { title: string; hint?: string; children?: ReactNode }) {
  return (
    <div className="state empty">
      <p><strong>{title}</strong></p>
      {hint && <p className="muted">{hint}</p>}
      {children}
    </div>
  );
}

/** Renders loading / error / empty / data states for a react-query result. */
export function QueryState<T>({ q, isEmpty, empty, children }: {
  q: { isLoading: boolean; isError: boolean; error: unknown; data: T | undefined; refetch: () => unknown };
  isEmpty?: (d: T) => boolean;
  empty?: ReactNode;
  children: (d: T) => ReactNode;
}) {
  if (q.isLoading) return <Spinner />;
  if (q.isError) return <ErrorBox error={q.error} onRetry={() => q.refetch()} />;
  if (q.data === undefined) return null;
  if (isEmpty && isEmpty(q.data)) return <>{empty ?? <Empty title="Nothing here yet" />}</>;
  return <>{children(q.data)}</>;
}

const TONES: Record<string, string> = {
  ACTIVE: 'good', EXPIRING_SOON: 'warn', EXPIRED: 'bad', ON_HOLD: 'info', CANCELLED: 'muted', SCHEDULED: 'info',
  INACTIVE: 'muted', RECORDED: 'good', VOID: 'bad', DISABLED: 'bad', INVITED: 'info',
};

export function StatusBadge({ status }: { status: string | null | undefined }) {
  if (!status) return <span className="badge muted">No plan</span>;
  return <span className={`badge ${TONES[status] ?? 'muted'}`}>{titleCase(status)}</span>;
}

export function Field({ label, error, hint, children, htmlFor }: {
  label: string; error?: string; hint?: string; children: ReactNode; htmlFor: string;
}) {
  return (
    <div className={`field${error ? ' has-error' : ''}`}>
      <label htmlFor={htmlFor}>{label}</label>
      {children}
      {hint && !error && <small className="muted">{hint}</small>}
      {error && <small className="err" role="alert" id={`${htmlFor}-err`}>{error}</small>}
    </div>
  );
}

export function Pager({ page, totalPages, onPage }: { page: number; totalPages: number; onPage: (p: number) => void }) {
  if (totalPages <= 1) return null;
  return (
    <nav className="pager" aria-label="Pagination">
      <button className="btn small" disabled={page <= 0} onClick={() => onPage(page - 1)}>Previous</button>
      <span>Page {page + 1} of {totalPages}</span>
      <button className="btn small" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>Next</button>
    </nav>
  );
}

export function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const prev = document.activeElement as HTMLElement | null;
    ref.current?.focus();
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('keydown', onKey);
      prev?.focus?.();
    };
  }, [onClose]);
  return (
    <div className="modal-back" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="modal" role="dialog" aria-modal="true" aria-label={title} tabIndex={-1} ref={ref}>
        <div className="modal-head">
          <h2>{title}</h2>
          <button className="btn small ghost" onClick={onClose} aria-label="Close">✕</button>
        </div>
        {children}
      </div>
    </div>
  );
}

export function FormError({ error }: { error: unknown }) {
  if (!error) return null;
  const msg = error instanceof ApiError ? error.message : GENERIC_ERROR;
  return <div className="form-error" role="alert">{msg}</div>;
}

export function PageHeader({ title, actions }: { title: string; actions?: ReactNode }) {
  return (
    <div className="page-head">
      <h1>{title}</h1>
      <div className="actions">{actions}</div>
    </div>
  );
}

export function Stat({ label, value, hint }: { label: string; value: ReactNode; hint?: string }) {
  return (
    <div className="stat">
      <div className="stat-label">{label}</div>
      <div className="stat-value">{value}</div>
      {hint && <div className="stat-hint muted">{hint}</div>}
    </div>
  );
}

export function useDebounced<T>(value: T, ms = 300): T {
  const [v, setV] = useState<T>(value);
  useEffect(() => {
    const t = setTimeout(() => setV(value), ms);
    return () => clearTimeout(t);
  }, [value, ms, setV]);
  return v;
}

