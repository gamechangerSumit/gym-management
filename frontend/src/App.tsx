import { Navigate, Outlet, Route, Routes } from 'react-router-dom';
import { useAuth } from './auth/AuthContext';
import type { StaffPermissions } from './api/types';
import { MemberLayout, StaffLayout } from './components/Layout';
import { ActivatePage, ChangePasswordPage, LoginPage, ResetPasswordPage } from './pages/AuthPages';
import { DashboardPage } from './pages/DashboardPage';
import { MembersPage } from './pages/MembersPage';
import { MemberDetailPage } from './pages/MemberDetailPage';
import { PlansPage } from './pages/PlansPage';
import { PaymentsPage } from './pages/PaymentsPage';
import { RecoveryPage } from './pages/RecoveryPage';
import { ScanPage } from './pages/ScanPage';
import { AttendancePage } from './pages/AttendancePage';
import { ReportsPage } from './pages/ReportsPage';
import { AuditPage, SettingsPage, StaffPage } from './pages/AdminPages';
import { MyAttendancePage, MyHomePage, MyPaymentsPage, MyProfilePage, MyQrPage } from './pages/MemberPortal';
import { AddOnsPage, MemberAddOnsPage } from './pages/AddOnsPage';
import { MemberRenewalPage, RenewalOrdersPage } from './pages/RenewalOrdersPage';

type Need = 'staff' | 'member' | 'owner' | keyof StaffPermissions;

/** Route guard. The server enforces every rule again; this only keeps people out of screens they cannot use. */
export function Guard({ need }: { need: Need }) {
  const { session, can, isOwner, isStaff, isMember } = useAuth();
  if (!session.authenticated) return <Navigate to="/login" replace />;
  if (session.mustChangePassword) return <Navigate to="/change-password" replace />;
  let ok: boolean;
  if (need === 'member') ok = isMember;
  else if (need === 'staff') ok = isOwner || isStaff;
  else if (need === 'owner') ok = isOwner;
  else ok = can(need);
  if (!ok) return <Navigate to="/" replace />;
  return <Outlet />;
}

function Home() {
  const { session } = useAuth();
  if (!session.authenticated) return <Navigate to="/login" replace />;
  if (session.mustChangePassword) return <Navigate to="/change-password" replace />;
  return <Navigate to={session.role === 'MEMBER' ? '/me' : '/dashboard'} replace />;
}

function NotFound() {
  return <div className="center-screen"><div className="card narrow"><h1>Page not found</h1><a href="/">Go home</a></div></div>;
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Home />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/activate" element={<ActivatePage />} />
      <Route path="/reset-password" element={<ResetPasswordPage />} />
      <Route path="/change-password" element={<ChangePasswordPage />} />

      <Route element={<Guard need="staff" />}>
        <Route element={<StaffLayout />}>
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/scan" element={<ScanPage />} />
          <Route path="/attendance" element={<AttendancePage />} />
          <Route path="/members" element={<MembersPage />} />
          <Route path="/members/:id" element={<MemberDetailPage />} />
          <Route path="/plans" element={<PlansPage />} />
          <Route element={<Guard need="canRecordPayments" />}><Route path="/payments" element={<PaymentsPage />} /></Route>
          <Route element={<Guard need="canManageMemberships" />}><Route path="/recovery" element={<RecoveryPage />} />
          <Route path="/addons" element={<AddOnsPage />} />
          <Route path="/renewal-orders" element={<RenewalOrdersPage />} /></Route>
          <Route element={<Guard need="canViewReports" />}><Route path="/reports" element={<ReportsPage />} /></Route>
          <Route element={<Guard need="owner" />}>
            <Route path="/staff" element={<StaffPage />} />
            <Route path="/settings" element={<SettingsPage />} />
            <Route path="/audit" element={<AuditPage />} />
          </Route>
        </Route>
      </Route>

      <Route element={<Guard need="member" />}>
        <Route element={<MemberLayout />}>
          <Route path="/me" element={<MyHomePage />} />
          <Route path="/me/qr" element={<MyQrPage />} />
          <Route path="/me/attendance" element={<MyAttendancePage />} />
          <Route path="/me/payments" element={<MyPaymentsPage />} />
          <Route path="/me/profile" element={<MyProfilePage />} />
          <Route path="/me/addons" element={<MemberAddOnsPage />} />
          <Route path="/me/renewal" element={<MemberRenewalPage />} />
        </Route>
      </Route>
      <Route path="*" element={<NotFound />} />
    </Routes>
  );
}
