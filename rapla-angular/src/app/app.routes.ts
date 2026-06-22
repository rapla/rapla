import { Routes } from '@angular/router';
import { authGuard } from './auth/auth.guard';

/**
 * The SPA is ONE page: the generic {@link ViewHostComponent} renders every
 * server-declared view by name ({@code views/:viewName}, PRD 078). The landing
 * route ({@code ''}) and the catch-all ({@code '**'}) resolve to the
 * {@link DefaultViewRedirectComponent}, which opens the last-opened view (or the
 * first catalog view). All bespoke, non-generic data views (the REST
 * reservations table, the hand-written appointments / week GraphQL queries) are
 * gone — the only data paths left are the views, the search, and auth.
 *
 * PRD 072 Phase 4 — the SPA owns no login page or OAuth callback route. Login is
 * the SERVER-rendered {@code /login} page; the authGuard redirects there (full
 * navigation) when there is no valid {@code access_token} cookie.
 */
export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./views/default-view-redirect.component').then(
        (m) => m.DefaultViewRedirectComponent,
      ),
  },
  {
    path: 'views/:viewName',
    canActivate: [authGuard],
    loadComponent: () => import('./views/view-host.component').then((m) => m.ViewHostComponent),
  },
  {
    path: '**',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./views/default-view-redirect.component').then(
        (m) => m.DefaultViewRedirectComponent,
      ),
  },
];
