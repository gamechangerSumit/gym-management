import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

interface Item { to: string; label: string; show: boolean }

export function DemoBanner() {
  const { session } = useAuth();
  if (!session.demo) return null;
  return <div className="demo-banner" role="note">DEMO — sample data only. Do not enter real member information.</div>;
}

export function StaffLayout() {
  const { session, can, isOwner, logout } = useAuth();
  const items: Item[] = [
    { to: '/dashboard', label: 'Dashboard', show: true },
    { to: '/scan', label: 'Check-in', show: true },
    { to: '/attendance', label: 'Attendance', show: true },
    { to: '/members', label: 'Members', show: true },
    { to: '/plans', label: 'Plans', show: true },
    { to: '/payments', label: 'Payments', show: can('canRecordPayments') },
    { to: '/recovery', label: 'Renewals', show: can('canManageMemberships') },
    { to: '/reports', label: 'Reports', show: can('canViewReports') },
    { to: '/staff', label: 'Staff', show: isOwner },
    { to: '/settings', label: 'Settings', show: isOwner },
    { to: '/audit', label: 'Audit', show: isOwner },
  ];
  return (
    <div className="shell">
      <DemoBanner />
      <header className="topbar">
        <strong className="brand">{session.gym?.name ?? 'Gym'}</strong>
        <span className="who">{session.loginId} · {session.role?.toLowerCase()}</span>
        <button className="btn small ghost" onClick={() => void logout()}>Sign out</button>
      </header>
      <nav className="nav" aria-label="Main">
        {items.filter((i) => i.show).map((i) => (
          <NavLink key={i.to} to={i.to} className={({ isActive }) => (isActive ? 'active' : '')}>{i.label}</NavLink>
        ))}
      </nav>
      <main className="content"><Outlet /></main>
    </div>
  );
}

export function MemberLayout() {
  const { session, logout } = useAuth();
  const items = [
    { to: '/me', label: 'My Gym', end: true },
    { to: '/me/qr', label: 'My QR' },
    { to: '/me/attendance', label: 'Visits' },
    { to: '/me/payments', label: 'Payments' },
    { to: '/me/profile', label: 'Profile' },
  ];
  return (
    <div className="shell">
      <DemoBanner />
      <header className="topbar">
        <strong className="brand">{session.gym?.name ?? 'Gym'}</strong>
        <button className="btn small ghost" onClick={() => void logout()}>Sign out</button>
      </header>
      <main className="content member"><Outlet /></main>
      <nav className="tabbar" aria-label="Member">
        {items.map((i) => (
          <NavLink key={i.to} to={i.to} end={i.end} className={({ isActive }) => (isActive ? 'active' : '')}>{i.label}</NavLink>
        ))}
      </nav>
    </div>
  );
}
