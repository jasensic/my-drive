import { Locator, Page } from '@playwright/test';

export class ShellPage {
  readonly userChip: Locator;
  readonly logout: Locator;
  readonly themeToggle: Locator;
  readonly menuButton: Locator;
  readonly brand: Locator;

  constructor(private readonly page: Page) {
    this.userChip = page.locator('.user-chip');
    this.logout = page.getByRole('button', { name: 'Logout' });
    this.themeToggle = page.getByRole('button', { name: /^(Dark mode|Light mode)/ });
    this.menuButton = page.getByRole('button', { name: /Open menu|Close menu/ });
    this.brand = page.locator('a.brand');
  }

  navLink(name: string): Locator {
    return this.page.getByRole('navigation', { name: 'Primary' }).getByRole('link', { name, exact: true });
  }

  async openMobileNav(): Promise<void> {
    if ((await this.menuButton.getAttribute('aria-label')) === 'Open menu') {
      await this.menuButton.click();
    }
  }
}
