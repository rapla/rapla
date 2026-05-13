import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { AuthService } from './auth.service';

/**
 * Entry point reached either by direct navigation, the authGuard, or the
 * 401 interceptor. If OAuth is configured and no previous attempt failed
 * during this tab session, auto-initiate the code flow so the user lands
 * directly on Spring's /oauth2/authorize. Otherwise show the manual button
 * (fallback for OAuth-disabled deployments + circuit-break for failed flows).
 */
@Component({
  selector: 'app-login',
  imports: [MatButtonModule, MatCardModule, MatProgressSpinnerModule],
  template: `
    <div class="login-wrap">
      <mat-card>
        <mat-card-header><mat-card-title>Rapla</mat-card-title></mat-card-header>
        <mat-card-content>
          @if (autoRedirecting) {
            <div class="centered">
              <mat-spinner diameter="28"></mat-spinner>
              <p>Redirecting to sign-in…</p>
            </div>
          } @else {
            <p [class.error]="errorMessage">{{ errorMessage ?? 'Sign in to access your reservations.' }}</p>
            @if (auth.lastOAuthError(); as oauthErr) {
              <p class="detail">OAuth library reported: <code>{{ oauthErr }}</code></p>
            }
            <button matButton="filled" (click)="retry()">Sign in</button>
            @if (errorMessage) {
              <button matButton (click)="forceFreshLogin()">Force fresh sign-in (prompt=login)</button>
            }
          }
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .login-wrap { display: flex; justify-content: center; padding: 4rem 1rem; }
    mat-card { max-width: 360px; width: 100%; }
    p { margin: 0 0 1rem; }
    .error { color: #c62828; }
    .detail { font-size: 0.85rem; color: rgba(0, 0, 0, 0.7); }
    .detail code { background: rgba(0, 0, 0, 0.05); padding: 0.15rem 0.35rem; border-radius: 3px; font-family: monospace; }
    button + button { margin-top: 0.5rem; }
    .centered { display: flex; flex-direction: column; align-items: center; gap: 0.75rem; padding: 1rem 0; }
    .centered p { margin: 0; color: rgba(0, 0, 0, 0.6); font-size: 0.9rem; }
  `]
})
export class LoginComponent implements OnInit {
  protected readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);

  autoRedirecting = false;
  errorMessage: string | null = null;

  ngOnInit() {
    const params = this.route.snapshot.queryParamMap;
    const oauthError = params.get('error');
    if (oauthError) {
      this.errorMessage = `Sign-in failed: ${oauthError}. Try again.`;
      return;
    }

    const failures = Number(sessionStorage.getItem('oauthFailures') ?? '0');
    if (failures > 0) {
      this.errorMessage =
        `Sign-in did not complete (attempt ${failures}). The OAuth code exchange returned without a valid token. ` +
        `Click below to retry, or check the server logs for /oauth2/token errors.`;
      return;
    }

    if (this.auth.isOAuthConfigured()) {
      this.autoRedirecting = true;
      this.auth.signIn();
    }
  }

  retry() {
    sessionStorage.removeItem('oauthFailures');
    this.auth.signIn();
  }

  /**
   * Force Spring Authorization Server to prompt for credentials even if it
   * has a session cookie for the user. Useful when the silent redirect path
   * keeps issuing codes that fail to exchange — typically a stale Spring
   * session for a user whose record changed (renamed, deleted, password
   * rotated) since the cookie was issued.
   */
  forceFreshLogin() {
    sessionStorage.removeItem('oauthFailures');
    this.auth.signInPromptLogin();
  }
}
