import { Locator, Page } from '@playwright/test';

export class UsersPage {
  readonly username: Locator;
  readonly password: Locator;
  readonly error: Locator;

  constructor(private readonly page: Page) {
    this.username = page.locator('#new-user');
    this.password = page.locator('#new-pass');
    this.error = page.locator('.banner.error');
  }

  async goto(): Promise<void> {
    await this.page.goto('/users');
  }

  async add(username: string, password: string): Promise<void> {
    await this.username.fill(username);
    await this.password.fill(password);
    await this.page.getByRole('button', { name: 'Add user' }).click();
  }

  userCard(username: string): Locator {
    return this.page.locator('p-card').filter({ hasText: username });
  }
}
