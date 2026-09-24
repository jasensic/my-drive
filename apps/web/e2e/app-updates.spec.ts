import { test, expect } from '@playwright/test';
import { AppUpdatesPage } from './pages/app-updates.page';
import { acceptDialogs } from './support/dialogs';
import { apkFile, textFile } from './support/files';
import { uniqueName } from './support/names';

test.describe('app updates', () => {
  test('rejects a file that is not an APK', async ({ page }) => {
    const updates = new AppUpdatesPage(page);
    await updates.goto();
    await expect(page.getByRole('heading', { name: 'Android app updates' })).toBeVisible();
    await expect(updates.publish()).toBeDisabled();
    await updates.chooseApk(textFile('not-an-apk.txt'));
    await expect(updates.error).not.toHaveCount(0);
    await expect(updates.publish()).toBeDisabled();
  });

  test('inspects, publishes, updates, replaces, and deletes a release', async ({ page }) => {
    const updates = new AppUpdatesPage(page);
    acceptDialogs(page);
    await updates.goto();
    const versionName = uniqueName('1.0').replace(/[^0-9a-z.-]/gi, '').slice(0, 20) || '1.0.0';
    const versionCode = Math.floor(Date.now() / 1000);
    const nextCode = versionCode + 1;
    const nextName = `${versionName}.1`;

    await updates.changelog.fill('Initial e2e release');
    await updates.chooseApk(apkFile('app.apk', versionCode, versionName));
    await expect(page.getByText(`Detected v${versionName} (versionCode ${versionCode})`)).toBeVisible();
    await updates.publish().click();
    await expect(updates.ok).toContainText(`Published ${versionName} (${versionCode})`);
    const card = updates.releaseCard(versionName);
    await expect(card).toBeVisible();

    await card.locator('textarea').fill('Updated changelog');
    await card.getByRole('button', { name: 'Save' }).click();
    await expect(updates.ok).toContainText(`Updated ${versionName}`);

    await card.locator('input[type="file"]').setInputFiles(apkFile('app-next.apk', nextCode, nextName));
    await expect(updates.ok).toContainText(`Replaced APK with ${nextName} (${nextCode})`);
    await expect(updates.releaseCard(nextName)).toBeVisible();

    await updates.releaseCard(nextName).getByRole('button', { name: 'Delete' }).click();
    await expect(updates.ok).toContainText(`Deleted ${nextName}`);
  });
});
