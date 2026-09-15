import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { SESSION_QUERY } from '../application/use-cases.tokens';

export const authGuard: CanActivateFn = () => {
  const session = inject(SESSION_QUERY);
  const router = inject(Router);
  return session.hasSession() ? true : router.createUrlTree(['/login']);
};

export const setupGuard: CanActivateFn = async () => {
  const session = inject(SESSION_QUERY);
  const router = inject(Router);
  if (session.hasSession()) {
    return router.createUrlTree(['/library']);
  }
  return true;
};
