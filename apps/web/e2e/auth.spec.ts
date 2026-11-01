import { test, expect } from '@playwright/test';
import { LoginPage } from './pages/login.page';
import { ShellPage } from './pages/shell.page';
import { UsersPage } from './pages/users.page';
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

  test('has no self sign-up', async ({ page }) => {
    const login = new LoginPage(page);
    await login.goto();
    await expect(page.getByText('Sign in to my-drive')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Create account' })).toHaveCount(0);
    await expect(page.getByText('Ask the person who set up this drive')).toBeVisible();
  });

  test('admin adds a user who can then sign in', async ({ page }) => {
    const login = new LoginPage(page);
    const shell = new ShellPage(page);
    const users = new UsersPage(page);
    await login.goto();
    await login.fill(ADMIN_USERNAME, ADMIN_PASSWORD);
    await login.submit('Login').click();
    await expect(page).toHaveURL(/\/photos/);
    await expect(shell.navLink('Users')).toBeVisible();

    await shell.navLink('Users').click();
    const username = uniqueName('user');
    await users.add(username, ADMIN_PASSWORD);
    await expect(users.userCard(username)).toContainText('Member');

    await shell.logout.click();
    await expect(page).toHaveURL(/\/login/);
    await expect(page.getByText('Sign in to my-drive')).toBeVisible();
    await login.fill(username, ADMIN_PASSWORD);
    await login.submit('Login').click();
    await expect(page).toHaveURL(/\/photos/);
    await expect(shell.userChip).toHaveText(username);
    await expect(shell.navLink('Users')).toHaveCount(0);
    await expect(shell.navLink('App updates')).toHaveCount(0);
  });
});
