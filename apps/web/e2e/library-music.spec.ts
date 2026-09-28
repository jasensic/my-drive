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

  test('cancels a search and unlocks the form', async ({ page }) => {
    const library = new LibraryPage(page);
    await page.route('**/v1/music/search', async (route) => {
      await new Promise(() => {});
    });
    await library.goto('/music');
    await library.searchKeyword().fill('stuck query');
    await page.getByRole('button', { name: 'Search' }).click();
    await expect(page.getByRole('button', { name: 'Cancel' })).toBeVisible();
    await expect(library.searchKeyword()).toBeDisabled();
    await page.getByRole('button', { name: 'Cancel' }).click();
    await expect(page.getByRole('button', { name: 'Search' })).toBeEnabled();
    await expect(library.searchKeyword()).toBeEnabled();
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
          done: true,
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

  test('shows the first 3 songs and loads more on scroll', async ({ page }) => {
    const library = new LibraryPage(page);
    const tracks = Array.from({ length: 6 }, (_, index) => {
      const number = String(index + 1).padStart(2, '0');
      return {
        id: `track-${number}`,
        source: 'NeteaseMusicClient',
        song_name: `Paged Song ${number}`,
        singers: 'E2E Artist',
        album: 'E2E Album',
        duration: '3:00',
        file_size: '3 MB',
        ext: 'mp3',
      };
    });
    await page.route('**/v1/music/search', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ search_id: 'paged-e2e', tracks, done: true }),
      });
    });
    await library.goto('/music');
    await library.searchKeyword().fill('paged song');
    await page.getByRole('button', { name: 'Search' }).click();
    await expect(page.getByText('Paged Song 01')).toBeVisible();
    await expect(page.getByText('Paged Song 03')).toBeVisible();
    await expect(page.getByText('Paged Song 04')).toHaveCount(0);
    await page.locator('.search-results').hover();
    await page.mouse.wheel(0, 400);
    await expect(page.getByText('Paged Song 04')).toBeVisible();
    await expect(page.getByText('Paged Song 06')).toBeVisible();
  });

  test('loads a Spotify playlist and downloads every track', async ({ page }) => {
    const library = new LibraryPage(page);
    let imports = 0;
    await page.route('**/v1/music/playlist', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          search_id: 'playlist-e2e',
          tracks: [
            {
              id: 'pl-1',
              source: 'SpotifyMusicClient',
              song_name: 'Playlist One',
              singers: 'Playlist Artist',
              album: 'Playlist Album',
              duration: '2:00',
              file_size: '4 MB',
              ext: 'mp3',
            },
            {
              id: 'pl-2',
              source: 'SpotifyMusicClient',
              song_name: 'Playlist Two',
              singers: 'Playlist Artist',
              album: 'Playlist Album',
              duration: '2:30',
              file_size: '4 MB',
              ext: 'mp3',
            },
          ],
          done: true,
        }),
      });
    });
    await page.route('**/v1/music/import', async (route) => {
      imports += 1;
      const body = route.request().postDataJSON() as { track_id: string };
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: '22222222-2222-2222-2222-222222222222',
          album_id: null,
          name: `${body.track_id}.mp3`,
          size: 2048,
          mime: 'audio/mpeg',
          checksum: 'abc',
          media_kind: 'audio',
          created_at: '2026-01-01T00:00:00Z',
          uploaded_at: '2026-01-01T00:00:00Z',
          deleted_at: null,
          purge_at: null,
          content_url: '/v1/files/22222222-2222-2222-2222-222222222222/content',
          thumbnail_url: null,
        }),
      });
    });
    await library.goto('/music');
    await page.getByPlaceholder('Spotify playlist URL').fill(
      'https://open.spotify.com/playlist/37i9dQZF1E8NWHOpySOxQd',
    );
    await page.getByRole('button', { name: 'Load playlist' }).click();
    await expect(page.getByText('Playlist One')).toBeVisible();
    await expect(page.getByText('Playlist Two')).toBeVisible();
    await page.getByRole('button', { name: 'Download all' }).click();
    await expect(library.ok).toContainText('Saved 2 tracks to the music library');
    expect(imports).toBe(2);
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
