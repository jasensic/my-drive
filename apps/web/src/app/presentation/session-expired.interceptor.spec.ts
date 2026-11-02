import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { SESSION_QUERY } from '../application/use-cases.tokens';
import { MusicPlaybackService } from './music-playback.service';
import { isSessionExpired, sessionExpiredInterceptor } from './session-expired.interceptor';

describe('isSessionExpired', () => {
  it('only reacts to a 401 on a signed-in session', () => {
    expect(isSessionExpired('/v1/files', 401, true)).toBe(true);
    expect(isSessionExpired('/v1/files', 401, false)).toBe(false);
    expect(isSessionExpired('/v1/files', 403, true)).toBe(false);
    expect(isSessionExpired('/v1/files', 500, true)).toBe(false);
  });

  it('leaves the login calls to the login page', () => {
    for (const url of ['/v1/login', '/v1/setup', '/v1/status', '/v1/auth/refresh']) {
      expect(isSessionExpired(url, 401, true)).toBe(false);
    }
    expect(isSessionExpired('/v1/files?silo=music', 401, true)).toBe(true);
  });
});

describe('sessionExpiredInterceptor', () => {
  function setup(hasSession: boolean) {
    const session = { hasSession: vi.fn(() => hasSession), logout: vi.fn() };
    const router = { navigateByUrl: vi.fn(async () => true) };
    const playback = { stop: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([sessionExpiredInterceptor])),
        provideHttpClientTesting(),
        { provide: SESSION_QUERY, useValue: session },
        { provide: Router, useValue: router },
        { provide: MusicPlaybackService, useValue: playback },
      ],
    });
    return {
      session,
      router,
      playback,
      http: TestBed.inject(HttpClient),
      backend: TestBed.inject(HttpTestingController),
    };
  }

  it('drops the session and returns to login when the token is rejected', async () => {
    const { session, router, playback, http, backend } = setup(true);
    const result = firstValueFrom(http.get('/v1/files?silo=music'));
    backend.expectOne('/v1/files?silo=music').flush({ error: 'invalid token' }, { status: 401, statusText: 'Unauthorized' });
    await expect(result).rejects.toMatchObject({ status: 401 });
    expect(playback.stop).toHaveBeenCalledOnce();
    expect(session.logout).toHaveBeenCalledOnce();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/login');
  });

  it('keeps wrong-password errors on the login page', async () => {
    const { session, router, http, backend } = setup(true);
    const result = firstValueFrom(http.post('/v1/login', {}));
    backend.expectOne('/v1/login').flush({ error: 'invalid credentials' }, { status: 401, statusText: 'Unauthorized' });
    await expect(result).rejects.toMatchObject({ status: 401 });
    expect(session.logout).not.toHaveBeenCalled();
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('does nothing without a session', async () => {
    const anonymous = setup(false);
    const first = firstValueFrom(anonymous.http.get('/v1/files'));
    anonymous.backend.expectOne('/v1/files').flush('', { status: 401, statusText: 'Unauthorized' });
    await expect(first).rejects.toMatchObject({ status: 401 });
    expect(anonymous.session.logout).not.toHaveBeenCalled();
  });
});
