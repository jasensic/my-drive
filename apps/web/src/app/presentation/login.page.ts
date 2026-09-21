import { Component, Inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Button } from 'primeng/button';
import { Card } from 'primeng/card';
import { InputText } from 'primeng/inputtext';
import { Password } from 'primeng/password';
import { CHECK_SETUP, LOGIN, SETUP_ADMIN } from '../application/use-cases.tokens';
import type { CheckSetup, Login, SetupAdmin } from '../application/use-cases.tokens';
import { ThemeModeService } from './theme.service';

@Component({
  selector: 'app-login-page',
  imports: [FormsModule, Button, Card, InputText, Password],
  template: `
    <div class="auth-wrap">
      <div class="theme">
        <p-button
          [icon]="theme.mode() === 'dark' ? 'pi pi-sun' : 'pi pi-moon'"
          [rounded]="true"
          [text]="true"
          [attr.aria-label]="theme.mode() === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'"
          (onClick)="theme.toggle()"
        />
      </div>
      <p-card [header]="setupRequired() ? 'Create admin' : 'Sign in to my-drive'">
        <div class="field">
          <label for="user">Username</label>
          <input id="user" pInputText [(ngModel)]="username" autocomplete="username" />
        </div>
        <div class="field">
          <label for="pass">Password</label>
          <p-password inputId="pass" [(ngModel)]="password" [feedback]="false" [toggleMask]="true" />
        </div>
        @if (error()) {
          <p class="error">{{ error() }}</p>
        }
        <p-button
          [label]="setupRequired() ? 'Complete setup' : 'Login'"
          [loading]="busy()"
          (onClick)="submit()"
        />
      </p-card>
    </div>
  `,
  styles: `
    .auth-wrap {
      min-height: 100dvh;
      display: grid;
      align-content: start;
      justify-items: center;
      padding: 2rem 1rem calc(1.5rem + env(safe-area-inset-bottom, 0px));
      position: relative;
    }
    .theme { position: absolute; top: 1rem; right: 1rem; }
    .field { display: flex; flex-direction: column; gap: 0.4rem; margin-bottom: 1rem; }
    .error { color: var(--p-red-500); }
    :host ::ng-deep .p-card { width: min(24rem, 100%); }
  `,
})
export class LoginPage {
  username = '';
  password = '';
  setupRequired = signal(false);
  busy = signal(false);
  error = signal<string | null>(null);

  constructor(
    @Inject(CHECK_SETUP) private readonly checkSetup: CheckSetup,
    @Inject(SETUP_ADMIN) private readonly setupAdmin: SetupAdmin,
    @Inject(LOGIN) private readonly login: Login,
    readonly theme: ThemeModeService,
    private readonly router: Router,
  ) {
    void this.checkSetup.execute().then((required) => this.setupRequired.set(required));
  }

  async submit() {
    this.busy.set(true);
    this.error.set(null);
    try {
      if (this.setupRequired()) {
        await this.setupAdmin.execute(this.username, this.password);
      } else {
        await this.login.execute(this.username, this.password);
      }
      await this.router.navigateByUrl('/photos');
    } catch (err) {
      this.error.set(extractError(err));
    } finally {
      this.busy.set(false);
    }
  }
}

export function extractError(err: unknown): string {
  if (typeof err === 'object' && err && 'error' in err) {
    const body = (err as { error?: { error?: string } | string; message?: string }).error;
    if (typeof body === 'object' && body?.error) {
      return body.error;
    }
    if (typeof body === 'string') {
      return body;
    }
  }
  return err instanceof Error ? err.message : 'Request failed';
}
