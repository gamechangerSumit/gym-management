import { useState, type FormEvent } from 'react';
import { Navigate, useNavigate, useSearchParams, Link } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { post, ApiError } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { Field, FormError } from '../components/ui';
import { DemoBanner } from '../components/Layout';
import { validatePassword } from '../lib/validation';

function homeFor(role: string | null): string {
  return role === 'MEMBER' ? '/me' : '/dashboard';
}

function Shell({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="auth-page">
      <div className="auth-brand-panel">
        <div className="auth-logo"><DumbbellIcon /></div>
        <span className="auth-eyebrow">GYM MANAGEMENT</span>
        <h1>Run your gym.<br /><em>Build stronger.</em></h1>
        <p>One clean workspace for members, check-ins, memberships, payments and renewals.</p>
        <div className="auth-feature"><span>01</span><strong>Fast check-ins</strong><small>QR and front-desk attendance in seconds.</small></div>
        <div className="auth-feature"><span>02</span><strong>Member intelligence</strong><small>See renewals, dues and activity at a glance.</small></div>
        <div className="auth-feature"><span>03</span><strong>Simple operations</strong><small>Less admin work. More time on the floor.</small></div>
      </div>
      <div className="auth-form-panel">
        <div className="auth-form-card">
          <DemoBanner />
          <div className="auth-mobile-logo"><div className="auth-logo"><DumbbellIcon /></div><strong>Gym Management</strong></div>
          <div className="auth-heading"><span>Welcome back</span><h2>{title}</h2></div>
          {children}
        </div>
        <p className="auth-footer">Secure session-based access · Gym Management</p>
      </div>
    </div>
  );
}

function DumbbellIcon() {
  return <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d="M6.5 6.5v11M17.5 6.5v11M3.5 9v6M20.5 9v6M3.5 12h17M6.5 8h11M6.5 16h11"/>
  </svg>;
}

export function LoginPage() {
  const { session, refresh } = useAuth();
  const nav = useNavigate();
  const [loginId, setLoginId] = useState('');
  const [password, setPassword] = useState('');
  const [errors, setErrors] = useState<{ loginId?: string; password?: string }>({});
  const m = useMutation({
    mutationFn: () => post('/api/v1/auth/login', { loginId: loginId.trim(), password }),
    onSuccess: async () => {
      await refresh();
      nav('/', { replace: true });
    },
  });
  if (session.authenticated) return <Navigate to={session.mustChangePassword ? '/change-password' : homeFor(session.role)} replace />;

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const errs = {
      loginId: loginId.trim() ? undefined : 'Enter your login ID.',
      password: password ? undefined : 'Enter your password.',
    };
    setErrors(errs);
    if (!errs.loginId && !errs.password) m.mutate();
  };
  const err = m.error instanceof ApiError ? m.error : null;
  return (
    <Shell title="Sign in">
      <form onSubmit={submit} noValidate>
        <Field label="Login ID" htmlFor="loginId" error={errors.loginId}>
          <input id="loginId" autoComplete="username" autoCapitalize="none" value={loginId} onChange={(e) => setLoginId(e.target.value)} />
        </Field>
        <Field label="Password" htmlFor="password" error={errors.password}>
          <input id="password" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} />
        </Field>
        {m.isError && <FormError error={m.error} />}
        {err?.status === 429 && <p className="muted">Too many attempts. Please wait a minute.</p>}
        <button className="btn primary block" disabled={m.isPending}>{m.isPending ? 'Signing in…' : 'Sign in'}</button>
      </form>
      <p className="muted small-print"><Link to="/reset-password">Forgot your password?</Link></p>
    </Shell>
  );
}

function NewPasswordForm({ onSubmit, pending, error, label }: {
  onSubmit: (pw: string) => void; pending: boolean; error: unknown; label: string;
}) {
  const [pw, setPw] = useState('');
  const [pw2, setPw2] = useState('');
  const [errs, setErrs] = useState<{ pw?: string; pw2?: string }>({});
  const submit = (e: FormEvent) => {
    e.preventDefault();
    const e1 = validatePassword(pw) ?? undefined;
    const e2 = pw === pw2 ? undefined : 'Passwords do not match.';
    setErrs({ pw: e1, pw2: e2 });
    if (!e1 && !e2) onSubmit(pw);
  };
  return (
    <form onSubmit={submit} noValidate>
      <Field label="New password" htmlFor="newpw" error={errs.pw} hint="At least 8 characters with letters and numbers.">
        <input id="newpw" type="password" autoComplete="new-password" value={pw} onChange={(e) => setPw(e.target.value)} />
      </Field>
      <Field label="Confirm new password" htmlFor="newpw2" error={errs.pw2}>
        <input id="newpw2" type="password" autoComplete="new-password" value={pw2} onChange={(e) => setPw2(e.target.value)} />
      </Field>
      <FormError error={error} />
      <button className="btn primary block" disabled={pending}>{pending ? 'Saving…' : label}</button>
    </form>
  );
}

export function ActivatePage() {
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';
  const nav = useNavigate();
  const m = useMutation({
    mutationFn: (pw: string) => post('/api/v1/auth/activate', { token, newPassword: pw }),
    onSuccess: () => nav('/login', { replace: true, state: { activated: true } }),
  });
  if (!token) return <Shell title="Activation link"><p>This link is missing its token. Ask the gym for a new invitation link.</p></Shell>;
  return (
    <Shell title="Activate your account">
      <p className="muted">Choose a password to finish setting up your account.</p>
      <NewPasswordForm onSubmit={(pw) => m.mutate(pw)} pending={m.isPending} error={m.error} label="Activate" />
    </Shell>
  );
}

export function ResetPasswordPage() {
  const [params] = useSearchParams();
  const token = params.get('token');
  const nav = useNavigate();
  const [loginId, setLoginId] = useState('');
  const [sent, setSent] = useState(false);
  const req = useMutation({
    mutationFn: () => post('/api/v1/auth/password-reset/request', { loginId: loginId.trim() }),
    onSuccess: () => setSent(true),
  });
  const confirm = useMutation({
    mutationFn: (pw: string) => post('/api/v1/auth/password-reset/confirm', { token, newPassword: pw }),
    onSuccess: () => nav('/login', { replace: true }),
  });
  if (token) {
    return (
      <Shell title="Set a new password">
        <NewPasswordForm onSubmit={(pw) => confirm.mutate(pw)} pending={confirm.isPending} error={confirm.error} label="Save password" />
      </Shell>
    );
  }
  return (
    <Shell title="Reset your password">
      {sent ? (
        <p>If that login exists, the gym owner has been notified. They will share a reset link with you directly.</p>
      ) : (
        <form onSubmit={(e) => { e.preventDefault(); if (loginId.trim()) req.mutate(); }} noValidate>
          <p className="muted">Enter your login ID. This app does not send email or SMS — the owner will give you a reset link.</p>
          <Field label="Login ID" htmlFor="rid"><input id="rid" value={loginId} onChange={(e) => setLoginId(e.target.value)} /></Field>
          <FormError error={req.error} />
          <button className="btn primary block" disabled={req.isPending || !loginId.trim()}>Request reset</button>
        </form>
      )}
      <p className="muted small-print"><Link to="/login">Back to sign in</Link></p>
    </Shell>
  );
}

export function ChangePasswordPage() {
  const { session, refresh, logout } = useAuth();
  const nav = useNavigate();
  const [current, setCurrent] = useState('');
  const [pw, setPw] = useState('');
  const [errs, setErrs] = useState<{ current?: string; pw?: string }>({});
  const m = useMutation({
    mutationFn: () => post('/api/v1/auth/change-password', { currentPassword: current, newPassword: pw }),
    onSuccess: async () => {
      await refresh();
      nav('/', { replace: true });
    },
  });
  if (!session.authenticated) return <Navigate to="/login" replace />;
  const submit = (e: FormEvent) => {
    e.preventDefault();
    const e1 = current ? undefined : 'Enter your current password.';
    const e2 = validatePassword(pw) ?? undefined;
    setErrs({ current: e1, pw: e2 });
    if (!e1 && !e2) m.mutate();
  };
  return (
    <Shell title={session.mustChangePassword ? 'Choose a new password' : 'Change password'}>
      {session.mustChangePassword && <p className="muted">For your security, set your own password before continuing.</p>}
      <form onSubmit={submit} noValidate>
        <Field label="Current password" htmlFor="cur" error={errs.current}>
          <input id="cur" type="password" autoComplete="current-password" value={current} onChange={(e) => setCurrent(e.target.value)} />
        </Field>
        <Field label="New password" htmlFor="np" error={errs.pw} hint="At least 8 characters with letters and numbers.">
          <input id="np" type="password" autoComplete="new-password" value={pw} onChange={(e) => setPw(e.target.value)} />
        </Field>
        <FormError error={m.error} />
        <button className="btn primary block" disabled={m.isPending}>Save password</button>
      </form>
      <p className="muted small-print"><button className="link" onClick={() => void logout()}>Sign out</button></p>
    </Shell>
  );
}
