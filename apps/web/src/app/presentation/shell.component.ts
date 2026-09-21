import { Component, Inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { MenuItem } from 'primeng/api';
import { Button } from 'primeng/button';
import { Menubar } from 'primeng/menubar';
import { Toast } from 'primeng/toast';
import { SESSION_QUERY } from '../application/use-cases.tokens';
import type { SessionQuery } from '../application/use-cases.tokens';
import { Router } from '@angular/router';
import { MusicPlaybackService } from './music-playback.service';
import { NowPlayingBar } from './now-playing-bar';
import { ThemeModeService } from './theme.service';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, Menubar, Toast, Button, NowPlayingBar],
  template: `
    <p-toast />
    <div class="frame" [class.playing]="playback.current()">
    <p-menubar [model]="items">
      <ng-template #start>
        <img class="brand" src="favicon.svg" width="28" height="28" alt="my-drive" />
      </ng-template>
      <ng-template #end>
        <div class="end">
          <p-button
            [label]="theme.mode() === 'dark' ? 'Light mode' : 'Dark mode'"
            [icon]="theme.mode() === 'dark' ? 'pi pi-sun' : 'pi pi-moon'"
            [text]="true"
            (onClick)="theme.toggle()"
          />
          <span class="user">{{ session.username() }}</span>
          <p-button label="Logout" [text]="true" (onClick)="logout()" />
        </div>
      </ng-template>
    </p-menubar>
    <router-outlet />
    </div>
    <app-now-playing-bar />
  `,
  styles: `
    .end { display: flex; align-items: center; gap: 0.5rem; }
    .user { margin-right: 0.25rem; }
    .frame.playing { padding-bottom: 4.5rem; }
    .brand { display: block; margin-right: 0.35rem; }
  `,
})
export class ShellComponent {
  items: MenuItem[] = [
    { label: 'Music', routerLink: '/music', icon: 'pi pi-volume-up' },
    { label: 'Photos & videos', routerLink: '/photos', icon: 'pi pi-images' },
    { label: 'Files', routerLink: '/files', icon: 'pi pi-folder' },
    { label: 'Import', routerLink: '/imports', icon: 'pi pi-cloud-download' },
    { label: 'Devices', routerLink: '/devices' },
    { label: 'App updates', routerLink: '/app-updates' },
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
