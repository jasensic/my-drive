import { test, expect } from '@playwright/test';
import { LibraryPage } from './pages/library.page';
import { wavFile } from './support/files';
import { uniqueName } from './support/names';

test.describe('music library', () => {
  test('shows search UI and surfaces a search failure', async ({ page }) => {
    const library = new LibraryPage(page);
    await page.route('**/v1/music/search', async (route) => {
      await route.fulfill({
        status: 502,
        contentType: 'application/json',
        body: JSON.stringify({ error: 'musicdl returned 502' }),
      });
    });
    await library.goto('/music');
    await expect(page.getByRole('heading', { name: 'Music' })).toBeVisible();
    await expect(page.getByText('Search songs')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Search' })).toBeDisabled();
    await library.searchKeyword().fill('e2e keyword');
    await page.getByRole('button', { name: 'Search' }).click();
    await expect(library.error).toContainText('musicdl returned 502');
  });

  test('downloads a mocked search result into the library', async ({ page }) => {
    const library = new LibraryPage(page);
    await page.route('**/v1/music/search', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          search_id: 'search-e2e',
          tracks: [
            {
              id: 'track-e2e',
              source: 'NeteaseMusicClient',
              song_name: 'Mock Track',
              singers: 'E2E Artist',
              album: 'E2E Album',
              duration: '3:00',
              file_size: '3 MB',
              ext: 'mp3',
            },
          ],
        }),
      });
    });
    await page.route('**/v1/music/import', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: '11111111-1111-1111-1111-111111111111',
          album_id: null,
          name: 'Mock Track.mp3',
          size: 2048,
          mime: 'audio/mpeg',
          checksum: 'abc',
          media_kind: 'audio',
          created_at: '2026-01-01T00:00:00Z',
          uploaded_at: '2026-01-01T00:00:00Z',
          deleted_at: null,
          purge_at: null,
          content_url: '/v1/files/11111111-1111-1111-1111-111111111111/content',
          thumbnail_url: null,
        }),
      });
    });
    await library.goto('/music');
    await library.searchKeyword().fill('mock song');
    await page.getByRole('button', { name: 'Search' }).click();
    await expect(page.getByText('Mock Track')).toBeVisible();
    await expect(page.getByText('E2E Artist')).toBeVisible();
    await page.getByRole('button', { name: 'Download' }).click();
    await expect(library.ok).toContainText('Saved Mock Track.mp3 to the music library');
  });

  test('plays an uploaded track in the now-playing bar', async ({ page }) => {
    const library = new LibraryPage(page);
    await library.goto('/music');
    const track = wavFile(`${uniqueName('play')}.wav`);
    await library.upload(track);
    await expect(library.fileLink(track.name)).toBeVisible();
    await library.card(track.name).getByRole('button', { name: 'Play' }).click();
    const bar = library.nowPlaying();
    await expect(bar).toBeVisible();
    await expect(bar.getByRole('link', { name: track.name })).toBeVisible();
    await expect(bar.getByRole('button', { name: 'Previous' })).toBeVisible();
    await expect(bar.getByRole('button', { name: 'Next' })).toBeVisible();
    await expect(bar.getByRole('slider', { name: 'Seek' })).toBeVisible();
    await bar.getByRole('button', { name: 'Stop' }).click();
    await expect(bar).toHaveCount(0);
  });

  test('keeps audio playing from the player page', async ({ page }) => {
    const library = new LibraryPage(page);
    await library.goto('/music');
    const track = wavFile(`${uniqueName('player-audio')}.wav`);
    await library.upload(track);
    await library.fileLink(track.name).click();
    await expect(page).toHaveURL(/\/player\//);
    await expect(page.getByText('This track keeps playing while you browse the rest of my-drive.')).toBeVisible();
    await expect(library.nowPlaying().getByRole('link', { name: track.name })).toBeVisible();
    await page.getByRole('button', { name: 'Back' }).click();
    await expect(page).toHaveURL(/\/music/);
  });
});
