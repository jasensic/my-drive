import { Locator, Page } from '@playwright/test';

export class ImportsPage {
  readonly title: Locator;
  readonly error: Locator;

  constructor(private readonly page: Page) {
    this.title = page.getByRole('heading', { name: 'Cloud imports' });
    this.error = page.locator('.banner.error');
  }

  async goto(): Promise<void> {
    await this.page.goto('/imports');
  }

  tab(name: string): Locator {
    return this.page.getByRole('tab', { name });
  }
}
