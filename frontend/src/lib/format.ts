/** Money is integer paise everywhere; only formatting converts to rupees. */
export function formatMoney(paise: number | null | undefined): string {
  if (paise === null || paise === undefined) return '—';
  const neg = paise < 0;
  const abs = Math.abs(paise);
  const rupees = Math.floor(abs / 100);
  const rem = abs % 100;
  const grouped = rupees.toLocaleString('en-IN');
  return `${neg ? '-' : ''}₹${grouped}${rem ? '.' + String(rem).padStart(2, '0') : ''}`;
}

/** Parses "1500", "1,500.50" into paise; returns null when invalid or not positive. */
export function parseRupeesToPaise(input: string): number | null {
  const s = input.trim().replace(/,/g, '');
  if (!/^\d+(\.\d{1,2})?$/.test(s)) return null;
  const [r, p = ''] = s.split('.');
  const paise = Number(r) * 100 + Number(p.padEnd(2, '0'));
  return Number.isSafeInteger(paise) ? paise : null;
}

export function formatDate(iso: string | null | undefined): string {
  if (!iso) return '—';
  const d = new Date(iso.length === 10 ? iso + 'T00:00:00' : iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
}

export function formatDateTime(iso: string | null | undefined, timeZone?: string): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString('en-IN', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit', timeZone });
}

export function titleCase(s: string | null | undefined): string {
  if (!s) return '—';
  return s.toLowerCase().split('_').map((w) => w.charAt(0).toUpperCase() + w.slice(1)).join(' ');
}
