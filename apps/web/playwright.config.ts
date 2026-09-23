import { defineConfig, devices } from '@playwright/test';
import path from 'node:path';

const authState = path.join(__dirname, 'e2e', '.auth', 'user.json');

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 30_000,
  expect: { timeout: 15_000 },
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: process.env['E2E_BASE_URL'] ?? 'http://localhost:4200',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [
    { name: 'setup', testMatch: /auth\.setup\.ts/ },
    {
      name: 'anon',
      testMatch: /auth\.spec\.ts/,
      dependencies: ['setup'],
      use: { storageState: { cookies: [], origins: [] } },
    },
    {
      name: 'chromium',
      dependencies: ['setup'],
      testIgnore: [/auth\.setup\.ts/, /auth\.spec\.ts/],
      use: {
        ...devices['Desktop Chrome'],
        storageState: authState,
      },
    },
  ],
});
