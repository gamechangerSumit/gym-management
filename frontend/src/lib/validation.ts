export type Errors<K extends string = string> = Partial<Record<K, string>>;

export function validatePassword(pw: string): string | null {
  if (pw.length < 8) return 'Use at least 8 characters.';
  if (!/[A-Za-z]/.test(pw) || !/\d/.test(pw)) return 'Include both letters and numbers.';
  return null;
}

export function validatePhone(raw: string): string | null {
  const digits = raw.replace(/[\s\-()]/g, '');
  if (!digits) return 'Phone number is required.';
  const d = digits.replace(/^\+?91/, '').replace(/^0/, '');
  if (!/^[6-9]\d{9}$/.test(d)) return 'Enter a valid 10-digit mobile number.';
  return null;
}

export function validateEmail(v: string): string | null {
  if (!v) return null;
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v) ? null : 'Enter a valid email address.';
}

export function validateName(v: string): string | null {
  const t = v.trim();
  if (t.length < 2) return 'Name must be at least 2 characters.';
  if (t.length > 120) return 'Name is too long.';
  return null;
}

export function compact<K extends string>(e: Errors<K>): Errors<K> {
  const out: Errors<K> = {};
  for (const k of Object.keys(e) as K[]) if (e[k]) out[k] = e[k];
  return out;
}
