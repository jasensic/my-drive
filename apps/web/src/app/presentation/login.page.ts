import { Component, Inject, computed, signal } from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { LucideDynamicIcon } from '@lucide/angular';
import { Button } from 'primeng/button';
import { Card } from 'primeng/card';
import { InputText } from 'primeng/inputtext';
import { Password } from 'primeng/password';
import { CHECK_SETUP, LOGIN, REGISTER_ACCOUNT, SETUP_ADMIN } from '../application/use-cases.tokens';
import type { CheckSetup, Login, RegisterAccount, SetupAdmin } from '../application/use-cases.tokens';
import { ThemeModeService } from './theme.service';

@Component({
  selector: 'app-login-page',
  imports: [FormsModule, Button, Card, InputText, Password, LucideDynamicIcon],
  template: `
    <div class="auth-screen">
      <div class="auth-theme">
        <button
          type="button"
          class="icon-btn"
          [attr.aria-label]="theme.mode() === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'"
          (click)="theme.toggle()"
        >
          <svg [lucideIcon]="theme.mode() === 'dark' ? 'sun' : 'moon'" aria-hidden="true" />
        </button>
      </div>
      <div class="auth-brand">
        <img src="icon-192.png" width="36" height="36" alt="" />
        <span>my-drive</span>
      </div>
      <p-card [header]="header()">
        <form class="stack-form" (ngSubmit)="submit()">
          <div class="field">
            <label for="user">Username</label>
            <input id="user" pInputText [(ngModel)]="username" name="username" autocomplete="username" />
          </div>
          <div class="field">
            <label for="pass">Password</label>
            <p-password inputId="pass" [(ngModel)]="password" name="password" [feedback]="false" [toggleMask]="true" />
          </div>
          @if (error()) {
            <p class="banner error">{{ error() }}</p>
          }
          <p-button
            type="submit"
            [label]="submitLabel()"
            [loading]="busy()"
          />
          @if (!setupRequired()) {
            <p-button
              type="button"
              [text]="true"
              [label]="registerMode() ? 'Have an account? Sign in' : 'Create account'"
              (onClick)="registerMode.set(!registerMode())"
            />
          }
        </form>
      </p-card>
    </div>
  `,
})
export class LoginPage {
  username = '';
  password = '';
  setupRequired = signal(false);
  registerMode = signal(false);
  busy = signal(false);
  error = signal<string | null>(null);
  header = computed(() => {
    if (this.setupRequired()) {
      return 'Create admin';
    }
    return this.registerMode() ? 'Create account' : 'Sign in to my-drive';
  });
  submitLabel = computed(() => {
    if (this.setupRequired()) {
      return 'Complete setup';
    }
    return this.registerMode() ? 'Create account' : 'Login';
  });

  constructor(
    @Inject(CHECK_SETUP) private readonly checkSetup: CheckSetup,
    @Inject(SETUP_ADMIN) private readonly setupAdmin: SetupAdmin,
    @Inject(LOGIN) private readonly login: Login,
    @Inject(REGISTER_ACCOUNT) private readonly registerAccount: RegisterAccount,
    readonly theme: ThemeModeService,
    private readonly router: Router,
  ) {
    void this.checkSetup.execute().then((required) => this.setupRequired.set(required));
  }

  async submit() {
    if (this.busy()) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    try {
      if (this.setupRequired()) {
        await this.setupAdmin.execute(this.username, this.password);
      } else if (this.registerMode()) {
        await this.registerAccount.execute(this.username, this.password);
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
