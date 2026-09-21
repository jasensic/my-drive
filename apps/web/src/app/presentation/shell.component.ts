import { Component, Inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { LucideDynamicIcon } from '@lucide/angular';
import { Toast } from 'primeng/toast';
import { SESSION_QUERY } from '../application/use-cases.tokens';
import type { SessionQuery } from '../application/use-cases.tokens';
import { MusicPlaybackService } from './music-playback.service';
import { NowPlayingBar } from './now-playing-bar';
import { ThemeModeService } from './theme.service';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Toast, LucideDynamicIcon, NowPlayingBar],
  template: `
    <p-toast />
    <div class="shell" [class.playing]="playback.current()" [class.nav-open]="navOpen()">
      <div class="nav-backdrop" (click)="navOpen.set(false)"></div>
      <aside class="sidebar">
        <a class="brand" routerLink="/photos" (click)="navOpen.set(false)">
          <img src="favicon.svg" width="32" height="32" alt="" />
          <span>my-drive</span>
        </a>
        <nav class="nav" aria-label="Primary">
          @for (item of nav; track item.routerLink) {
            <a
              class="nav-link"
              [routerLink]="item.routerLink"
              routerLinkActive="active"
              [routerLinkActiveOptions]="{ exact: true }"
              (click)="navOpen.set(false)"
            >
              <svg [lucideIcon]="item.icon" [size]="18" aria-hidden="true" />
              <span>{{ item.label }}</span>
            </a>
          }
        </nav>
      </aside>
      <div class="shell-main">
        <header class="topbar">
          <button
            type="button"
            class="icon-btn menu-btn"
            [attr.aria-expanded]="navOpen()"
            [attr.aria-label]="navOpen() ? 'Close menu' : 'Open menu'"
            (click)="navOpen.set(!navOpen())"
          >
            <svg [lucideIcon]="navOpen() ? 'x' : 'menu'" aria-hidden="true" />
          </button>
          <div class="topbar-end">
            <button type="button" class="ghost-btn" (click)="theme.toggle()">
              <svg [lucideIcon]="theme.mode() === 'dark' ? 'sun' : 'moon'" aria-hidden="true" />
              {{ theme.mode() === 'dark' ? 'Light mode' : 'Dark mode' }}
            </button>
            <span class="user-chip">{{ session.username() }}</span>
            <button type="button" class="ghost-btn" (click)="logout()">
              <svg lucideIcon="log-out" aria-hidden="true" />
              Logout
            </button>
          </div>
        </header>
        <main class="content">
          <router-outlet />
        </main>
      </div>
    </div>
    <app-now-playing-bar />
  `,
})
export class ShellComponent {
  navOpen = signal(false);
  readonly nav = [
    { label: 'Music', routerLink: '/music', icon: 'music' },
    { label: 'Photos & videos', routerLink: '/photos', icon: 'images' },
    { label: 'Files', routerLink: '/files', icon: 'folder' },
    { label: 'Import', routerLink: '/imports', icon: 'cloud-download' },
    { label: 'Devices', routerLink: '/devices', icon: 'monitor-smartphone' },
    { label: 'App updates', routerLink: '/app-updates', icon: 'package' },
  ];

  constructor(
    @Inject(SESSION_QUERY) readonly session: SessionQuery,
    @Inject(MusicPlaybackService) readonly playback: MusicPlaybackService,
    readonly theme: ThemeModeService,
    private readonly router: Router,
  ) {}

  logout() {
    this.playback.stop();
    this.session.logout();
    void this.router.navigateByUrl('/login');
  }
}
