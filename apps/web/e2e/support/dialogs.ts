import { Page } from '@playwright/test';

export function acceptDialogs(page: Page): void {
  page.on('dialog', (dialog) => {
    void dialog.accept();
  });
}
