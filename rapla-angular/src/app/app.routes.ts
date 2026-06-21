import { Routes } from '@angular/router';
import { authGuard } from './auth/auth.guard';

/**
 * PRD 072 Phase 4 — the SPA no longer owns a login page or an OAuth callback
 * route. Login is the SERVER-rendered {@code /login} page (outside the SPA);
 * the authGuard redirects there (full navigation) when there is no valid
 * {@code access_token} cookie. The catch-all lands on the guarded reservations
 * view, which bounces to {@code /login} when unauthenticated.
 */
export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'reservations' },
  {
    path: 'reservations',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./reservations/reservations.component').then((m) => m.ReservationsComponent),
  },
  {
    // PRD 078 — GraphQL-driven table view (appointments / blocks). Sits beside
    // the legacy /api/table reservations view until it supersedes it.
    path: 'appointments',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./views/appointments-view.component').then((m) => m.AppointmentsViewComponent),
  },
  { path: '**', redirectTo: 'reservations' },
];
