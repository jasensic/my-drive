import { test, expect } from '@playwright/test';
import { LibraryPage } from './pages/library.page';
import { acceptDialogs } from './support/dialogs';
import { pngFile, wavFile } from './support/files';
import { uniqueName } from './support/names';

test.describe('trash', () => {
  test('moves a photo to trash, restores it, and purges another', async ({ page }) => {
    const library = new LibraryPage(page);
    acceptDialogs(page);
    await library.goto('/photos');
    const keep = pngFile(`${uniqueName('keep')}.png`);
    const gone = pngFile(`${uniqueName('gone')}.png`);
    await library.upload([keep, gone]);
    await library.card(keep.name).getByRole('button', { name: 'Move to trash' }).click();
    await expect(library.fileLink(keep.name)).toHaveCount(0);

    await library.openTrash();
    await expect(library.fileLink(keep.name)).toBeVisible();
    await expect(library.card(keep.name).getByText(/Deletes /)).toBeVisible();
    await library.card(keep.name).getByRole('button', { name: 'Restore' }).click();
    await library.backToLibrary();
    await expect(library.fileLink(keep.name)).toBeVisible();

    await library.card(gone.name).getByRole('button', { name: 'Move to trash' }).click();
    await library.openTrash();
    await library.card(gone.name).getByRole('button', { name: 'Delete forever' }).click();
    await expect(library.ok).toContainText(`Deleted ${gone.name}`);
    await expect(library.fileLink(gone.name)).toHaveCount(0);
  });

  test('empties trash for one silo only', async ({ page }) => {
    const library = new LibraryPage(page);
    acceptDialogs(page);
    await library.goto('/photos');
    const photo = pngFile(`${uniqueName('empty-photo')}.png`);
    await library.upload(photo);
    await library.card(photo.name).getByRole('button', { name: 'Move to trash' }).click();

    await library.goto('/music');
    const track = wavFile(`${uniqueName('empty-track')}.wav`);
    await library.upload(track);
    await library.card(track.name).getByRole('button', { name: 'Move to trash' }).click();

    await library.goto('/photos');
    await library.openTrash();
    await expect(library.fileLink(photo.name)).toBeVisible();
    await expect(library.fileLink(track.name)).toHaveCount(0);
    await page.getByRole('button', { name: 'Empty trash' }).click();
    await expect(library.ok).toContainText('Removed');
    await expect(page.getByText('This silo trash is empty.')).toBeVisible();

    await library.goto('/music');
    await library.openTrash();
    await expect(library.fileLink(track.name)).toBeVisible();
  });
});
