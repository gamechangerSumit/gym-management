import { defineConfig } from '@playwright/test';

// Run against a running stack:  docker compose up -d --build   then   E2E_OWNER_PASSWORD=... npx playwright test
export default defineConfig({
  testDir: './tests',
  timeout: 60_000,
  workers: 1,
  use: { baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:8080', viewport: { width: 390, height: 800 }, trace: 'retain-on-failure' },
});
