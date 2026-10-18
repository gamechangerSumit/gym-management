import { NavLink, Outlet } from 'react-router-dom';
import {
  Activity, BarChart3, Bell, CalendarCheck2, CreditCard, Dumbbell, FileClock, LayoutDashboard, CalendarClock, BellRing,
  LogOut, Menu, QrCode, Settings, Users, UserCog, X, ChevronRight, ShoppingBag
} from 'lucide-react';
import { useState } from 'react';
import { useAuth } from '../auth/AuthContext';

interface Item { to: string; label: string; show: boolean; icon: typeof LayoutDashboard; }
interface Section { label: string; items: Item[]; }

const iconSize = 18;

export function DemoBanner() {
  const { session } = useAuth();
  if (!session.demo) return null;
  return <div className="demo-banner"><span>DEMO ENVIRONMENT</span><span>Sample data only — do not enter real member information.</span></div>;
}

export function StaffLayout() {
  const { session, can, isOwner, logout } = useAuth();
  const [mobileOpen, setMobileOpen] = useState(false);
  const sections: Section[] = [
    {
      label: 'Workspace',
      items: [
        { to: '/dashboard', label: 'Dashboard', show: true, icon: LayoutDashboard },
        { to: '/scan', label: 'Check-in', show: true, icon: QrCode },
        { to: '/attendance', label: 'Attendance', show: true, icon: CalendarCheck2 },
        { to: '/gym-qr', label: 'Gym QR display', show: true, icon: QrCode },
      ],
    },
    {
      label: 'Members',
      items: [
        { to: '/members', label: 'Members', show: true, icon: Users },
        { to: '/plans', label: 'Plans', show: true, icon: Dumbbell },
        { to: '/payments', label: 'Payments', show: can('canRecordPayments'), icon: CreditCard },
        { to: '/recovery', label: 'Retention', show: can('canManageMemberships'), icon: Activity },
        { to: '/renewal-orders', label: 'Renewal orders', show: isOwner || can('canManageMemberships') || can('canRecordPayments'), icon: CalendarClock },
        { to: '/reminders', label: 'Reminders', show: isOwner || can('canManageMemberships'), icon: BellRing },
        { to: '/addons', label: 'Add-ons', show: isOwner || can('canRecordPayments') || can('canManageMemberships'), icon: ShoppingBag },
        { to: '/reports', label: 'Reports', show: can('canViewReports'), icon: BarChart3 },
      ],
    },
    {
      label: 'Administration',
      items: [
        { to: '/staff', label: 'Staff', show: isOwner, icon: UserCog },
        { to: '/settings', label: 'Settings', show: isOwner, icon: Settings },
        { to: '/audit', label: 'Audit log', show: isOwner, icon: FileClock },
      ],
    },
  ];

  const close = () => setMobileOpen(false);
  const nav = (
    <nav className="sidebar-nav" aria-label="Main navigation">
      {sections.map((section) => {
        const visible = section.items.filter((i) => i.show);
        if (!visible.length) return null;
        return (
          <div className="nav-section" key={section.label}>
            <div className="nav-section-title">{section.label}</div>
            {visible.map((item) => {
              const Icon = item.icon;
              return (
                <NavLink key={item.to} to={item.to} onClick={close}
                  className={({ isActive }) => isActive ? 'nav-item active' : 'nav-item'}>
                  <Icon size={iconSize} strokeWidth={1.9} />
                  <span>{item.label}</span>
                  <ChevronRight className="nav-chevron" size={15} />
                </NavLink>
              );
            })}
          </div>
        );
      })}
    </nav>
  );

  return (
    <div className="app-shell">
      <DemoBanner />
      <aside className={mobileOpen ? 'sidebar mobile-open' : 'sidebar'}>
        <div className="brand-lockup">
          <div className="brand-mark"><Dumbbell size={21} strokeWidth={2.4} /></div>
          <div className="brand-copy">
            <strong>{session.gym?.name ?? 'Gym'}</strong>
            <span>Management Console</span>
          </div>
          <button className="icon-btn mobile-close" onClick={close} aria-label="Close navigation"><X size={20} /></button>
        </div>
        <div className="sidebar-scroll">{nav}</div>
        <div className="sidebar-user">
          <div className="avatar">{(session.loginId ?? 'G').slice(0, 1).toUpperCase()}</div>
          <div className="user-meta"><strong>{session.loginId ?? 'User'}</strong><span>{session.role?.toLowerCase()}</span></div>
          <button className="icon-btn" onClick={() => void logout()} title="Sign out" aria-label="Sign out"><LogOut size={17} /></button>
        </div>
      </aside>

      {mobileOpen && <button className="sidebar-backdrop" aria-label="Close navigation" onClick={close} />}

      <div className="app-main">
        <header className="topbar">
          <button className="icon-btn mobile-menu" onClick={() => setMobileOpen(true)} aria-label="Open navigation"><Menu size={22} /></button>
          <div className="topbar-title"><span>Gym operations</span><strong>{session.gym?.name ?? 'Dashboard'}</strong></div>
          <div className="topbar-actions">
            <button className="icon-btn" aria-label="Notifications"><Bell size={19} /></button>
            <div className="topbar-profile"><span>{session.loginId}</span><small>{session.role?.toLowerCase()}</small></div>
          </div>
        </header>
        <main className="content"><Outlet /></main>
      </div>
    </div>
  );
}

export function MemberLayout() {
  const { session, logout } = useAuth();
  const items = [
    { to: '/me', label: 'Home', icon: LayoutDashboard, end: true },
    { to: '/me/check-in', label: 'Check-in', icon: QrCode },
    { to: '/me/attendance', label: 'Visits', icon: CalendarCheck2 },
    { to: '/me/payments', label: 'Payments', icon: CreditCard },
    { to: '/me/profile', label: 'Profile', icon: Users },
    { to: '/me/addons', label: 'Add-ons', icon: ShoppingBag },
    { to: '/me/renewal', label: 'Renew', icon: CalendarClock },
  ];
  return (
    <div className="member-app">
      <DemoBanner />
      <header className="member-topbar">
        <div className="brand-lockup">
          <div className="brand-mark"><Dumbbell size={20} /></div>
          <div className="brand-copy"><strong>{session.gym?.name ?? 'Gym'}</strong><span>Member portal</span></div>
        </div>
        <button className="btn small ghost" onClick={() => void logout()}><LogOut size={15} /> Sign out</button>
      </header>
      <main className="content member"><Outlet /></main>
      <nav className="tabbar" aria-label="Member">
        {items.map((i) => {
          const Icon = i.icon;
          return <NavLink key={i.to} to={i.to} end={i.end} className={({ isActive }) => isActive ? 'active' : ''}>
            <Icon size={19} /><span>{i.label}</span>
          </NavLink>;
        })}
      </nav>
    </div>
  );
}
