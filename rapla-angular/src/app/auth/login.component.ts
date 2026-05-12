import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';

import { AuthService } from './auth.service';

@Component({
  selector: 'app-login',
  imports: [FormsModule],
  template: `
    <div class="login-card">
      <h1>Rapla</h1>
      <p class="hint">Dev: <code>admin</code> / empty password.</p>
      <form (ngSubmit)="submit()">
        <label>
          Username
          <input name="username" [(ngModel)]="username" autocomplete="username" autofocus />
        </label>
        <label>
          Password
          <input name="password" type="password" [(ngModel)]="password" autocomplete="current-password" />
        </label>
        <button type="submit" [disabled]="busy()">{{ busy() ? 'Signing in…' : 'Sign in' }}</button>
        @if (error()) {
          <p class="error">{{ error() }}</p>
        }
      </form>
    </div>
  `,
  styles: [`
    .login-card { max-width: 360px; margin: 4rem auto; padding: 2rem; border: 1px solid #ddd; border-radius: 8px; font-family: system-ui, sans-serif; }
    h1 { margin: 0 0 0.5rem; font-size: 1.5rem; }
    .hint { color: #666; font-size: 0.85rem; margin-bottom: 1.5rem; }
    label { display: block; margin-bottom: 1rem; font-size: 0.9rem; }
    input { display: block; width: 100%; padding: 0.5rem; margin-top: 0.25rem; box-sizing: border-box; border: 1px solid #ccc; border-radius: 4px; }
    button { width: 100%; padding: 0.6rem; font-size: 1rem; border: none; border-radius: 4px; background: #2563eb; color: white; cursor: pointer; }
    button:disabled { background: #9ca3af; cursor: not-allowed; }
    .error { color: #dc2626; font-size: 0.85rem; margin-top: 1rem; }
  `]
})
export class LoginComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  username = '';
  password = '';
  busy = signal(false);
  error = signal<string | null>(null);

  submit() {
    this.busy.set(true);
    this.error.set(null);
    this.auth.login(this.username, this.password).subscribe({
      next: () => {
        this.busy.set(false);
        this.router.navigateByUrl('/reservations');
      },
      error: (err) => {
        this.busy.set(false);
        this.error.set(err?.error?.message ?? `Sign-in failed (HTTP ${err?.status ?? '?'})`);
      }
    });
  }
}
