import { inject } from '@angular/core';
import { CanActivateFn } from '@angular/router';

import { AuthService } from './auth.service';

/**
 * PRD 072 Phase 4 — cookie-credential guard. Login state comes from the
 * identity loaded at app init via {@code GET /api/auth/me} (see app.config.ts).
 * When there is no identity (no valid {@code access_token} cookie), the browser
 * is redirected to the SERVER-rendered {@code /login} page (a full navigation,
 * not an SPA route) so the server can run the chooser / password / OAuth flow
 * and set the cookies.
 */
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  if (auth.isLoggedIn()) {
    return true;
  }
  auth.redirectToLogin();
  return false;
};
