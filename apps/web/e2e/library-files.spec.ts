import { test, expect } from '@playwright/test';
import { LibraryPage } from './pages/library.page';
import { pngFile, textFile } from './support/files';
import { uniqueName } from './support/names';

test.describe('files library', () => {
  test('uploads documents and keeps them out of photos', async ({ page }) => {
    const library = new LibraryPage(page);
    await library.goto('/files');
    await expect(page.getByRole('heading', { name: 'Files' })).toBeVisible();
    const note = textFile(`${uniqueName('note')}.txt`);
    await library.upload(note);
    await expect(library.ok).toContainText('Uploaded 1 file');
    await expect(library.fileLink(note.name)).toBeVisible();
    await expect(library.card(note.name).getByText('file', { exact: true })).toBeVisible();

    await library.upload(pngFile(`${uniqueName('pic')}.png`));
    await expect(library.error).toContainText('belong in another section');

    await library.goto('/photos');
    await expect(library.fileLink(note.name)).toHaveCount(0);
  });

  test('trashes and restores a document', async ({ page }) => {
    const library = new LibraryPage(page);
    await library.goto('/files');
    const note = textFile(`${uniqueName('restore-me')}.txt`);
    await library.upload(note);
    await library.card(note.name).getByRole('button', { name: 'Move to trash' }).click();
    await expect(library.ok).toContainText(`Moved ${note.name} to trash`);
    await expect(library.fileLink(note.name)).toHaveCount(0);

    await library.openTrash();
    await expect(page.getByText('Trash for this silo only')).toBeVisible();
    await library.card(note.name).getByRole('button', { name: 'Restore' }).click();
    await expect(library.ok).toContainText(`Restored ${note.name}`);
    await library.backToLibrary();
    await expect(library.fileLink(note.name)).toBeVisible();
  });
});
