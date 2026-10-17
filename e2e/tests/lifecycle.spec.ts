import { expect, test, type Page } from '@playwright/test';

const OWNER = process.env.E2E_OWNER_LOGIN ?? 'owner';
const OWNER_PW = process.env.E2E_OWNER_PASSWORD ?? '';
const NEW_PW = 'E2eOwnerPass123';
const run = Date.now().toString().slice(-6);
const phone = '98' + (Date.now() % 100000000).toString().padStart(8, '0');

async function login(page: Page, id: string, pw: string) {
  await page.goto('/login');
  await page.getByLabel('Login ID').fill(id);
  await page.getByLabel('Password').fill(pw);
  await page.getByRole('button', { name: 'Sign in' }).click();
}

test.skip(!OWNER_PW, 'set E2E_OWNER_PASSWORD to the bootstrap owner password');

test('owner lifecycle and member isolation', async ({ page, browser }) => {
  // Owner signs in (first login may force a password change).
  await login(page, OWNER, OWNER_PW);
  await page.waitForURL(/\/(dashboard|change-password)/);
  let ownerPw = OWNER_PW;
  if (page.url().includes('change-password')) {
    await page.getByLabel('Current password').fill(OWNER_PW);
    await page.getByLabel('New password').fill(NEW_PW);
    await page.getByRole('button', { name: 'Save password' }).click();
    ownerPw = NEW_PW;
    await page.waitForURL(/dashboard/);
  }
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();

  // Plan.
  await page.goto('/plans');
  await page.getByRole('button', { name: 'New plan' }).click();
  const planName = `Monthly ${run}`;
  await page.getByLabel('Name').fill(planName);
  await page.getByLabel('Duration (days)').fill('30');
  await page.getByLabel('Price (₹)').fill('1500');
  await page.getByRole('button', { name: 'Save' }).click();
  await expect(page.getByText(planName)).toBeVisible();

  // Member + membership + payment.
  await page.goto('/members');
  await page.getByRole('button', { name: 'Add member' }).click();
  await page.getByLabel('Full name').fill(`E2E Member ${run}`);
  await page.getByLabel('Phone').fill(phone);
  await page.getByRole('button', { name: 'Save' }).click();
  await expect(page.getByRole('heading', { name: new RegExp(`E2E Member ${run}`) })).toBeVisible();
  await page.getByRole('button', { name: 'Assign membership' }).click();
  const optValue = await page.locator('#as-plan option', { hasText: planName }).getAttribute('value');
  await page.getByLabel('Plan').selectOption(optValue as string);
  await page.getByRole('button', { name: 'Assign' }).click();
  await expect(page.getByText(planName).first()).toBeVisible();
  await page.getByRole('button', { name: 'Record payment' }).click();
  await page.getByLabel('Amount (₹)').fill('1500');
  await page.getByRole('button', { name: 'Record', exact: true }).click();
  await expect(page.getByText('Payment recorded')).toBeVisible();
  await page.getByRole('button', { name: 'Done' }).click();

  // Invite link -> member activates.
  const [inviteRes] = await Promise.all([
    page.waitForResponse((r) => r.url().includes('/invite') && r.request().method() === 'POST'),
    page.getByRole('button', { name: 'Create invite link' }).click(),
  ]);
  const invite = (await inviteRes.json()) as { loginId: string; url: string };
  const link = invite.url;
  const loginId = invite.loginId;
  expect(link).toContain('/activate?token=');

  const ctx = await browser.newContext({ viewport: { width: 360, height: 740 } });
  const mp = await ctx.newPage();
  await mp.goto(new URL(link).pathname + new URL(link).search);
  await mp.getByLabel('New password', { exact: true }).fill('MemberPass123');
  await mp.getByLabel('Confirm new password').fill('MemberPass123');
  await mp.getByRole('button', { name: 'Activate' }).click();
  await mp.waitForURL(/login/);

  await login(mp, loginId, 'MemberPass123');
  await mp.waitForURL(/\/me/);
  await expect(mp.getByText(planName)).toBeVisible();

  // Isolation: a member is bounced from staff screens and the API refuses them.
  await mp.goto('/members');
  await mp.waitForURL(/\/me/);
  const status = await mp.evaluate(async () => (await fetch('/api/v1/members', { credentials: 'same-origin' })).status);
  expect(status).toBe(403);
  await ctx.close();
  expect(ownerPw.length).toBeGreaterThan(0);
});
