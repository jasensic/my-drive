import { test, expect } from '@playwright/test';
import { LoginPage } from './pages/login.page';
import { ShellPage } from './pages/shell.page';
import { ADMIN_PASSWORD, ADMIN_USERNAME } from './support/credentials';
import { uniqueName } from './support/names';

test.describe('auth', () => {
  test('redirects anonymous visitors to login', async ({ page }) => {
    await page.goto('/photos');
    await expect(page).toHaveURL(/\/login/);
    await expect(page.getByText('Sign in to my-drive')).toBeVisible();
  });

  test('rejects invalid credentials', async ({ page }) => {
    const login = new LoginPage(page);
    await login.goto();
    await expect(page.getByText('Sign in to my-drive')).toBeVisible();
    await login.fill(ADMIN_USERNAME, 'wrong-password');
    await login.submit('Login').click();
    await expect(login.error).toContainText('invalid credentials');
  });

  test('logs in as the admin', async ({ page }) => {
    const login = new LoginPage(page);
    const shell = new ShellPage(page);
    await login.goto();
    await login.fill(ADMIN_USERNAME, ADMIN_PASSWORD);
    await login.submit('Login').click();
    await expect(page).toHaveURL(/\/photos/);
    await expect(shell.userChip).toHaveText(ADMIN_USERNAME);
    await expect(page.getByRole('heading', { name: 'Photos & videos' })).toBeVisible();
  });

  test('registers a new account', async ({ page }) => {
    const login = new LoginPage(page);
    const shell = new ShellPage(page);
    await login.goto();
    await login.modeToggle('Create account').click();
    await expect(page.getByText('Create account').first()).toBeVisible();
    const username = uniqueName('user');
    await login.fill(username, ADMIN_PASSWORD);
    await login.submit('Create account').click();
    await expect(page).toHaveURL(/\/photos/);
    await expect(shell.userChip).toHaveText(username);
  });
});
