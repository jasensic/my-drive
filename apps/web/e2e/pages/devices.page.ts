import { Locator, Page } from '@playwright/test';

export class DevicesPage {
  readonly name: Locator;
  readonly error: Locator;
  readonly empty: Locator;

  constructor(private readonly page: Page) {
    this.name = page.getByPlaceholder('Phone A');
    this.error = page.locator('.banner.error');
    this.empty = page.getByText('No devices registered yet.');
  }

  async goto(): Promise<void> {
    await this.page.goto('/devices');
  }

  async register(name: string): Promise<void> {
    await this.name.fill(name);
    await this.page.getByRole('button', { name: 'Register device' }).click();
  }

  deviceCard(name: string): Locator {
    return this.page.locator('p-card').filter({ hasText: name });
  }
}
