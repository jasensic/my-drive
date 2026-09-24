import { expect, Locator, Page } from '@playwright/test';
import { UploadPayload } from '../support/files';

export type LibrarySiloPath = '/photos' | '/music' | '/files';

export class LibraryPage {
  readonly error: Locator;
  readonly ok: Locator;
  readonly dropzone: Locator;
  readonly fileInput: Locator;
  readonly albumName: Locator;

  constructor(private readonly page: Page) {
    this.error = page.locator('.banner.error');
    this.ok = page.locator('.banner.ok');
    this.dropzone = page.locator('.dropzone');
    this.fileInput = page.locator('.dropzone input[type="file"]');
    this.albumName = page.getByPlaceholder('New album');
  }

  async goto(silo: LibrarySiloPath = '/photos'): Promise<void> {
    await this.page.goto(silo);
  }

  title(name: string | RegExp): Locator {
    return this.page.getByRole('heading', { name });
  }

  empty(text: string): Locator {
    return this.page.locator('.empty-state').getByText(text);
  }

  fileLink(name: string): Locator {
    return this.page.getByRole('link', { name, exact: true });
  }

  card(name: string): Locator {
    return this.page.locator('p-card').filter({ has: this.fileLink(name) });
  }

  async upload(file: UploadPayload | UploadPayload[]): Promise<void> {
    await this.fileInput.setInputFiles(file);
  }

  async createAlbum(name: string): Promise<void> {
    await this.albumName.fill(name);
    await this.page.getByRole('button', { name: 'Create album' }).click();
    await expect(this.page.getByRole('button', { name: 'Rename album' })).toBeVisible();
  }

  async openTrash(): Promise<void> {
    await this.page.getByRole('button', { name: 'Trash', exact: true }).click();
  }

  async backToLibrary(): Promise<void> {
    await this.page.getByRole('button', { name: 'Back to library' }).click();
  }

  async selectFile(name: string): Promise<void> {
    const card = this.card(name);
    await expect(card).toBeVisible();
    const selected = await card.evaluate(
      (el) => el.classList.contains('selected') || Boolean(el.querySelector('.selected')),
    );
    if (!selected) {
      await card.locator('.p-checkbox').click();
    }
    await expect(this.page.getByRole('button', { name: 'Manage' })).toBeVisible();
  }

  async openManageMenu(): Promise<void> {
    await expect(this.page.getByRole('button', { name: 'Manage' })).toBeVisible();
    await this.page.getByRole('button', { name: 'Manage' }).click();
  }

  menuItem(label: string): Locator {
    return this.page.locator('.md-menu-item').filter({ has: this.page.getByText(label, { exact: true }) });
  }

  async chooseMenu(label: string): Promise<void> {
    await this.menuItem(label).click();
  }

  async renameSelected(nextName: string): Promise<void> {
    await this.openManageMenu();
    await this.chooseMenu('Rename');
    const dialog = this.page.getByRole('dialog', { name: 'Rename' });
    await dialog.locator('input').fill(nextName);
    await dialog.getByRole('button', { name: 'Save' }).click();
  }

  searchKeyword(): Locator {
    return this.page.getByPlaceholder('Artist or song');
  }

  nowPlaying(): Locator {
    return this.page.locator('.now-playing');
  }
}
