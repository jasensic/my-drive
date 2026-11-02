import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { SESSION_QUERY } from '../application/use-cases.tokens';
import { MusicPlaybackService } from './music-playback.service';

/** Calls where a 401 means "wrong credentials", which the login page already shows. */
const AUTH_ENTRY_POINTS = ['/v1/login', '/v1/setup', '/v1/status', '/v1/auth/refresh'];

export function isSessionExpired(url: string, status: number, hasSession: boolean): boolean {
  if (status !== 401 || !hasSession) {
    return false;
  }
  const path = url.split('?')[0];
  return !AUTH_ENTRY_POINTS.some((entry) => path.endsWith(entry));
}

/**
 * A stored token the server no longer accepts (expired after 7 days, or signed with a
 * previous JWT_SECRET) must not leave the user on a page full of errors: drop the
 * session and go back to the login screen.
 */
export const sessionExpiredInterceptor: HttpInterceptorFn = (req, next) => {
  const session = inject(SESSION_QUERY);
  const router = inject(Router);
  const playback = inject(MusicPlaybackService);
  return next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && isSessionExpired(req.url, error.status, session.hasSession())) {
        playback.stop();
        session.logout();
        void router.navigateByUrl('/login');
      }
      return throwError(() => error);
    }),
  );
};
