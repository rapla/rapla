import { describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { of } from 'rxjs';

import { ApiKeysDialogComponent } from './api-keys-dialog.component';
import { ApiKeysService, ApiKeyCreated, ApiKeyMetadata } from './api-keys.service';

const KEY: ApiKeyMetadata = {
  id: 'k1',
  label: 'CI export',
  alg: 'RS256',
  thumbprint: 'abcd1234',
  createdAt: '2026-05-10T12:00:00Z',
  expiresAt: null,
  scopes: ['read', 'write_events'],
};

function configure(stub: Partial<ApiKeysService>) {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    imports: [ApiKeysDialogComponent],
    providers: [provideAnimationsAsync(), { provide: ApiKeysService, useValue: stub }],
  });
}

describe('ApiKeysDialogComponent', () => {
  it('renders the key list with scope chips', async () => {
    configure({ list: () => of([KEY]) });
    const fixture = TestBed.createComponent(ApiKeysDialogComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('CI export');
    expect(text).toContain('write_events');
    expect(text).toContain('…1234'); // last-4 thumbprint
  });

  it('shows the one-time secret after create', async () => {
    const created: ApiKeyCreated = { ...KEY, id: 'k2', key: 'eyJ.MY.SECRET' };
    const create = vi.fn(() => of(created));
    configure({ list: () => of([KEY]), create });
    const fixture = TestBed.createComponent(ApiKeysDialogComponent);
    const c = fixture.componentInstance;
    fixture.detectChanges();

    c.startCreate();
    c.form.controls.label.setValue('bot');
    c.toggleScope('write_events', true);
    c.submitCreate();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(create).toHaveBeenCalledWith({
      label: 'bot',
      expiresInDays: 180,
      scopes: expect.arrayContaining(['read', 'write_events']),
    });
    expect(c.freshSecret()).toBe('eyJ.MY.SECRET');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('eyJ.MY.SECRET');
  });

  it('rotates via the server endpoint with the chosen grace window and shows the new key', async () => {
    // PRD 076 Phase 6 / D12 — one server call mints a same-scope successor + grace-expires the old.
    const rotated: ApiKeyCreated = { ...KEY, id: 'k2', thumbprint: 'new9', key: 'eyJ.ROTATED' };
    const rotate = vi.fn(() => of(rotated));
    configure({ list: () => of([KEY]), rotate });
    const fixture = TestBed.createComponent(ApiKeysDialogComponent);
    const c = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();

    c.startRotate(KEY);
    expect(c.graceControl.value).toBe(180); // default grace window
    c.graceControl.setValue(60);
    c.confirmRotate(KEY);

    expect(rotate).toHaveBeenCalledWith('k1', 60);
    expect(c.freshSecret()).toBe('eyJ.ROTATED');
    expect(c.rotatingId()).toBeNull();
  });

  it('blocks rotate when the grace window exceeds 2 days', () => {
    const rotate = vi.fn(() => of({ ...KEY, key: 'unused' }));
    configure({ list: () => of([KEY]), rotate });
    const fixture = TestBed.createComponent(ApiKeysDialogComponent);
    const c = fixture.componentInstance;
    c.startRotate(KEY);
    c.graceControl.setValue(2881); // > 2 days
    c.confirmRotate(KEY);
    expect(rotate).not.toHaveBeenCalled();
  });

  it('only offers rotate for keys holding rotate_self (D15 Model B)', async () => {
    configure({ list: () => of([KEY, { ...KEY, id: 'kr', scopes: ['read', 'rotate_self'] }]) });
    const fixture = TestBed.createComponent(ApiKeysDialogComponent);
    const c = fixture.componentInstance;
    expect(c.isRotatable(KEY)).toBe(false); // read + write_events, no rotate_self
    expect(c.isRotatable({ ...KEY, scopes: ['read', 'rotate_self'] })).toBe(true);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    // Exactly one rotate (autorenew) button rendered — only for the rotate_self key.
    const html = (fixture.nativeElement as HTMLElement).innerHTML;
    expect((html.match(/autorenew/g) ?? []).length).toBe(1);
  });

  it('defaults a new key to a 180-day expiry', () => {
    configure({ list: () => of([KEY]) });
    const fixture = TestBed.createComponent(ApiKeysDialogComponent);
    const c = fixture.componentInstance;
    c.startCreate();
    expect(c.form.controls.expiresInDays.value).toBe(180);
  });

  it('shows expiry relative — minutes for a grace window, days for a long-lived key', () => {
    configure({ list: () => of([KEY]) });
    const c = TestBed.createComponent(ApiKeysDialogComponent).componentInstance;
    expect(c.expiryLabel(null)).toBe('no expiry');
    expect(c.expiryLabel(new Date(Date.now() - 1000).toISOString())).toBe('expired');
    expect(c.expiryLabel(new Date(Date.now() + 30 * 60_000).toISOString())).toBe('expires in 30 min');
    expect(c.expiryLabel(new Date(Date.now() + 5 * 86_400_000).toISOString())).toBe('expires in 5 days');
  });

  it('read scope cannot be removed from the selection', () => {
    configure({ list: () => of([KEY]) });
    const fixture = TestBed.createComponent(ApiKeysDialogComponent);
    const c = fixture.componentInstance;
    c.startCreate();
    c.toggleScope('read', false);
    expect(c.selectedScopes()).toContain('read');
  });
});
