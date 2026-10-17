import { createContext, useContext, useEffect, type ReactNode } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { get, post, setUnauthorizedHandler } from '../api/client';
import type { SessionInfo, StaffPermissions } from '../api/types';

interface AuthValue {
  session: SessionInfo;
  logout: () => Promise<void>;
  refresh: () => Promise<void>;
  can: (p: keyof StaffPermissions) => boolean;
  isOwner: boolean;
  isStaff: boolean;
  isMember: boolean;
}

const Ctx = createContext<AuthValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const qc = useQueryClient();
  const q = useQuery({ queryKey: ['session'], queryFn: () => get<SessionInfo>('/api/v1/auth/session'), staleTime: 60_000 });

  useEffect(() => {
    setUnauthorizedHandler(() => {
      qc.setQueryData(['session'], (old: SessionInfo | undefined) =>
        old ? { ...old, authenticated: false, role: null, permissions: null } : old);
    });
    return () => setUnauthorizedHandler(null);
  }, [qc]);

  if (q.isLoading) return <div className="center-screen" role="status">Loading…</div>;
  if (q.isError || !q.data) {
    return (
      <div className="center-screen">
        <div className="card narrow">
          <h1>Can't reach the server</h1>
          <p className="muted">Check your connection, then try again.</p>
          <button className="btn" onClick={() => q.refetch()}>Retry</button>
        </div>
      </div>
    );
  }
  const session = q.data;
  const value: AuthValue = {
    session,
    logout: async () => {
      try {
        await post('/api/v1/auth/logout');
      } finally {
        qc.clear();
        await qc.invalidateQueries({ queryKey: ['session'] });
      }
    },
    refresh: async () => {
      await qc.invalidateQueries({ queryKey: ['session'] });
    },
    can: (p) => session.role === 'OWNER' || (session.role === 'STAFF' && !!session.permissions?.[p]),
    isOwner: session.role === 'OWNER',
    isStaff: session.role === 'STAFF',
    isMember: session.role === 'MEMBER',
  };
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useAuth(): AuthValue {
  const v = useContext(Ctx);
  if (!v) throw new Error('useAuth outside AuthProvider');
  return v;
}
