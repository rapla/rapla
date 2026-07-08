import { beforeEach, describe, expect, it } from 'vitest';
import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { ClassificationEditComponent, type ClassificationPatch } from './classification-edit.component';

const SDL = `
type meetingClassification implements Classification & ReservationClassification {
  name: String @displayName(value : "Name") @required
  topic: String @displayName(value : "Thema")
  seats: Int @displayName(value : "Plätze")
  confirmed: Boolean @displayName(value : "Bestätigt")
  gruppe: farben @displayName(value : "Gruppe")
  raum: Allocatable @displayName(value : "Raum") @expectedType(key : "room")
  notiz: String @displayName(value : "Notiz") @editView(value : "additional")
  type: DynamicType!
  typeKey: String!
}

enum farben {
  "Rot"
  c1
  c2
}
`;

@Component({
  standalone: true,
  imports: [ClassificationEditComponent],
  template: `<app-classification-edit
    [typeKey]="'meeting'"
    [values]="values()"
    [disabled]="disabled()"
    [excludeKeys]="['name']"
    (patch)="patches.push($event)"
  />`,
})
class HostComponent {
  readonly values = signal<Record<string, unknown>>({
    name: 'Sync',
    topic: 'Q3',
    seats: 4,
    confirmed: true,
    gruppe: 'c1',
    raum: 'alloc-1',
    notiz: 'intern',
  });
  readonly disabled = signal(false);
  readonly patches: ClassificationPatch[] = [];
}

describe('ClassificationEditComponent (PRD 096 Phase 2)', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HostComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  async function create() {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    TestBed.inject(HttpTestingController).expectOne('/api/graphql/schema').flush(SDL);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  it('renders one widget per descriptor, honoring excludeKeys and the v1 widget map', async () => {
    const fixture = await create();
    const el = fixture.nativeElement as HTMLElement;
    const labels = Array.from(el.querySelectorAll('.lab')).map((l) => l.textContent?.trim());
    expect(labels).toEqual(['Thema', 'Plätze', 'Bestätigt', 'Gruppe', 'Raum']);
    expect(el.querySelector('input[type="text"]')).toBeTruthy();
    expect(el.querySelector('input[type="number"]')).toBeTruthy();
    expect(el.querySelector('input[type="checkbox"]')).toBeTruthy();
    const select = el.querySelector('select')!;
    expect(Array.from(select.options).map((o) => o.textContent?.trim())).toEqual([
      '',
      'Rot',
      'c2',
    ]);
    // unset placeholder is visible but NOT pickable (deliberate Swing deviation)
    expect(select.options[0].disabled).toBe(true);
    // ALLOCATABLE → read-only in v1, value still visible
    expect(el.querySelector('.ro')?.textContent).toContain('alloc-1');
  });

  it('emits controlled patches with label + coalesceKey; never mutates its input', async () => {
    const fixture = await create();
    const host = fixture.componentInstance;
    const el = fixture.nativeElement as HTMLElement;

    const topic = el.querySelector('input[type="text"]') as HTMLInputElement;
    topic.value = 'Q4';
    topic.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(host.patches.at(-1)).toEqual({
      key: 'topic',
      value: 'Q4',
      label: 'Thema',
      coalesceKey: 'values:topic',
    });
    // controlled: the input object is untouched until the host applies the patch
    expect(host.values()['topic']).toBe('Q3');

    const confirmed = el.querySelector('input[type="checkbox"]') as HTMLInputElement;
    confirmed.click();
    fixture.detectChanges();
    expect(host.patches.at(-1)).toEqual({
      key: 'confirmed',
      value: false,
      label: 'Bestätigt',
      coalesceKey: null,
    });

    const select = el.querySelector('select') as HTMLSelectElement;
    select.value = 'c2';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(host.patches.at(-1)).toEqual({
      key: 'gruppe',
      value: 'c2',
      label: 'Gruppe',
      coalesceKey: null,
    });
  });

  it('keeps additional (@editView additional) attributes out of the main grid, behind a collapsed expander', async () => {
    const fixture = await create();
    const el = fixture.nativeElement as HTMLElement;

    // 'Notiz' is @editView additional → NOT rendered in the always-visible grid
    const mainLabels = () =>
      Array.from(el.querySelectorAll('.lab')).map((l) => l.textContent?.trim());
    expect(mainLabels()).not.toContain('Notiz');

    const toggle = el.querySelector('.exp-toggle') as HTMLButtonElement;
    expect(toggle).toBeTruthy();
    expect(toggle.textContent).toContain('Weitere Felder');
    expect(toggle.textContent).toContain('1'); // one additional attribute
    expect(toggle.getAttribute('aria-expanded')).toBe('false');

    toggle.click();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(toggle.getAttribute('aria-expanded')).toBe('true');
    expect(mainLabels()).toContain('Notiz');
  });

  it('emits patches from an expanded additional field like any other', async () => {
    const fixture = await create();
    const host = fixture.componentInstance;
    const el = fixture.nativeElement as HTMLElement;

    (el.querySelector('.exp-toggle') as HTMLButtonElement).click();
    fixture.detectChanges();
    await fixture.whenStable();

    const notiz = el.querySelector('.exp-body input[type="text"]') as HTMLInputElement;
    expect(notiz).toBeTruthy();
    notiz.value = 'geändert';
    notiz.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(host.patches.at(-1)).toEqual({
      key: 'notiz',
      value: 'geändert',
      label: 'Notiz',
      coalesceKey: 'values:notiz',
    });
  });

  it('disables every widget in disabled mode', async () => {
    const fixture = await create();
    fixture.componentInstance.disabled.set(true);
    fixture.detectChanges();
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const widgets = el.querySelectorAll<HTMLInputElement | HTMLSelectElement>('input, select');
    expect(widgets.length).toBeGreaterThan(0);
    for (const w of Array.from(widgets)) {
      expect(w.disabled).toBe(true);
    }
  });
});
