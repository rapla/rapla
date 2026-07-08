import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

import {
  AllocatableEditDialogComponent,
  type AllocatableEditDialogData,
} from './allocatable-edit-dialog.component';

const SDL = `
type kameraClassification implements AllocatableClassification & Classification {
  name: String @displayName(value : "Name")
  beschreibung: String @displayName(value : "Beschreibung")
  type: DynamicType!
  typeKey: String!
}

type stativClassification implements AllocatableClassification & Classification {
  name: String @displayName(value : "Name")
  hoehe: Int @displayName(value : "Höhe")
  type: DynamicType!
  typeKey: String!
}
`;

const TYPES = {
  data: {
    types: [
      { key: 'kamera', name: 'Kamera', classificationType: 'RESOURCE' },
      { key: 'stativ', name: 'Stativ', classificationType: 'RESOURCE' },
      { key: 'event', name: 'Veranstaltung', classificationType: 'RESERVATION' },
    ],
  },
};

interface ShellFixture {
  data: {
    allocatable: {
      id: string;
      displayName: string;
      lastModifiedAt: string;
      canModify: boolean;
      classification: {
        typeKey: string;
        type: { name: string; classificationType: string };
      };
    } | null;
  };
}

const SHELL: ShellFixture = {
  data: {
    allocatable: {
      id: 'a-1',
      displayName: 'Canon G25 01',
      lastModifiedAt: '2026-07-01T10:00:00Z',
      canModify: true,
      classification: {
        typeKey: 'kamera',
        type: { name: 'Kamera', classificationType: 'RESOURCE' },
      },
    },
  },
};

const VALUES = {
  data: { allocatable: { classification: { name: 'Canon G25 01', beschreibung: 'Vollformat' } } },
};

describe('AllocatableEditDialogComponent (PRD 096 Phase 4)', () => {
  const closeSpy = vi.fn();

  async function setup(data: AllocatableEditDialogData, shell = SHELL) {
    closeSpy.mockClear();
    await TestBed.configureTestingModule({
      imports: [AllocatableEditDialogComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MAT_DIALOG_DATA, useValue: data },
        { provide: MatDialogRef, useValue: { close: closeSpy } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(AllocatableEditDialogComponent);
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    // load order: shell query → (schema fetch) → values query
    http.expectOne((r) => r.url === '/api/graphql').flush(shell);
    fixture.detectChanges();
    if (shell.data.allocatable) {
      http.expectOne('/api/graphql/schema').flush(SDL);
      fixture.detectChanges();
      http.expectOne((r) => r.url === '/api/graphql').flush(VALUES);
      fixture.detectChanges();
      if (shell.data.allocatable.canModify && data.readOnly !== true) {
        // editable mode loads the same-kind type options for the type select
        http.expectOne((r) => r.url === '/api/graphql').flush(TYPES);
        fixture.detectChanges();
      }
    }
    await fixture.whenStable();
    fixture.detectChanges();
    return { fixture, http };
  }

  beforeEach(() => TestBed.resetTestingModule());

  it('loads shell + values, renders every attribute editable, saves via updateAllocatable', async () => {
    const { fixture, http } = await setup({ id: 'a-1' });
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('h2')?.textContent).toContain('Canon G25 01');
    expect(el.textContent).toContain('Kamera');

    const inputs = el.querySelectorAll<HTMLInputElement>('input[type="text"]');
    expect(inputs.length).toBe(2); // name IS an ordinary attribute here
    expect(inputs[0].value).toBe('Canon G25 01');

    inputs[1].value = 'APS-C';
    inputs[1].dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(fixture.componentInstance.dirty()).toBe(true);

    (el.querySelector('.bar button.primary') as HTMLButtonElement).click();
    fixture.detectChanges();
    const save = http.expectOne((r) => r.url === '/api/graphql');
    const body = save.request.body as { query: string; variables: Record<string, unknown> };
    expect(body.query).toContain('updateAllocatable');
    expect(body.variables['input']).toEqual({
      typeKey: 'kamera',
      classification: { kamera: { name: 'Canon G25 01', beschreibung: 'APS-C' } },
    });
    expect(body.variables['expected']).toBe('2026-07-01T10:00:00');
    save.flush({ data: { updateAllocatable: { id: 'a-1' } } });
    fixture.detectChanges();
    expect(closeSpy).toHaveBeenCalledWith('saved');
  });

  it('readOnly ("Anzeigen") disables widgets and hides Speichern', async () => {
    const { fixture } = await setup({ id: 'a-1', readOnly: true });
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('nur ansehen');
    const inputs = el.querySelectorAll<HTMLInputElement>('input');
    expect(inputs.length).toBeGreaterThan(0);
    for (const i of Array.from(inputs)) expect(i.disabled).toBe(true);
    expect(el.querySelector('.bar button.primary')).toBeNull();
  });

  it('canModify=false from the server forces view mode even without readOnly', async () => {
    const shell = structuredClone(SHELL);
    shell.data.allocatable!.canModify = false;
    const { fixture } = await setup({ id: 'a-1' }, shell);
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('nur ansehen');
    expect(el.querySelector('.bar button.primary')).toBeNull();
  });

  it('type change remaps same-key/same-type values and saves the new variant', async () => {
    const { fixture, http } = await setup({ id: 'a-1' });
    const el = fixture.nativeElement as HTMLElement;
    const select = el.querySelector('.typesel select') as HTMLSelectElement;
    expect(Array.from(select.options).map((o) => o.textContent?.trim())).toEqual([
      'Kamera',
      'Stativ',
    ]);

    select.value = 'stativ';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    const d = fixture.componentInstance.draft()!;
    expect(d.typeKey).toBe('stativ');
    // name survives (same key+type on stativ), beschreibung is dropped
    expect(d.values).toEqual({ name: 'Canon G25 01' });
    expect(fixture.componentInstance.dirty()).toBe(true);

    (el.querySelector('.bar button.primary') as HTMLButtonElement).click();
    fixture.detectChanges();
    const save = http.expectOne((r) => r.url === '/api/graphql');
    const body = save.request.body as { query: string; variables: Record<string, unknown> };
    expect(body.variables['input']).toEqual({
      typeKey: 'stativ',
      classification: { stativ: { name: 'Canon G25 01' } },
    });
    save.flush({ data: { updateAllocatable: { id: 'a-1' } } });
    expect(closeSpy).toHaveBeenCalledWith('saved');
  });

  it('unknown id shows "nicht gefunden" without leaking existence details', async () => {
    const { fixture } = await setup({ id: 'a-x' }, { data: { allocatable: null } });
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Nicht gefunden');
  });
});
