import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

import {
  ResourceEditDialogComponent,
  type ResourceEditDialogData,
} from './resource-edit-dialog.component';
import { flushSchema } from '../classification/schema-testing';

const SDL = `
type kameraClassification implements ResourceClassification & Classification {
  name: String @displayName(value : "Name")
  beschreibung: String @displayName(value : "Beschreibung")
  type: DynamicType!
  typeKey: String!
}

type stativClassification implements ResourceClassification & Classification {
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
    resource: {
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
    resource: {
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
  data: { resource: { classification: { name: 'Canon G25 01', beschreibung: 'Vollformat' } } },
};

describe('ResourceEditDialogComponent (PRD 096 Phase 4)', () => {
  const closeSpy = vi.fn();

  async function setup(data: ResourceEditDialogData, shell = SHELL) {
    closeSpy.mockClear();
    await TestBed.configureTestingModule({
      imports: [ResourceEditDialogComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MAT_DIALOG_DATA, useValue: data },
        { provide: MatDialogRef, useValue: { close: closeSpy } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(ResourceEditDialogComponent);
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    // load order: shell query → (schema fetch) → values query
    http.expectOne((r) => r.url === '/api/graphql').flush(shell);
    fixture.detectChanges();
    if (shell.data.resource) {
      flushSchema(http, SDL);
      fixture.detectChanges();
      http.expectOne((r) => r.url === '/api/graphql').flush(VALUES);
      fixture.detectChanges();
      if (shell.data.resource.canModify && data.readOnly !== true) {
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

  it('loads shell + values, renders every attribute editable, saves via updateResource', async () => {
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
    expect(body.query).toContain('updateResource');
    expect(body.variables['input']).toEqual({
      typeKey: 'kamera',
      classification: { kamera: { name: 'Canon G25 01', beschreibung: 'APS-C' } },
    });
    expect(body.variables['expected']).toBe('2026-07-01T10:00:00');
    save.flush({ data: { updateResource: { id: 'a-1' } } });
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
    shell.data.resource!.canModify = false;
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
    save.flush({ data: { updateResource: { id: 'a-1' } } });
    expect(closeSpy).toHaveBeenCalledWith('saved');
  });

  it('unknown id shows "nicht gefunden" without leaking existence details', async () => {
    const { fixture } = await setup({ id: 'a-x' }, { data: { resource: null } });
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Nicht gefunden');
  });
});

describe('ResourceEditDialogComponent create mode (PRD 122)', () => {
  const closeSpy = vi.fn();
  const CREATE_SDL =
    SDL +
    `
type dozentClassification implements PersonClassification & Classification {
  name: String @displayName(value : "Name")
  type: DynamicType!
  typeKey: String!
}
`;
  const CREATABLE = {
    data: {
      newResourceOptions: {
        resourceTypes: [
          { key: 'dozent', name: 'Dozent', classificationType: 'PERSON' },
          { key: 'kamera', name: 'Kamera', classificationType: 'RESOURCE' },
          { key: 'stativ', name: 'Stativ', classificationType: 'RESOURCE' },
        ],
      },
    },
  };

  const gql = (http: HttpTestingController, field: string) =>
    http.expectOne(
      (r) => r.url === '/api/graphql' && (r.body as { query: string }).query.includes(field),
    );

  const PROTOS: Record<string, Record<string, unknown>> = {
    kamera: { name: null, beschreibung: 'Standard' },
    dozent: { name: null },
  };

  async function setup(
    typeKey = 'kamera',
    beforePrototype: (c: ResourceEditDialogComponent) => void = () => undefined,
  ) {
    closeSpy.mockClear();
    await TestBed.configureTestingModule({
      imports: [ResourceEditDialogComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MAT_DIALOG_DATA, useValue: { create: { typeKey } } },
        { provide: MatDialogRef, useValue: { close: closeSpy } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(ResourceEditDialogComponent);
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    gql(http, 'newResourceOptions').flush(CREATABLE);
    flushSchema(http, CREATE_SDL);
    fixture.detectChanges();
    beforePrototype(fixture.componentInstance);
    const proto = gql(http, 'resourcePrototype');
    expect((proto.request.body as { variables: { k: string } }).variables.k).toBe(typeKey);
    proto.flush({ data: { resourcePrototype: { classification: PROTOS[typeKey] } } });
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return { fixture, http };
  }

  beforeEach(() => TestBed.resetTestingModule());

  it('loads no resource, offers every creatable type of both kinds and prefills from the prototype', async () => {
    const { fixture, http } = await setup();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('h2')?.textContent).toContain('Neue Ressource');
    const select = el.querySelector('.typesel select') as HTMLSelectElement;
    expect(Array.from(select.options).map((o) => o.textContent?.trim())).toEqual([
      'Kamera',
      'Stativ',
      'Dozent',
    ]);
    expect(fixture.componentInstance.draft()?.values['beschreibung']).toBe('Standard');
    http.verify();
  });

  it('groups the creatable types under "Ressourcen" then "Personen" (ruling A3)', async () => {
    const { fixture } = await setup();
    const groups = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('.typesel select optgroup'),
    ).map((g) => ({
      label: g.getAttribute('label'),
      types: Array.from(g.querySelectorAll('option')).map((o) => o.textContent?.trim()),
    }));
    expect(groups).toEqual([
      { label: 'Ressourcen', types: ['Kamera', 'Stativ'] },
      { label: 'Personen', types: ['Dozent'] },
    ]);
  });

  it('a type change loads the target prototype; remapped values win over its defaults', async () => {
    const { fixture, http } = await setup();
    const c = fixture.componentInstance;
    c.applyPatch({ key: 'name', value: 'Mein Stativ', label: 'Name', coalesceKey: null });
    c.setTypeKey('stativ');
    const proto = gql(http, 'resourcePrototype');
    expect((proto.request.body as { variables: { k: string } }).variables.k).toBe('stativ');
    proto.flush({
      data: { resourcePrototype: { classification: { name: 'Stativ-Default', hoehe: 150 } } },
    });
    expect(c.draft()?.typeKey).toBe('stativ');
    expect(c.draft()?.values).toEqual({ name: 'Mein Stativ', hoehe: 150 });
    http.verify();
  });

  it('input typed before the prototype arrives survives the seeding (M1)', async () => {
    const { fixture } = await setup('kamera', (c) =>
      c.applyPatch({
        key: 'beschreibung',
        value: 'Eigene',
        label: 'Beschreibung',
        coalesceKey: null,
      }),
    );
    expect(fixture.componentInstance.draft()?.values).toEqual({
      name: null,
      beschreibung: 'Eigene',
    });
  });

  it('an untouched new resource is not dirty — the seeded defaults are the baseline (M2)', async () => {
    const { fixture } = await setup();
    const el = fixture.nativeElement as HTMLElement;
    expect(fixture.componentInstance.dirty()).toBe(false);
    expect((el.querySelector('.bar button.primary') as HTMLButtonElement).disabled).toBe(true);
    fixture.componentInstance.applyPatch({
      key: 'name',
      value: 'Canon R5',
      label: 'Name',
      coalesceKey: null,
    });
    expect(fixture.componentInstance.dirty()).toBe(true);
  });

  it('a person type titles the dialog "Neue Person", a resource type "Neue Ressource" (M4)', async () => {
    const { fixture, http } = await setup('dozent');
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('h2')?.textContent).toContain('Neue Person');
    fixture.componentInstance.setTypeKey('kamera');
    gql(http, 'resourcePrototype').flush({ data: { resourcePrototype: null } });
    fixture.detectChanges();
    expect(el.querySelector('h2')?.textContent).toContain('Neue Ressource');
  });

  it('saves via createResource with a fresh a… id and closes with "saved"', async () => {
    const { fixture, http } = await setup();
    const el = fixture.nativeElement as HTMLElement;
    fixture.componentInstance.applyPatch({
      key: 'name',
      value: 'Canon R5',
      label: 'Name',
      coalesceKey: null,
    });
    fixture.detectChanges();
    (el.querySelector('.bar button.primary') as HTMLButtonElement).click();
    const save = gql(http, 'createResource');
    const vars = (save.request.body as { variables: Record<string, unknown> }).variables;
    const input = vars['input'] as { id: string; typeKey: string; classification: unknown };
    expect(input.id).toMatch(/^a[0-9a-f]{7}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/);
    expect(input.id).toBe(fixture.componentInstance.entityId);
    expect(input.typeKey).toBe('kamera');
    expect(input.classification).toEqual({
      kamera: { name: 'Canon R5', beschreibung: 'Standard' },
    });
    expect(vars['expected']).toBeUndefined();
    save.flush({ data: { createResource: { id: input.id } } });
    expect(closeSpy).toHaveBeenCalledWith('saved');
  });
});
