import { Component, Inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { MenuItem } from 'primeng/api';
import { Button } from 'primeng/button';
import { Menubar } from 'primeng/menubar';
import { Toast } from 'primeng/toast';
import { SESSION_QUERY } from '../application/use-cases.tokens';
import type { SessionQuery } from '../application/use-cases.tokens';
import { Router } from '@angular/router';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, Menubar, Toast, Button],
  template: `
    <p-toast />
    <p-menubar [model]="items">
      <ng-template #end>
        <span class="user">{{ session.username() }}</span>
        <p-button label="Logout" [text]="true" (onClick)="logout()" />
      </ng-template>
    </p-menubar>
    <router-outlet />
  `,
  styles: `
    .user { margin-right: 0.75rem; }
  `,
})
export class ShellComponent {
  items: MenuItem[] = [
    { label: 'Library', routerLink: '/library' },
    { label: 'Devices', routerLink: '/devices' },
    { label: 'App updates', routerLink: '/app-updates' },
  ];

  constructor(
    @Inject(SESSION_QUERY) readonly session: SessionQuery,
    private readonly router: Router,
  ) {}

  logout() {
    this.session.logout();
    void this.router.navigateByUrl('/login');
  }
}
