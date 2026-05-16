import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { OAuthErrorEvent, OAuthEvent, OAuthService } from 'angular-oauth2-oidc';

export interface OAuthProviderEntry {
  id: string;
  displayName: string;
  icon: string;
  order: number;
  webPickerVisible: boolean;
  clientId: string;
  issuer: string;
  authorizeUrl: string;
  /**
   * For external providers (Microsoft, Google) this is rapla's BFF
   * token-exchange endpoint (`/api/auth/oauth/exchange/{providerId}`), not
   * the IdP's real token URL. The BFF adds the server-held `client_secret`
   * before forwarding to the IdP — keeping the secret off the SPA wire.
   * For the rapla embedded SAS entry, this is the SAS token endpoint
   * (no BFF needed; no secret involved).
   */
  tokenUrl: string;
  jwksUrl: string;
  endSessionUrl: string;
  scopes: string[];
  extraAuthorizeParams: Record<string, string>;
}

export interface OAuthPicker {
  mode: 'auto' | 'always' | 'never';
  primary: string;
}

export interface OAuthDiscovery {
  enabled: boolean;
  clientId: string;
  issuer: string;
  authorizeUrl: string;
  tokenUrl: string;
  logoutUrl: string;
  jwksUrl: string;
  userinfoUrl: string;
  endSessionUrl: string;
  scopes: string[];
  showPasteFallback?: boolean;
  picker: OAuthPicker;
  providers: OAuthProviderEntry[];
}

/**
 * Thin wrapper around angular-oauth2-oidc's OAuthService.
 *
 * The library handles the Authorization Code + PKCE flow against whichever
 * IdP the discovery endpoint nominates. PRD 036 lets a deployment enable
 * multiple providers (rapla embedded SAS, Microsoft Entra, Google) — the
 * picker UI on /login lets the user choose which to use.
 *
 * `lastOAuthError` captures the most recent OAuthErrorEvent so the UI can
 * surface what `/oauth2/token` actually said (invalid_grant, redirect_uri
 * mismatch, etc.) rather than just "no token".
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  /**
   * localStorage key tracking which provider the user signed in with. Survives
   * the OAuth callback redirect (browser reload) and tab close. Must match the
   * storage the OAuth library uses (`setStorage(localStorage)` in app.config)
   * so a token refresh after page reload still hits the right token endpoint.
   * Cleared on signOut().
   */
  private static readonly ACTIVE_PROVIDER_KEY = 'rapla.oauth.activeProvider';

  private readonly oauth = inject(OAuthService);
  private readonly router = inject(Router);

  private redirecting = false;

  readonly lastOAuthError = signal<string | null>(null);
  readonly discovery = signal<OAuthDiscovery | null>(null);

  /** Providers visible to the web picker, sorted by `order`. */
  readonly pickerProviders = computed<OAuthProviderEntry[]>(() => {
    const d = this.discovery();
    if (!d) return [];
    return d.providers
      .filter(p => p.webPickerVisible)
      .slice()
      .sort((a, b) => a.order - b.order);
  });

  /**
   * Whether the picker UI should render. Driven by `picker.mode`:
   *   auto   — render when ≥2 visible providers (or 0; that case prompts a
   *            "no IdP configured" UX upstream).
   *   always — render whenever any provider is visible.
   *   never  — never render.
   */
  readonly shouldShowPicker = computed(() => {
    const d = this.discovery();
    if (!d) return false;
    const count = this.pickerProviders().length;
    switch (d.picker.mode) {
      case 'always': return count > 0;
      case 'never':  return false;
      case 'auto':
      default:       return count >= 2;
    }
  });

  constructor() {
    this.oauth.events.subscribe((evt: OAuthEvent) => {
      if (evt instanceof OAuthErrorEvent) {
        const reason = describeOAuthError(evt);
        this.lastOAuthError.set(reason);
        console.error('[oauth]', evt.type, evt);
      } else if (evt.type === 'token_received' || evt.type === 'token_refreshed') {
        this.lastOAuthError.set(null);
      }
    });
  }

  setDiscovery(cfg: OAuthDiscovery): void {
    this.discovery.set(cfg);
  }

  /** The provider entry the picker auto-fires when mode=auto+single or mode=never. */
  primaryProvider(): OAuthProviderEntry | null {
    const d = this.discovery();
    if (!d) return null;
    return d.providers.find(p => p.id === d.picker.primary) ?? d.providers[0] ?? null;
  }

  /**
   * The provider the user is currently signed in with (picker choice, persisted
   * across the callback redirect via localStorage). Falls back to the primary
   * provider when no choice was saved (e.g. first launch). Critical for the
   * callback path: when the browser comes back to /app/auth/callback?code=…
   * after the IdP redirect, the SPA fully reloads and the app initializer
   * needs to reconfigure OAuthService with the same provider the user picked
   * pre-redirect — otherwise the code is exchanged at the wrong token endpoint.
   */
  activeProvider(): OAuthProviderEntry | null {
    const d = this.discovery();
    if (!d) return null;
    const stored = localStorage.getItem(AuthService.ACTIVE_PROVIDER_KEY);
    if (stored) {
      const p = d.providers.find(p => p.id === stored);
      if (p) return p;
    }
    return this.primaryProvider();
  }

  /**
   * Reconfigure {@link OAuthService} for one of the discovery's providers and
   * start a code flow. When `providerId` is null/unknown, falls back to the
   * top-level flat fields (rapla embedded SAS).
   */
  signInWithProvider(providerId: string): void {
    if (this.redirecting) return;
    const d = this.discovery();
    if (!d) { console.warn('[oauth] signInWithProvider before discovery loaded'); return; }
    const provider = d.providers.find(p => p.id === providerId) ?? null;
    if (provider) {
      // Persist BEFORE the redirect so the post-redirect appInitializer applies
      // the matching provider and exchanges the code at the right token endpoint.
      localStorage.setItem(AuthService.ACTIVE_PROVIDER_KEY, providerId);
    }
    this.applyProviderToOAuthService(provider, d);
    this.lastOAuthError.set(null);
    this.redirecting = true;
    this.oauth.initCodeFlow(undefined, provider?.extraAuthorizeParams ?? {});
  }

  /**
   * Apply a provider entry's URLs/clientId/scopes to {@link OAuthService}.
   * Exposed so the app initializer can configure it once at startup with the
   * primary provider.
   */
  applyProviderToOAuthService(provider: OAuthProviderEntry | null, cfg: OAuthDiscovery): void {
    const origin = window.location.origin;
    const useProvider = provider ?? this.legacyFallbackProvider(cfg);
    this.oauth.configure({
      issuer: useProvider.issuer,
      clientId: useProvider.clientId,
      redirectUri: origin + '/app/auth/callback',
      responseType: 'code',
      scope: (useProvider.scopes ?? cfg.scopes ?? ['openid', 'profile', 'offline_access']).join(' '),
      loginUrl: useProvider.authorizeUrl,
      tokenEndpoint: useProvider.tokenUrl,
      userinfoEndpoint: cfg.userinfoUrl,
      logoutUrl: useProvider.endSessionUrl || cfg.endSessionUrl || cfg.logoutUrl,
      // After IdP RP-initiated logout, land directly on /app/login so the
      // picker renders ready for the next sign-in. (Without this, the
      // browser hits /app/, the auth guard sees no token, then bounces to
      // /login — one extra navigation.)
      postLogoutRedirectUri: origin + '/app/login',
      showDebugInformation: false,
      skipIssuerCheck: true,
      strictDiscoveryDocumentValidation: false,
    });
  }

  /**
   * Synthesise a provider entry from the flat top-level fields when discovery
   * arrived from a pre-PRD-036 server (no `providers[]`). The flat fields
   * always describe the rapla embedded SAS.
   */
  private legacyFallbackProvider(cfg: OAuthDiscovery): OAuthProviderEntry {
    return {
      id: 'rapla',
      displayName: 'Sign in with rapla password',
      icon: 'rapla',
      order: 0,
      webPickerVisible: false,
      clientId: cfg.clientId,
      issuer: cfg.issuer,
      authorizeUrl: cfg.authorizeUrl,
      tokenUrl: cfg.tokenUrl,
      jwksUrl: cfg.jwksUrl,
      endSessionUrl: cfg.endSessionUrl,
      scopes: cfg.scopes,
      extraAuthorizeParams: {},
    };
  }

  signIn(): void {
    if (this.redirecting) return;
    this.redirecting = true;
    this.lastOAuthError.set(null);
    const primary = this.primaryProvider();
    this.oauth.initCodeFlow(undefined, primary?.extraAuthorizeParams ?? {});
  }

  /**
   * Same as signIn() but adds `prompt=login` so the IdP forces a fresh
   * credential prompt even if it has a valid session cookie. Use this to
   * break out of "silent re-redirect issues codes that won't exchange" —
   * e.g. stale Spring session.
   */
  signInPromptLogin(): void {
    if (this.redirecting) return;
    this.redirecting = true;
    this.lastOAuthError.set(null);
    const primary = this.primaryProvider();
    this.oauth.initCodeFlow(undefined, { ...(primary?.extraAuthorizeParams ?? {}), prompt: 'login' });
  }

  /**
   * Explicit user-driven sign-out. Behaviour depends on the active provider:
   *
   * <ul>
   *   <li><b>rapla SAS / Microsoft Entra</b> — both expose a proper OIDC
   *       end-session endpoint (rapla's `/connect/logout`, Entra's
   *       `/oauth2/v2.0/logout`). Call <code>oauth.logOut()</code> which
   *       clears local tokens AND navigates to the IdP's end-session URL
   *       with <code>id_token_hint</code> + <code>post_logout_redirect_uri</code>.
   *       The IdP terminates its session and bounces back to <code>/app/</code>.</li>
   *   <li><b>Google</b> — has no proper RP-initiated OIDC logout. Don't
   *       redirect anywhere external; just clear local tokens and route to
   *       <code>/login</code>. Hitting rapla's <code>/connect/logout</code>
   *       with a Google id_token would 404 (rapla SAS doesn't recognise
   *       externally-issued id_tokens).</li>
   * </ul>
   */
  signOut(): void {
    const active = this.activeProvider();
    const hasIdpLogout = active != null && !!active.endSessionUrl && active.endSessionUrl.length > 0;
    localStorage.removeItem(AuthService.ACTIVE_PROVIDER_KEY);
    if (hasIdpLogout) {
      this.oauth.logOut();
    } else {
      this.oauth.logOut(true);
      this.router.navigateByUrl('/login');
    }
  }

  /**
   * 401 path: token expired, refresh failed, or the server kicked us out.
   * Clear local state without redirecting to the IdP's end-session endpoint
   * (the session is already dead server-side), then navigate to /login where
   * the auto-redirect to /oauth2/authorize takes over.
   */
  handleUnauthenticated(): void {
    localStorage.removeItem(AuthService.ACTIVE_PROVIDER_KEY);
    this.oauth.logOut(true);
    this.router.navigateByUrl('/login');
  }

  /**
   * Returns the bearer token to send on rapla API calls. For external IdPs
   * (Google, Microsoft) we send the OIDC {@code id_token} — a real signed
   * JWT carrying the user's identity claims that rapla's
   * {@code IssuerAwareJwtDecoder} can validate. Google's
   * {@code access_token} is opaque (not a JWT) and would fail rapla's
   * JWT decoder with "malformed token". For the rapla embedded SAS the
   * access_token is itself a JWT (carrying the user UUID), so we keep
   * that path unchanged.
   */
  token(): string | null {
    const activeProviderId = localStorage.getItem(AuthService.ACTIVE_PROVIDER_KEY);
    if (activeProviderId && activeProviderId !== 'rapla') {
      const idToken = this.oauth.getIdToken();
      if (idToken) return idToken;
    }
    return this.oauth.getAccessToken() || null;
  }

  isLoggedIn(): boolean {
    return this.oauth.hasValidAccessToken();
  }

  /**
   * True once the app initializer in app.config.ts has called oauth.configure().
   * If the backend has rapla.oauth.enabled=false, this stays false and the
   * /login page falls back to its manual button.
   */
  isOAuthConfigured(): boolean {
    return Boolean(this.oauth.loginUrl);
  }

  identityClaims(): Record<string, unknown> | null {
    return (this.oauth.getIdentityClaims() as Record<string, unknown>) ?? null;
  }
}

function describeOAuthError(evt: OAuthErrorEvent): string {
  const reason = (evt as unknown as { reason?: unknown }).reason;
  let detail = '';
  if (reason && typeof reason === 'object') {
    const r = reason as Record<string, unknown>;
    const err = r['error'] ?? r['error_description'];
    const status = r['status'];
    const url = r['url'];
    if (err) detail += ` ${err}`;
    if (status) detail += ` (HTTP ${status})`;
    if (url) detail += ` from ${url}`;
  } else if (typeof reason === 'string') {
    detail = ` ${reason}`;
  }
  return `${evt.type}${detail}`.trim();
}
