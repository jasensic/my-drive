import { test, expect } from '@playwright/test';
import path from 'node:path';

test('setup, upload, and create a sync profile', async ({ page }) => {
  await page.goto('/login');
  await page.locator('#user').fill('admin');
  await page.locator('#pass').fill('password123');
  await page.getByRole('button', { name: /Complete setup|Login/ }).click();
  await expect(page.getByRole('button', { name: 'Upload' })).toBeVisible({ timeout: 15_000 });

  const filePath = path.join(__dirname, 'fixtures', 'tiny.jpg');
  await page.locator('input[type="file"]').setInputFiles(filePath);
  await expect(page.getByText('tiny.jpg')).toBeVisible({ timeout: 15_000 });

  await page.getByRole('link', { name: 'Devices' }).click();
  await expect(page.getByPlaceholder('Phone A')).toBeVisible();
  await page.getByPlaceholder('Phone A').fill('Device A');
  await page.getByRole('button', { name: 'Register device' }).click();
  await expect(page.getByText('Device A')).toBeVisible();
  await page.getByRole('button', { name: 'Edit rules' }).click();
  await expect(page.getByText('Sync rules')).toBeVisible();
});
