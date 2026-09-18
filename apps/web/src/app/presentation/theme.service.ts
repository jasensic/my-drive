import { Injectable, signal } from '@angular/core';
import { ThemeMode } from '../domain/models';

const STORAGE_KEY = 'mydrive.theme';

@Injectable()
export class ThemeModeService {
  readonly mode = signal<ThemeMode>(this.readInitial());

  constructor() {
    this.apply(this.mode());
  }

  toggle() {
    this.set(this.mode() === 'dark' ? 'light' : 'dark');
  }

  set(mode: ThemeMode) {
    this.mode.set(mode);
    localStorage.setItem(STORAGE_KEY, mode);
    this.apply(mode);
  }

  private apply(mode: ThemeMode) {
    const root = document.documentElement;
    root.classList.toggle('app-dark', mode === 'dark');
    root.style.colorScheme = mode;
  }

  private readInitial(): ThemeMode {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored === 'light' || stored === 'dark') {
      return stored;
    }
    return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }
}
