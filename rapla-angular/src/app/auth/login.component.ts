import { Component, inject } from '@angular/core';

import { AuthService } from './auth.service';

@Component({
  selector: 'app-login',
  template: `
    <div class="login-card">
      <h1>Rapla</h1>
      <p>Sign in to access your reservations.</p>
      <button type="button" (click)="auth.signIn()">Sign in</button>
    </div>
  `,
  styles: [`
    .login-card { max-width: 360px; margin: 4rem auto; padding: 2rem; border: 1px solid #ddd; border-radius: 8px; font-family: system-ui, sans-serif; text-align: center; }
    h1 { margin: 0 0 0.5rem; font-size: 1.5rem; }
    p { color: #666; font-size: 0.9rem; margin: 0 0 1.5rem; }
    button { width: 100%; padding: 0.7rem; font-size: 1rem; border: none; border-radius: 4px; background: #2563eb; color: white; cursor: pointer; }
    button:hover { background: #1d4ed8; }
  `]
})
export class LoginComponent {
  protected readonly auth = inject(AuthService);
}
