import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { provideRouter } from '@angular/router';
import { provideOAuthClient } from 'angular-oauth2-oidc';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';

import { LoginComponent } from './login.component';
import { AuthService, OAuthDiscovery, OAuthProviderEntry } from './auth.service';

function provider(id: string, order = 10, webPickerVisible = true, extraParams: Record<string, string> = {}): OAuthProviderEntry {
  return {
    id,
    displayName: 'Sign in with ' + id,
    icon: id,
    order,
    webPickerVisible,
    clientId: id + '-client',
    issuer: 'https://issuer/' + id,
    authorizeUrl: 'https://idp/' + id + '/authorize',
    tokenUrl: 'https://idp/' + id + '/token',
    jwksUrl: 'https://idp/' + id + '/jwks',
    endSessionUrl: 'https://idp/' + id + '/logout',
    scopes: ['openid', 'profile'],
    extraAuthorizeParams: extraParams,
  };
}

function discovery(mode: 'auto' | 'always' | 'never', providers: OAuthProviderEntry[], primary = 'rapla'): OAuthDiscovery {
  return {
    enabled: true,
    clientId: 'rapla-client',
    issuer: 'http://localhost:8051',
    authorizeUrl: 'http://localhost:8051/oauth2/authorize',
    tokenUrl: 'http://localhost:8051/oauth2/token',
    refreshUrl: 'http://localhost:8051/api/auth/refresh',
    logoutUrl: 'http://localhost:8051/connect/logout',
    jwksUrl: 'http://localhost:8051/oauth2/jwks',
    userinfoUrl: 'http://localhost:8051/userinfo',
    endSessionUrl: 'http://localhost:8051/connect/logout',
    scopes: ['openid', 'profile'],
    picker: { mode, primary },
    providers,
  };
}

function setUpComponent() {
  TestBed.configureTestingModule({
    imports: [LoginComponent],
    providers: [
      provideAnimationsAsync(),
      provideRouter([]),
      provideOAuthClient(),
      {
        provide: ActivatedRoute,
        useValue: { snapshot: { queryParamMap: convertToParamMap({}) } },
      },
    ],
  });
}

describe('LoginComponent picker (PRD 036)', () => {
  beforeEach(() => {
    sessionStorage.clear();
    setUpComponent();
  });

  it('renders a button per webPickerVisible:true provider in mode=auto with >=2 providers', () => {
    const auth = TestBed.inject(AuthService);
    auth.setDiscovery(discovery('auto', [
      provider('rapla', 0, false),
      provider('microsoft', 10),
      provider('google', 20),
    ]));

    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();

    const buttons = fixture.nativeElement.querySelectorAll('button[data-provider-id]');
    expect(buttons.length).toBe(2);
    expect(buttons[0].getAttribute('data-provider-id')).toBe('microsoft');
    expect(buttons[1].getAttribute('data-provider-id')).toBe('google');
  });

  it('does NOT render picker in mode=auto with a single visible provider (auto-fires instead)', () => {
    const auth = TestBed.inject(AuthService);
    auth.setDiscovery(discovery('auto', [
      provider('rapla', 0, false),
      provider('microsoft', 10),
    ]));
    vi.spyOn(auth, 'signIn').mockImplementation(() => undefined);

    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();

    const buttons = fixture.nativeElement.querySelectorAll('button[data-provider-id]');
    expect(buttons.length).toBe(0);
  });

  it('renders picker in mode=always even with a single visible provider', () => {
    const auth = TestBed.inject(AuthService);
    auth.setDiscovery(discovery('always', [
      provider('microsoft', 10),
    ]));

    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();

    const buttons = fixture.nativeElement.querySelectorAll('button[data-provider-id]');
    expect(buttons.length).toBe(1);
    expect(buttons[0].getAttribute('data-provider-id')).toBe('microsoft');
  });

  it('does NOT render picker in mode=never even with many providers', () => {
    const auth = TestBed.inject(AuthService);
    auth.setDiscovery(discovery('never', [
      provider('microsoft', 10),
      provider('google', 20),
    ]));
    vi.spyOn(auth, 'signIn').mockImplementation(() => undefined);

    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();

    const buttons = fixture.nativeElement.querySelectorAll('button[data-provider-id]');
    expect(buttons.length).toBe(0);
  });

  it('sorts picker buttons by `order` ascending', () => {
    const auth = TestBed.inject(AuthService);
    auth.setDiscovery(discovery('always', [
      provider('google', 20),
      provider('microsoft', 10),
      provider('thirdparty', 30),
    ]));

    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();

    const buttons = fixture.nativeElement.querySelectorAll('button[data-provider-id]');
    const ids = Array.from(buttons).map((b: unknown) => (b as HTMLElement).getAttribute('data-provider-id'));
    expect(ids).toEqual(['microsoft', 'google', 'thirdparty']);
  });

  it('clicking a provider button calls signInWithProvider with the right id', () => {
    const auth = TestBed.inject(AuthService);
    auth.setDiscovery(discovery('always', [
      provider('microsoft', 10),
      provider('google', 20),
    ]));
    const spy = vi.spyOn(auth, 'signInWithProvider').mockImplementation(() => undefined);

    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();

    const googleBtn = fixture.nativeElement.querySelector('button[data-provider-id="google"]') as HTMLButtonElement;
    googleBtn.click();

    expect(spy).toHaveBeenCalledWith('google');
  });

  it('filters out providers with webPickerVisible:false', () => {
    const auth = TestBed.inject(AuthService);
    auth.setDiscovery(discovery('always', [
      provider('rapla', 0, false),       // hidden
      provider('microsoft', 10, true),   // visible
      provider('google', 20, false),     // hidden
    ]));

    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();

    const buttons = fixture.nativeElement.querySelectorAll('button[data-provider-id]');
    expect(buttons.length).toBe(1);
    expect(buttons[0].getAttribute('data-provider-id')).toBe('microsoft');
  });
});
