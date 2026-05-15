import { Component, OnInit, computed, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';

import { AuthService, OAuthProviderEntry } from './auth.service';

/**
 * Entry point reached either by direct navigation, the authGuard, or the
 * 401 interceptor.
 *
 * PRD 036 behaviour:
 *   picker.mode = 'auto' + 1 visible provider:  auto-fire that provider.
 *   picker.mode = 'auto' + ≥2 visible providers: render picker buttons.
 *   picker.mode = 'always':                     always render picker buttons.
 *   picker.mode = 'never':                      auto-fire the primary provider.
 *
 * When auto-firing fails (OAuth error in this tab's session), fall back to
 * the manual-retry UI to break the redirect loop.
 */
@Component({
  selector: 'app-login',
  imports: [MatButtonModule, MatCardModule, MatProgressSpinnerModule, MatIconModule],
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
            @if (showPicker()) {
              <div class="picker">
                @for (provider of pickerProviders(); track provider.id) {
                  <button matButton="filled"
                          class="provider-btn"
                          [attr.data-provider-id]="provider.id"
                          (click)="signInWithProvider(provider.id)">
                    <mat-icon class="provider-icon" [attr.aria-hidden]="true">
                      {{ iconNameFor(provider) }}
                    </mat-icon>
                    <span>{{ provider.displayName }}</span>
                  </button>
                }
              </div>
            } @else {
              <button matButton="filled" (click)="retry()">Sign in</button>
            }
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
    .picker { display: flex; flex-direction: column; gap: 0.5rem; }
    .provider-btn { display: flex; align-items: center; justify-content: center; gap: 0.5rem; }
    .provider-icon { font-size: 1.1rem; height: 1.1rem; width: 1.1rem; }
  `]
})
export class LoginComponent implements OnInit {
  protected readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);

  autoRedirecting = false;
  errorMessage: string | null = null;

  readonly showPicker = computed(() => this.auth.shouldShowPicker());
  readonly pickerProviders = computed(() => this.auth.pickerProviders());

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

    // Don't auto-fire when the picker would render — the user has to choose.
    if (this.auth.isOAuthConfigured() && !this.auth.shouldShowPicker()) {
      this.autoRedirecting = true;
      this.auth.signIn();
    }
  }

  retry() {
    sessionStorage.removeItem('oauthFailures');
    this.auth.signIn();
  }

  signInWithProvider(providerId: string) {
    sessionStorage.removeItem('oauthFailures');
    this.auth.signInWithProvider(providerId);
  }

  /**
   * Force the IdP to prompt for credentials even if it has a session cookie.
   * Useful when silent redirect keeps issuing codes that fail to exchange —
   * typically a stale session for a user whose record changed since the
   * cookie was issued.
   */
  forceFreshLogin() {
    sessionStorage.removeItem('oauthFailures');
    this.auth.signInPromptLogin();
  }

  /**
   * Maps the discovery-emitted icon name (well-known: `microsoft`, `google`,
   * `rapla`) to a Material Icons code-point. Falls back to a generic
   * key/login icon for custom / unknown values.
   */
  iconNameFor(provider: OAuthProviderEntry): string {
    switch (provider.icon) {
      case 'microsoft': return 'window';      // Material doesn't ship a Microsoft logo
      case 'google':    return 'g_translate'; // Closest Google-branded Material icon
      case 'rapla':     return 'key';
      default:          return 'login';
    }
  }
}
