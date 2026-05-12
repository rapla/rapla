import { Routes } from '@angular/router';
import { authGuard } from './auth/auth.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'reservations' },
  { path: 'login', loadComponent: () => import('./auth/login.component').then(m => m.LoginComponent) },
  {
    path: 'reservations',
    canActivate: [authGuard],
    loadComponent: () => import('./reservations/reservations.component').then(m => m.ReservationsComponent)
  },
  { path: '**', redirectTo: 'login' }
];
