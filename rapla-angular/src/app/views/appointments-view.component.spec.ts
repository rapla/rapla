import { describe, it, expect, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { AppointmentsViewComponent } from './appointments-view.component';
import { GraphqlService, GqlResponse } from '../graphql/graphql.service';
import { AppointmentsData } from './appointments-view';

function stubGql(response: GqlResponse<AppointmentsData>): Partial<GraphqlService> {
  // query<T> is generic; a fixed-type stub can't satisfy it directly — cast.
  return { query: () => of(response) } as unknown as Partial<GraphqlService>;
}

function stubGqlError(status: number): Partial<GraphqlService> {
  return { query: () => throwError(() => ({ status })) } as unknown as Partial<GraphqlService>;
}

const ONE_ROW: GqlResponse<AppointmentsData> = {
  data: {
    appointmentBlocks: [
      {
        name: 'Programmieren II',
        start: '2026-06-15T08:00:00',
        duration: '2 UE 0 Min',
        persons: [{ name: 'Prof. X' }, { name: 'Dr. A' }],
        resources: [{ name: 'H004 Seminarraum' }],
      },
    ],
  },
};

async function mount(response: GqlResponse<AppointmentsData>) {
  TestBed.configureTestingModule({
    imports: [AppointmentsViewComponent],
    providers: [{ provide: GraphqlService, useValue: stubGql(response) }],
  });
  const fixture = TestBed.createComponent(AppointmentsViewComponent);
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();
  return fixture;
}

describe('AppointmentsViewComponent', () => {
  beforeEach(() => TestBed.resetTestingModule());

  it('renders the fallback headers in order', async () => {
    const fixture = await mount(ONE_ROW);
    const headers = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('th.mat-mdc-header-cell'),
    ).map((th) => th.textContent?.trim());
    expect(headers).toEqual(['Veranstaltung', 'Beginn', 'Dauer', 'Dozent', 'Raum']);
  });

  it('joins list cells (Dozent) with the convention separator', async () => {
    const fixture = await mount(ONE_ROW);
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Prof. X; Dr. A');
    expect(text).toContain('Programmieren II');
    expect(text).toContain('2 UE 0 Min');
  });

  it('prefers server extensions.view over the fallback', async () => {
    const fixture = await mount({
      ...ONE_ROW,
      extensions: {
        view: { key: 'appointments', title: 'Server-Titel', columns: [{ alias: 'duration', header: 'Nur Dauer' }] },
      },
    });
    const headers = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('th.mat-mdc-header-cell'),
    ).map((th) => th.textContent?.trim());
    expect(headers).toEqual(['Nur Dauer']);
  });

  it('shows an error message when the response carries errors', async () => {
    const fixture = await mount({ errors: [{ message: 'boom' }] });
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('boom');
  });

  it('does NOT surface a 401 as a view error (auth layer owns it)', async () => {
    TestBed.configureTestingModule({
      imports: [AppointmentsViewComponent],
      providers: [{ provide: GraphqlService, useValue: stubGqlError(401) }],
    });
    const fixture = TestBed.createComponent(AppointmentsViewComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('.error')).toBeNull();
  });

  it('DOES surface a non-auth error (e.g. 500)', async () => {
    TestBed.configureTestingModule({
      imports: [AppointmentsViewComponent],
      providers: [{ provide: GraphqlService, useValue: stubGqlError(500) }],
    });
    const fixture = TestBed.createComponent(AppointmentsViewComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('.error')).not.toBeNull();
  });
});
