export type Role = 'OWNER' | 'STAFF' | 'MEMBER';

export interface StaffPermissions {
  canRecordPayments: boolean;
  canManageMemberships: boolean;
  canEditMembers: boolean;
  canViewReports: boolean;
}

export interface SessionInfo {
  authenticated: boolean;
  userId: number | null;
  loginId: string | null;
  role: Role | null;
  mustChangePassword: boolean;
  memberId: number | null;
  permissions: StaffPermissions | null;
  gym: { name: string; timezone: string } | null;
  demo: boolean;
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface MemberSummary {
  id: number;
  memberCode: string;
  fullName: string;
  phone: string;
  status: string;
  membershipStatus: string | null;
  membershipEndDate: string | null;
  planName: string | null;
}

export interface MemberDetail {
  id: number;
  memberCode: string;
  fullName: string;
  phone: string;
  email: string | null;
  joinDate: string;
  emergencyContact: string | null;
  status: string;
  staffNotes: string | null;
  hasAccount: boolean;
  accountStatus: string | null;
  accountLoginId: string | null;
  membershipStatus: string | null;
  membershipStartDate: string | null;
  membershipEndDate: string | null;
  planName: string | null;
  duesPaise: number | null;
}

export interface AddOn {
  id: number;
  kind: 'PT' | 'DIET' | 'PRODUCT';
  name: string;
  description: string | null;
  pricePaise: number;
  validityDays: number | null;
  stockOnHand: number | null;
  sessionsIncluded: number | null;
  active: boolean;
}

export interface AddOnOrder {
  id: number;
  memberId: number;
  memberCode: string;
  memberName: string;
  addonId: number;
  addonName: string;
  kind: 'PT' | 'DIET' | 'PRODUCT';
  quantity: number;
  unitPricePaise: number;
  totalPaise: number;
  sessionsIncluded: number | null;
  sessionsUsed: number;
  status: string;
  createdAt: string;
  paidAt: string | null;
  fulfilledAt: string | null;
}

export interface Plan {
  id: number;
  name: string;
  durationDays: number;
  pricePaise: number;
  description: string | null;
  active: boolean;
}

export interface Membership {
  id: number;
  memberId: number;
  planId: number;
  planName: string;
  startDate: string;
  endDate: string;
  pricePaise: number;
  storedStatus: string;
  status: string;
  renewedFromId: number | null;
  daysRemaining: number;
  holdReason: string | null;
  cancelReason: string | null;
  createdAt: string;
}

export interface Payment {
  id: number;
  memberId: number;
  memberCode: string;
  memberName: string;
  membershipId: number | null;
  amountPaise: number;
  paidAt: string;
  method: string;
  receiptNo: string;
  status: string;
  voidReason: string | null;
  voidedAt: string | null;
  recordedBy: string | null;
  note: string | null;
  addonOrderId: number | null;
}

export interface Dues {
  billedPaise: number;
  paidPaise: number;
  duesPaise: number;
}

export interface Receipt {
  gymName: string;
  receiptNo: string;
  status: string;
  voidReason: string | null;
  paidAt: string;
  paidAtText: string;
  amountPaise: number;
  amountText: string;
  method: string;
  memberCode: string;
  memberName: string;
  planName: string | null;
  note: string | null;
}

export interface QrPass {
  passId: number;
  token: string;
  payload: string;
  status: string;
  createdAt: string;
}

export type ScanResultCode =
  | 'CHECKED_IN'
  | 'DUPLICATE_RECENT'
  | 'EXPIRED'
  | 'NO_ACTIVE_MEMBERSHIP'
  | 'ON_HOLD'
  | 'MEMBER_INACTIVE'
  | 'INVALID_TOKEN';

export interface ScanResponse {
  result: ScanResultCode;
  message: string;
  memberId: number | null;
  memberCode: string | null;
  memberName: string | null;
  membershipEndDate: string | null;
  checkedInAt: string | null;
}

export interface AttendanceRecord {
  id: number;
  memberId: number;
  memberCode: string;
  memberName: string;
  checkedInAt: string;
  date: string;
  source: string;
  recordedBy: string | null;
  assistedReason: string | null;
}

export interface DashboardSummary {
  activeMembers: number;
  expiringSoon: number;
  expired: number;
  todaysCheckIns: number;
  collections: { from: string; to: string; totalPaise: number; paymentCount: number; rule: string } | null;
  expiringSoonDays: number;
  asOf: string;
  activeMembers7d: number;
  openNoShowCases: number;
  membersReturned7d: number;
  renewalsDue7d: number;
  renewalCollectedMonthPaise: number;
  addonRevenueMonthPaise: number;
}

export interface ActionItem {
  type: string;
  memberId: number;
  memberCode: string;
  memberName: string;
  phone: string;
  detail: string;
  rule: string;
}

export interface ActionList {
  asOf: string;
  items: ActionItem[];
  rules: string[];
}

export interface HealthScore {
  score: number | null;
  label: string;
  disclaimer: string;
  factors: { key: string; name: string; weight: number; value: number | null; detail: string; rule: string }[];
}

export interface Leakage {
  asOf: string;
  indicators: { key: string; title: string; count: number; amountPaise: number | null; rule: string }[];
}

export interface Followup {
  membershipId: number;
  memberId: number;
  memberCode: string;
  memberName: string;
  phone: string;
  endDate: string;
  planName: string;
  contactStatus: string;
  note: string | null;
  updatedAt: string | null;
}

export interface StaffUser {
  id: number;
  loginId: string;
  status: string;
  mustChangePassword: boolean;
  lastLoginAt: string | null;
  createdAt: string;
  permissions: StaffPermissions;
}

export interface Settings {
  gymName: string;
  timezone: string;
  duplicateWindowMinutes: number;
  dropOffDays: number;
  expiringSoonDays: number;
  noShowThresholdDays: number;
  weeklyVisitGoal: number;
  memberCanEditEmail: boolean;
}

export interface AuditEntry {
  id: number;
  actorId: number | null;
  actorLogin: string | null;
  action: string;
  entityType: string;
  entityId: number | null;
  details: unknown;
  at: string;
}

export interface LinkResponse {
  loginId: string;
  url: string;
  expiresAt: string;
}

export interface MeProfile {
  memberCode: string;
  fullName: string;
  phone: string;
  email: string | null;
  joinDate: string;
  emergencyContact: string | null;
  status: string;
  canEditEmail: boolean;
}

export interface MeMembership {
  hasMembership: boolean;
  planName: string | null;
  startDate: string | null;
  endDate: string | null;
  status: string | null;
  daysRemaining: number | null;
  visitsThisMonth: number;
  visitsThisWeek: number;
  weeklyVisitGoal: number;
  currentStreakWeeks: number;
  duesPaise: number;
}

export interface MeAttendancePage extends Page<{ id: number; checkedInAt: string; date: string; source: string }> {
  visitsThisMonth: number;
  monthly: { month: string; visits: number }[];
}

export interface MePayment {
  id: number;
  receiptNo: string;
  amountPaise: number;
  paidAt: string;
  method: string;
  status: string;
}

export interface MeQr {
  token: string;
  payload: string;
  issuedAt: string;
}

export interface Collections {
  from: string;
  to: string;
  totalPaise: number;
  paymentCount: number;
  voidedCount: number;
  voidedPaise: number;
  byMethod: { method: string; count: number; paise: number }[];
  rows: Page<{ paymentId: number; receiptNo: string; paidAt: string; memberCode: string; memberName: string; method: string; amountPaise: number }>;
}

export interface AttendanceReport {
  from: string;
  to: string;
  totalCheckIns: number;
  uniqueMembers: number;
  byDay: { date: string; count: number }[];
  rows: Page<{ memberCode: string; memberName: string; checkedInAt: string; date: string; source: string }>;
}

export interface ExpiryReport {
  from: string;
  to: string;
  totalMemberships: number;
  rows: Page<{ memberId: number; memberCode: string; memberName: string; phone: string; planName: string; startDate: string; endDate: string; status: string; renewed: boolean }>;
}

export interface OutstandingReport {
  totalDuesPaise: number;
  memberCount: number;
  rows: Page<{ memberId: number; memberCode: string; memberName: string; phone: string; billedPaise: number; paidPaise: number; duesPaise: number }>;
}
