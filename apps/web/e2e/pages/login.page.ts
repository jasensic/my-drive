import { Locator, Page } from '@playwright/test';

export class LoginPage {
  readonly username: Locator;
  readonly password: Locator;
  readonly error: Locator;
  readonly themeToggle: Locator;

  constructor(private readonly page: Page) {
    this.username = page.locator('#user');
    this.password = page.locator('#pass');
    this.error = page.locator('.banner.error');
    this.themeToggle = page.getByRole('button', { name: /Switch to (light|dark) mode/ });
  }

  async goto(): Promise<void> {
    await this.page.goto('/login');
  }

  header(name: string | RegExp): Locator {
    return this.page.getByText(name);
  }

  submit(name: string | RegExp): Locator {
    return this.page.locator('form').getByRole('button', { name });
  }

  modeToggle(name: string | RegExp): Locator {
    return this.page.getByRole('button', { name });
  }

  async fill(username: string, password: string): Promise<void> {
    await this.username.fill(username);
    await this.password.fill(password);
  }

  async authenticate(username: string, password: string): Promise<void> {
    await this.fill(username, password);
    await this.page.getByRole('button', { name: /Complete setup|Login|Create account/ }).click();
  }
}
