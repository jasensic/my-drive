import { Component, Inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { LucideDynamicIcon } from '@lucide/angular';
import { Button } from 'primeng/button';
import { Card } from 'primeng/card';
import { InputText } from 'primeng/inputtext';
import { Password } from 'primeng/password';
import { CREATE_USER, LIST_USERS, SESSION_QUERY } from '../application/use-cases.tokens';
import type { CreateUser, ListUsers, SessionQuery } from '../application/use-cases.tokens';
import { UserProfile } from '../domain/models';
import { extractError } from './login.page';

@Component({
  selector: 'app-users-page',
  imports: [FormsModule, Button, Card, InputText, Password, LucideDynamicIcon],
  template: `
    <section class="page">
      <header class="page-head">
        <div class="page-intro">
          <h1 class="page-title">
            <svg lucideIcon="users" [size]="22" aria-hidden="true" />
            Users
          </h1>
        </div>
      </header>

      @if (!session.isAdmin()) {
        <p class="banner error">Only an admin can add users.</p>
      } @else {
        <p-card header="Add a user">
          <form class="stack-form" (ngSubmit)="create()">
            <div class="field">
              <label for="new-user">Username</label>
              <input id="new-user" pInputText [(ngModel)]="username" name="username" autocomplete="off" />
            </div>
            <div class="field">
              <label for="new-pass">Password</label>
              <p-password
                inputId="new-pass"
                [(ngModel)]="password"
                name="password"
                [feedback]="false"
                [toggleMask]="true"
                autocomplete="new-password"
              />
            </div>
            <p class="caption">They sign in on the portal or the phone app with these credentials.</p>
            <p-button type="submit" label="Add user" [loading]="busy()" />
          </form>
        </p-card>
      }

      @if (error()) {
        <p class="banner error">{{ error() }}</p>
      }

      <div class="card-list">
        @for (user of users(); track user.id) {
          <p-card [header]="user.username" [subheader]="user.is_admin ? 'Admin' : 'Member'" />
        }
      </div>
    </section>
  `,
})
export class UsersPage {
  users = signal<UserProfile[]>([]);
  busy = signal(false);
  error = signal<string | null>(null);
  username = '';
  password = '';

  constructor(
    @Inject(LIST_USERS) private readonly listUsers: ListUsers,
    @Inject(CREATE_USER) private readonly createUser: CreateUser,
    @Inject(SESSION_QUERY) readonly session: SessionQuery,
  ) {
    void this.reload();
  }

  async reload() {
    try {
      this.users.set(await this.listUsers.execute());
    } catch (err) {
      this.error.set(extractError(err));
    }
  }

  async create() {
    if (this.busy()) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    try {
      await this.createUser.execute(this.username, this.password);
      this.username = '';
      this.password = '';
      await this.reload();
    } catch (err) {
      this.error.set(extractError(err));
    } finally {
      this.busy.set(false);
    }
  }
}
