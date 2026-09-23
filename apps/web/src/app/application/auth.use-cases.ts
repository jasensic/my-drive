import { Inject, Injectable } from '@angular/core';
import { AUTH_REPOSITORY, AuthRepository, TOKEN_STORE, TokenStore } from '../domain/ports';
import { AuthSession, UserProfile } from '../domain/models';
import { CheckSetup, ListUsers, Login, RegisterAccount, SessionQuery, SetupAdmin } from './use-cases.tokens';

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
    this.tokens.set(session.token, session.user.username, session.user.id);
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
    this.tokens.set(session.token, session.user.username, session.user.id);
    return session;
  }
}

@Injectable()
export class SessionQueryService implements SessionQuery {
  constructor(@Inject(TOKEN_STORE) private readonly tokens: TokenStore) {}
  hasSession(): boolean {
    return !!this.tokens.get();
  }
  username(): string | null {
    return this.tokens.username();
  }
  userId(): string | null {
    return this.tokens.userId();
  }
  logout(): void {
    this.tokens.clear();
  }
}

@Injectable()
export class RegisterAccountService implements RegisterAccount {
  constructor(
    @Inject(AUTH_REPOSITORY) private readonly auth: AuthRepository,
    @Inject(TOKEN_STORE) private readonly tokens: TokenStore,
  ) {}
  async execute(username: string, password: string): Promise<AuthSession> {
    const session = await this.auth.register(username, password);
    this.tokens.set(session.token, session.user.username, session.user.id);
    return session;
  }
}

@Injectable()
export class ListUsersService implements ListUsers {
  constructor(@Inject(AUTH_REPOSITORY) private readonly auth: AuthRepository) {}
  execute(): Promise<UserProfile[]> {
    return this.auth.listUsers();
  }
}
