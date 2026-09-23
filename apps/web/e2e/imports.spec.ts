import { test, expect } from '@playwright/test';
import { ImportsPage } from './pages/imports.page';

test.describe('cloud imports', () => {
  test('shows unconfigured Google and Spotify tabs', async ({ page }) => {
    const imports = new ImportsPage(page);
    await imports.goto();
    await expect(imports.title).toBeVisible();
    await expect(imports.tab('Google Photos')).toBeVisible();
    await expect(page.getByText('GOOGLE_OAUTH_CLIENT_ID')).toBeVisible();

    await imports.tab('Google Drive').click();
    await expect(page.getByText('GOOGLE_API_KEY')).toBeVisible();

    await imports.tab('Spotify').click();
    await expect(page.getByText('SPOTIFY_CLIENT_ID')).toBeVisible();
  });

  test('spotify callback reports missing auth params', async ({ page }) => {
    await page.goto('/imports/spotify/callback');
    await expect(page.getByText('Missing Spotify authorization code.')).toBeVisible();
    await page.getByRole('link', { name: 'Back to imports' }).click();
    await expect(page).toHaveURL(/\/imports$/);
  });

  test('spotify callback surfaces provider errors', async ({ page }) => {
    await page.goto('/imports/spotify/callback?error=access_denied');
    await expect(page.getByText('Spotify authorization failed: access_denied')).toBeVisible();
  });
});
