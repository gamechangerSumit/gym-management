export class ApiError extends Error {
  status: number;
  code: string;
  fieldErrors: Record<string, string>;
  constructor(status: number, code: string, message: string, fieldErrors: Record<string, string> = {}) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
  }
}

function csrfToken(): string | undefined {
  const m = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
  return m ? decodeURIComponent(m[1]) : undefined;
}

type Method = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

/** Shown for any failure we cannot explain; never exposes server internals. */
export const GENERIC_ERROR = 'Something went wrong. Please try again.';

let onUnauthorized: (() => void) | null = null;
export function setUnauthorizedHandler(fn: (() => void) | null) {
  onUnauthorized = fn;
}

export async function api<T = unknown>(method: Method, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (method !== 'GET') {
    const t = csrfToken();
    if (t) headers['X-XSRF-TOKEN'] = t;
  }
  let res: Response;
  try {
    res = await fetch(path, {
      method,
      headers,
      credentials: 'same-origin',
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, 'NETWORK', 'Cannot reach the server. Check your connection and try again.');
  }
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  let data: unknown = undefined;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = undefined;
    }
  }
  if (!res.ok) {
    const e = (data ?? {}) as { code?: string; message?: string; fieldErrors?: Record<string, string> };
    if (res.status === 401 && onUnauthorized && !path.endsWith('/auth/login')) onUnauthorized();
    throw new ApiError(res.status, e.code ?? 'ERROR', e.message ?? GENERIC_ERROR, e.fieldErrors ?? {});
  }
  return data as T;
}

export const get = <T,>(path: string) => api<T>('GET', path);
export const post = <T = void,>(path: string, body?: unknown) => api<T>('POST', path, body ?? {});
export const put = <T = void,>(path: string, body?: unknown) => api<T>('PUT', path, body ?? {});
export const patch = <T = void,>(path: string, body?: unknown) => api<T>('PATCH', path, body ?? {});

export function qs(params: Record<string, string | number | boolean | null | undefined>): string {
  const p = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== null && v !== '') p.set(k, String(v));
  }
  const s = p.toString();
  return s ? `?${s}` : '';
}
