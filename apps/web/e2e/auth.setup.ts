import { test, expect } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
import { LoginPage } from './pages/login.page';
import { AUTH_STATE_PATH } from './support/auth';
import { ADMIN_PASSWORD, ADMIN_USERNAME } from './support/credentials';

test('authenticate admin', async ({ page }) => {
  const login = new LoginPage(page);
  await login.goto();
  await expect(page.getByText(/Create admin|Sign in to my-drive/)).toBeVisible();

  if (await page.getByText('Create admin').isVisible()) {
    await login.fill(ADMIN_USERNAME, 'short');
    await login.submit('Complete setup').click();
    await expect(login.error).toContainText('password must be at least 8 characters');
    await login.fill(ADMIN_USERNAME, ADMIN_PASSWORD);
    await login.submit('Complete setup').click();
  } else {
    await login.fill(ADMIN_USERNAME, ADMIN_PASSWORD);
    await login.submit('Login').click();
  }

  await expect(page.getByRole('button', { name: 'Upload' })).toBeVisible();
  fs.mkdirSync(path.dirname(AUTH_STATE_PATH), { recursive: true });
  await page.context().storageState({ path: AUTH_STATE_PATH });
});
