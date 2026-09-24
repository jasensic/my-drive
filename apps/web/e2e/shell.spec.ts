import { test, expect } from '@playwright/test';
import { LoginPage } from './pages/login.page';
import { ShellPage } from './pages/shell.page';
import { ADMIN_USERNAME } from './support/credentials';

const NAV = [
  { name: 'Music', url: /\/music/, heading: 'Music' },
  { name: 'Photos & videos', url: /\/photos/, heading: 'Photos & videos' },
  { name: 'Files', url: /\/files/, heading: 'Files' },
  { name: 'Import', url: /\/imports/, heading: 'Cloud imports' },
  { name: 'Devices', url: /\/devices/, heading: 'Devices' },
  { name: 'App updates', url: /\/app-updates/, heading: 'Android app updates' },
] as const;

test.describe('shell', () => {
  test('redirects the home and library routes to photos', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveURL(/\/photos/);
    await page.goto('/library');
    await expect(page).toHaveURL(/\/photos/);
  });

  test('keeps an authenticated visit to login on photos', async ({ page }) => {
    await page.goto('/login');
    await expect(page).toHaveURL(/\/photos/);
    await expect(page.getByRole('heading', { name: 'Photos & videos' })).toBeVisible();
  });

  test('navigates every primary section', async ({ page }) => {
    const shell = new ShellPage(page);
    await page.goto('/photos');
    await expect(shell.userChip).toHaveText(ADMIN_USERNAME);
    for (const item of NAV) {
      await shell.navLink(item.name).click();
      await expect(page).toHaveURL(item.url);
      await expect(page.getByRole('heading', { name: item.heading })).toBeVisible();
    }
    await shell.brand.click();
    await expect(page).toHaveURL(/\/photos/);
  });

  test('toggles light and dark theme', async ({ page }) => {
    const shell = new ShellPage(page);
    await page.goto('/photos');
    const before = (await shell.themeToggle.textContent()) ?? '';
    await shell.themeToggle.click();
    if (before.includes('Dark mode')) {
      await expect(page.locator('html')).toHaveClass(/app-dark/);
      await expect.poll(() => page.evaluate(() => localStorage.getItem('mydrive.theme'))).toBe('dark');
      await expect(shell.themeToggle).toHaveText(/Light mode/);
    } else {
      await expect(page.locator('html')).not.toHaveClass(/app-dark/);
      await expect.poll(() => page.evaluate(() => localStorage.getItem('mydrive.theme'))).toBe('light');
      await expect(shell.themeToggle).toHaveText(/Dark mode/);
    }
  });

  test('toggles theme from the login screen after logout', async ({ page }) => {
    const shell = new ShellPage(page);
    const login = new LoginPage(page);
    await page.goto('/photos');
    await shell.logout.click();
    await expect(page).toHaveURL(/\/login/);
    const wasDark = (await login.themeToggle.getAttribute('aria-label')) === 'Switch to light mode';
    await login.themeToggle.click();
    if (wasDark) {
      await expect(page.locator('html')).not.toHaveClass(/app-dark/);
    } else {
      await expect(page.locator('html')).toHaveClass(/app-dark/);
    }
  });

  test('logs out and blocks protected routes', async ({ page }) => {
    const shell = new ShellPage(page);
    await page.goto('/photos');
    await shell.logout.click();
    await expect(page).toHaveURL(/\/login/);
    await page.goto('/music');
    await expect(page).toHaveURL(/\/login/);
  });

  test('opens the mobile navigation', async ({ page }) => {
    const shell = new ShellPage(page);
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/photos');
    await expect(shell.menuButton).toBeVisible();
    await shell.openMobileNav();
    await expect(shell.navLink('Devices')).toBeVisible();
    await shell.navLink('Devices').click();
    await expect(page).toHaveURL(/\/devices/);
  });
});
