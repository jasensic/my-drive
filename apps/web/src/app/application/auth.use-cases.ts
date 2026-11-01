import { Inject, Injectable } from '@angular/core';
import { AUTH_REPOSITORY, AuthRepository, TOKEN_STORE, TokenStore } from '../domain/ports';
import { AuthSession, UserProfile } from '../domain/models';
import { CheckSetup, CreateUser, ListUsers, Login, SessionQuery, SetupAdmin } from './use-cases.tokens';

@Injectable()
export class CheckSetupService implements CheckSetup {
  constructor(@Inject(AUTH_REPOSITORY) private readonly auth: AuthRepository) {}
  execute(): Promise<boolean> {
    return this.auth.status().then((s) => s.setup_required);
  }
}

@Injectable()
export class SetupAdminService implements SetupAdmin {
  constructor(
    @Inject(AUTH_REPOSITORY) private readonly auth: AuthRepository,
    @Inject(TOKEN_STORE) private readonly tokens: TokenStore,
  ) {}
  async execute(username: string, password: string): Promise<AuthSession> {
    const session = await this.auth.setup(username, password);
    this.tokens.set(session.token, session.user.username, session.user.id, session.user.is_admin ?? true);
    return session;
  }
}

@Injectable()
export class LoginService implements Login {
  constructor(
    @Inject(AUTH_REPOSITORY) private readonly auth: AuthRepository,
    @Inject(TOKEN_STORE) private readonly tokens: TokenStore,
  ) {}
  async execute(username: string, password: string): Promise<AuthSession> {
    const session = await this.auth.login(username, password);
    this.tokens.set(session.token, session.user.username, session.user.id, session.user.is_admin ?? false);
    return session;
  }
}

@Injectable()
export class SessionQueryService implements SessionQuery {
  constructor(
    @Inject(TOKEN_STORE) private readonly tokens: TokenStore,
    @Inject(AUTH_REPOSITORY) private readonly auth: AuthRepository,
  ) {}
  hasSession(): boolean {
    return !!this.tokens.get();
  }
  username(): string | null {
    return this.tokens.username();
  }
  userId(): string | null {
    return this.tokens.userId();
  }
  isAdmin(): boolean {
    return this.tokens.isAdmin();
  }
  async refreshProfile(): Promise<void> {
    const token = this.tokens.get();
    if (!token) {
      return;
    }
    const me = await this.auth.me();
    this.tokens.set(token, me.username, me.id, me.is_admin ?? false);
  }
  logout(): void {
    this.tokens.clear();
  }
}

@Injectable()
export class CreateUserService implements CreateUser {
  constructor(@Inject(AUTH_REPOSITORY) private readonly auth: AuthRepository) {}
  execute(username: string, password: string): Promise<UserProfile> {
    const trimmed = username.trim();
    if (!trimmed) {
      return Promise.reject(new Error('Username is required'));
    }
    if (password.length < 8) {
      return Promise.reject(new Error('Password must be at least 8 characters'));
    }
    return this.auth.createUser(trimmed, password);
  }
}

@Injectable()
export class ListUsersService implements ListUsers {
  constructor(@Inject(AUTH_REPOSITORY) private readonly auth: AuthRepository) {}
  execute(): Promise<UserProfile[]> {
    return this.auth.listUsers();
  }
}
