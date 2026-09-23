import { test, expect } from '@playwright/test';
import { LibraryPage } from './pages/library.page';
import { acceptDialogs } from './support/dialogs';
import { jpegFile, pngFile, textFile } from './support/files';
import { uniqueName } from './support/names';

test.describe('photos library', () => {
  test('uploads images and rejects files from another silo', async ({ page }) => {
    const library = new LibraryPage(page);
    await library.goto('/photos');
    await expect(page.getByRole('heading', { name: 'Photos & videos' })).toBeVisible();
    await expect(library.dropzone).toContainText('Drag and drop to upload into photos & videos');

    const photo = pngFile(`${uniqueName('shot')}.png`);
    await library.upload(photo);
    await expect(library.ok).toContainText('Uploaded 1 file');
    await expect(library.fileLink(photo.name)).toBeVisible();
    await expect(library.card(photo.name).getByText('photo', { exact: true })).toBeVisible();
    await expect(library.card(photo.name).getByRole('img', { name: photo.name })).toBeVisible();

    await library.upload(textFile(`${uniqueName('notes')}.txt`));
    await expect(library.error).toContainText('belong in another section');
  });

  test('uploads several photos at once', async ({ page }) => {
    const library = new LibraryPage(page);
    await library.goto('/photos');
    const first = pngFile(`${uniqueName('a')}.png`);
    const second = jpegFile(`${uniqueName('b')}.jpg`);
    await library.upload([first, second]);
    await expect(library.ok).toContainText('Uploaded 2 files');
    await expect(library.fileLink(first.name)).toBeVisible();
    await expect(library.fileLink(second.name)).toBeVisible();
  });

  test('creates, filters, renames, and deletes an album', async ({ page }) => {
    const library = new LibraryPage(page);
    acceptDialogs(page);
    await library.goto('/photos');
    const album = uniqueName('Holiday');
    const renamed = `${album}-renamed`;
    const photo = pngFile(`${uniqueName('album-shot')}.png`);
    await library.createAlbum(album);
    await library.upload(photo);
    await expect(library.fileLink(photo.name)).toBeVisible();

    await page.locator('.filters').getByRole('combobox').click();
    await page.getByRole('option', { name: 'All albums' }).click();
    await expect(library.fileLink(photo.name)).toBeVisible();
    await page.locator('.filters').getByRole('combobox').click();
    await page.getByRole('option', { name: album }).click();
    await expect(library.fileLink(photo.name)).toBeVisible();

    await page.getByRole('button', { name: 'Rename album' }).click();
    const rename = page.getByRole('dialog', { name: 'Rename' });
    await rename.locator('input').fill(renamed);
    await rename.getByRole('button', { name: 'Save' }).click();
    await expect(page.locator('.filters')).toContainText(renamed);

    await page.getByRole('button', { name: 'Delete album' }).click();
    await expect(library.fileLink(photo.name)).toBeVisible();
  });

  test('renames, moves, shares, and opens a photo', async ({ page }) => {
    const library = new LibraryPage(page);
    await page.addInitScript(() => {
      Object.defineProperty(navigator, 'share', { configurable: true, value: undefined });
      Object.defineProperty(navigator, 'canShare', { configurable: true, value: undefined });
    });
    await library.goto('/photos');
    const original = pngFile(`${uniqueName('orig')}.png`);
    const renamed = `${uniqueName('renamed')}.png`;
    const album = uniqueName('MoveInto');
    await library.upload(original);
    await expect(library.fileLink(original.name)).toBeVisible();

    await library.selectFile(original.name);
    await library.renameSelected(renamed);
    await expect(library.fileLink(renamed)).toBeVisible();

    await library.selectFile(renamed);
    await library.openManageMenu();
    await library.chooseMenu('Move / album');
    const move = page.getByRole('dialog', { name: 'Move to album' });
    await move.getByPlaceholder('Or create album').fill(album);
    await move.getByRole('button', { name: 'Create' }).click();
    await move.getByRole('button', { name: 'Move' }).click();
    await expect(library.ok).toContainText('Updated 1 item');

    await library.selectFile(renamed);
    await library.openManageMenu();
    const download = page.waitForEvent('download');
    await library.chooseMenu('Share');
    expect((await download).suggestedFilename()).toBe(renamed);
    await expect(library.ok).toContainText('Prepared 1 file to share');

    await library.selectFile(renamed);
    await library.openManageMenu();
    await library.chooseMenu('Share with account');
    await expect(page.getByRole('dialog', { name: 'Share with account' })).toBeVisible();
    await page.getByRole('dialog', { name: 'Share with account' }).getByRole('button', { name: 'Cancel' }).click();

    await library.fileLink(renamed).click();
    await expect(page).toHaveURL(/\/player\//);
    await expect(page.getByRole('img', { name: renamed })).toBeVisible();
  });
});
