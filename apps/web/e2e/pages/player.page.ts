import { Locator, Page } from '@playwright/test';

export class PlayerPage {
  readonly back: Locator;
  readonly missing: Locator;

  constructor(private readonly page: Page) {
    this.back = page.getByRole('button', { name: 'Back' });
    this.missing = page.getByText('This file could not be opened.');
  }

  async goto(id: string): Promise<void> {
    await this.page.goto(`/player/${id}`);
  }

  heading(name: string): Locator {
    return this.page.getByText(name, { exact: true });
  }

  image(name: string): Locator {
    return this.page.getByRole('img', { name });
  }

  download(): Locator {
    return this.page.getByRole('link', { name: 'Download' });
  }
}
