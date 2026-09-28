import { expect, Locator, Page } from '@playwright/test';

export class ShellPage {
  readonly userChip: Locator;
  readonly logout: Locator;
  readonly themeToggle: Locator;
  readonly menuButton: Locator;
  readonly brand: Locator;
  readonly searchButton: Locator;
  readonly searchInput: Locator;

  constructor(private readonly page: Page) {
    this.userChip = page.locator('.user-chip');
    this.logout = page.getByRole('button', { name: 'Logout' });
    this.themeToggle = page.getByRole('button', { name: /^(Dark mode|Light mode)/ });
    this.menuButton = page.getByRole('button', { name: /Open menu|Close menu/ });
    this.brand = page.locator('a.brand');
    this.searchButton = page.locator('button.search-trigger');
    this.searchInput = page.getByPlaceholder('Search by file name');
  }

  navLink(name: string): Locator {
    return this.page.getByRole('navigation', { name: 'Primary' }).getByRole('link', { name, exact: true });
  }

  async openMobileNav(): Promise<void> {
    if ((await this.menuButton.getAttribute('aria-label')) === 'Open menu') {
      await this.menuButton.click();
    }
  }

  async openFileSearch(via: 'button' | 'keyboard' = 'keyboard'): Promise<void> {
    if (via === 'button') {
      await this.searchButton.click();
    } else {
      await this.page.keyboard.press('Control+k');
    }
    await expect(this.searchInput).toBeVisible();
  }

  searchHit(name: string): Locator {
    return this.page.locator('.file-search-hit').filter({ hasText: name });
  }
}
