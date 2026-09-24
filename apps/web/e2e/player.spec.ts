import { test, expect } from '@playwright/test';
import { LibraryPage } from './pages/library.page';
import { PlayerPage } from './pages/player.page';
import { pngFile, textFile } from './support/files';
import { uniqueName } from './support/names';

test.describe('player', () => {
  test('shows a photo and returns to photos', async ({ page }) => {
    const library = new LibraryPage(page);
    const player = new PlayerPage(page);
    await library.goto('/photos');
    const photo = pngFile(`${uniqueName('player-photo')}.png`);
    await library.upload(photo);
    await library.fileLink(photo.name).click();
    await expect(page).toHaveURL(/\/player\//);
    await expect(player.image(photo.name)).toBeVisible();
    await player.back.click();
    await expect(page).toHaveURL(/\/photos/);
  });

  test('offers a download for a document', async ({ page }) => {
    const library = new LibraryPage(page);
    const player = new PlayerPage(page);
    await library.goto('/files');
    const note = textFile(`${uniqueName('player-doc')}.txt`);
    await library.upload(note);
    await library.fileLink(note.name).click();
    await expect(player.download()).toBeVisible();
    await player.back.click();
    await expect(page).toHaveURL(/\/files/);
  });

  test('shows a missing state for an unknown file', async ({ page }) => {
    const player = new PlayerPage(page);
    await player.goto('00000000-0000-0000-0000-000000000000');
    await expect(player.missing).toBeVisible();
  });
});
