import { defineConfig } from '@playwright/test';

// E2E specs are not run in CI sandboxes without browsers. Provide:
//   E2E_BASE_URL (default http://localhost:5173), E2E_OWNER_LOGIN, E2E_OWNER_PASSWORD,
//   E2E_MEMBER_LOGIN, E2E_MEMBER_PASSWORD, E2E_OTHER_MEMBER_NAME (optional)
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  fullyParallel: false,
  workers: 1,
  reporter: 'list',
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173',
    trace: 'retain-on-failure',
  },
});
