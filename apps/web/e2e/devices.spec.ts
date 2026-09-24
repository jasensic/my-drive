import { test, expect } from '@playwright/test';
import { DevicesPage } from './pages/devices.page';
import { uniqueName } from './support/names';

test.describe('devices', () => {
  test('requires a device name', async ({ page }) => {
    const devices = new DevicesPage(page);
    await devices.goto();
    await expect(page.getByRole('heading', { name: /Devices/ })).toBeVisible();
    await page.getByRole('button', { name: 'Register device' }).click();
    await expect(devices.error).toContainText('Device name is required');
  });

  test('registers a device and persists sync rules', async ({ page }) => {
    const devices = new DevicesPage(page);
    await devices.goto();
    const name = uniqueName('Phone');
    await devices.register(name);
    const card = devices.deviceCard(name);
    await expect(card).toBeVisible();
    await expect(card).toContainText('Never synced');

    await card.getByRole('button', { name: 'Edit rules' }).click();
    await expect(card.getByText('Sync rules')).toBeVisible();
    await expect(card.getByText('Photos')).toBeVisible();
    await expect(card.getByText('Videos')).toBeVisible();
    await expect(card.getByText('Audio')).toBeVisible();
    await expect(card.getByText('Files')).toBeVisible();
    await expect(card.getByText('Include all')).toHaveCount(4);

    const profileName = `${name} profile`;
    await card.locator('#profile-name').fill(profileName);
    await card.getByRole('button', { name: 'Save profile' }).click();
    await expect(card.getByRole('button', { name: 'Edit rules' })).toBeVisible();

    await card.getByRole('button', { name: 'Edit rules' }).click();
    await expect(card.locator('#profile-name')).toHaveValue(profileName);
    await card.getByRole('button', { name: 'Cancel' }).click();
    await expect(card.getByRole('button', { name: 'Edit rules' })).toBeVisible();
    await expect(card.getByText('Sync rules')).toHaveCount(0);
  });
});
