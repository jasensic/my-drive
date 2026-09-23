import { Locator, Page } from '@playwright/test';
import { UploadPayload } from '../support/files';

export class AppUpdatesPage {
  readonly error: Locator;
  readonly ok: Locator;
  readonly changelog: Locator;
  readonly empty: Locator;
  readonly apkInput: Locator;

  constructor(private readonly page: Page) {
    this.error = page.locator('.banner.error');
    this.ok = page.locator('.banner.ok');
    this.changelog = page.locator('#changelog');
    this.empty = page.getByText('No app releases published yet.');
    this.apkInput = page.locator('p-card').filter({ hasText: 'Publish APK' }).locator('input[type="file"]');
  }

  async goto(): Promise<void> {
    await this.page.goto('/app-updates');
  }

  publish(): Locator {
    return this.page.getByRole('button', { name: 'Publish' });
  }

  async chooseApk(file: UploadPayload): Promise<void> {
    await this.apkInput.setInputFiles(file);
  }

  releaseCard(versionName: string): Locator {
    return this.page.locator('p-card').filter({ hasText: `v${versionName}` });
  }
}
