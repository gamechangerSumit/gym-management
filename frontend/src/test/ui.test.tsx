import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { ApiError } from '../api/client';
import { Empty, ErrorBox, QueryState } from '../components/ui';
import { ActivatePage } from '../pages/AuthPages';

vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ session: { authenticated: false, demo: false, role: null, mustChangePassword: false }, refresh: vi.fn(), logout: vi.fn() }),
}));

function wrap(ui: React.ReactNode, route = '/') {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={qc}><MemoryRouter initialEntries={[route]}>{ui}</MemoryRouter></QueryClientProvider>);
}

describe('QueryState', () => {
  const base = { isLoading: false, isError: false, error: null, data: undefined as unknown, refetch: vi.fn() };
  it('shows loading', () => {
    render(<QueryState q={{ ...base, isLoading: true }}>{() => <p>data</p>}</QueryState>);
    expect(screen.getByRole('status')).toHaveTextContent('Loading');
  });
  it('shows a retryable error without leaking details', async () => {
    const refetch = vi.fn();
    render(<QueryState q={{ ...base, isError: true, error: new Error('stack trace at com.example'), refetch }}>{() => <p>data</p>}</QueryState>);
    expect(screen.getByRole('alert')).toHaveTextContent('Something went wrong');
    expect(screen.queryByText(/com.example/)).toBeNull();
    await userEvent.click(screen.getByRole('button', { name: /try again/i }));
    expect(refetch).toHaveBeenCalled();
  });
  it('shows the empty state', () => {
    render(<QueryState q={{ ...base, data: [] as string[] }} isEmpty={(d) => d.length === 0} empty={<Empty title="No members yet" />}>{() => <p>data</p>}</QueryState>);
    expect(screen.getByText('No members yet')).toBeInTheDocument();
  });
  it('renders data', () => {
    render(<QueryState q={{ ...base, data: ['x'] }}>{(d) => <p>{d[0]}</p>}</QueryState>);
    expect(screen.getByText('x')).toBeInTheDocument();
  });
  it('shows server messages from ApiError', () => {
    render(<ErrorBox error={new ApiError(409, 'DUPLICATE_PHONE', 'A member with this phone number already exists.')} />);
    expect(screen.getByRole('alert')).toHaveTextContent('already exists');
  });
});

describe('ActivatePage form validation', () => {
  beforeEach(() => vi.restoreAllMocks());
  it('explains a missing token', () => {
    wrap(<ActivatePage />, '/activate');
    expect(screen.getByText(/missing its token/i)).toBeInTheDocument();
  });
  it('blocks weak and mismatched passwords before calling the server', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    wrap(<ActivatePage />, '/activate?token=abc');
    await userEvent.type(screen.getByLabelText('New password'), 'short');
    await userEvent.type(screen.getByLabelText('Confirm new password'), 'different');
    await userEvent.click(screen.getByRole('button', { name: 'Activate' }));
    const alerts = screen.getAllByRole('alert').map((a) => a.textContent);
    expect(alerts.join(' ')).toMatch(/at least 8/);
    expect(alerts.join(' ')).toMatch(/do not match/);
    expect(fetchSpy).not.toHaveBeenCalled();
  });
});
