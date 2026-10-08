import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { t } from '../i18n/i18n.service';
import { FilterStore } from '../state/filter-store';
import { ResourceSelectionStore, type ResourceItem } from '../state/resource-selection-store';
import { ViewStateStore } from '../state/view-state-store';
import { ImportSyncButtonComponent } from './import-sync-button.component';
import { ImportWorklistService } from './import-worklist.service';

/** dhbwrapla PRD 004 § H5a — the sync button lives in the filter row and is active only for
 *  1 to 19 effective Kurse (group chips count with their Kurse). Ported from the toolbar's
 *  Dualis-Sync spec: hidden without a source, "!" on failure, green when all linked. */
describe('ImportSyncButtonComponent', () => {
  const open = vi.fn(() => ({ afterClosed: () => of(undefined) }));
  const kurs = (id: string, parentIds: string[] = []) =>
    ({ id, label: id, parentIds }) as ResourceItem;
  const linkedOne = [
    { id: 'r1', name: 'Mathe', externalId: 'v:1', firstDate: '2026-10-12T08:00:00', resourceIds: ['k1'] },
  ];

  function setup(over: Record<string, unknown> = {}, resources: ResourceItem[] = []) {
    TestBed.resetTestingModule();
    open.mockClear();
    TestBed.configureTestingModule({
      imports: [ImportSyncButtonComponent],
      providers: [
        provideAnimationsAsync(),
        { provide: MatDialog, useValue: { open } },
        { provide: ResourceSelectionStore, useValue: { resources: signal(resources) } },
        {
          provide: ViewStateStore,
          useValue: { window: signal({ from: '2026-10-12T00:00:00', to: '2026-10-19T00:00:00' }) },
        },
        {
          provide: ImportWorklistService,
          useValue: {
            sourceName: signal('Dualis'),
            loadFailed: signal(false),
            linked: signal([]),
            worklist: signal(null),
            windowRange: signal(null),
            ensureLoaded: () => of(null),
            ...over,
          },
        },
      ],
    });
    return TestBed.inject(FilterStore);
  }

  function render() {
    const f = TestBed.createComponent(ImportSyncButtonComponent);
    f.detectChanges();
    return f.nativeElement as HTMLElement;
  }

  beforeEach(() => setup());

  it('is hidden when no import source is deployed', () => {
    setup({ sourceName: signal('') });
    expect(render().querySelector('button')).toBeNull();
  });

  it('asks for a Kurs in the filter instead of showing 0/0', () => {
    const button = render().querySelector('button') as HTMLButtonElement;
    expect(button.disabled).toBe(true);
    expect(button.textContent?.trim()).toBe(t('import_sync_pick_course', 'Dualis'));
    expect(render().querySelector('.import-sync-badge')).toBeNull();
  });

  it('asks to narrow below 50 when a group chip stands for 50 Kurse', () => {
    const kurse = Array.from({ length: 50 }, (_, i) => kurs(`k${i}`, ['group']));
    const store = setup({}, [kurs('group'), ...kurse]);
    store.add({ id: 'group', kind: 'resource', label: 'Gruppe' });
    const button = render().querySelector('button') as HTMLButtonElement;
    expect(button.disabled).toBe(true);
    expect(button.textContent?.trim()).toBe(t('import_sync_too_many', 'Dualis', 50, 50));
    expect(button.getAttribute('title')).toBe(t('import_sync_too_many_tooltip', 50));
  });

  it('is active for a Kurs chip and counts linked from the reservations', () => {
    const store = setup({ linked: signal(linkedOne), worklist: signal({ groups: [], items: [] }) }, [kurs('k1')]);
    store.add({ id: 'k1', kind: 'resource', label: 'STG-WIB25B' });
    const el = render();
    expect((el.querySelector('button') as HTMLButtonElement).disabled).toBe(false);
    expect(el.querySelector('.import-sync-badge')?.textContent?.trim()).toBe('1/1');
    expect(el.querySelector('.import-sync-badge')?.classList.contains('done')).toBe(true);
  });

  it('shows ! when the load failed and is never green then', () => {
    const store = setup({ loadFailed: signal(true), linked: signal(linkedOne) }, [kurs('k1')]);
    store.add({ id: 'k1', kind: 'resource', label: 'STG-WIB25B' });
    const badge = render().querySelector('.import-sync-badge') as HTMLElement;
    expect(badge.textContent?.trim()).toBe('!');
    expect(badge.classList.contains('done')).toBe(false);
  });

  it('opens the dialog for the Kurse behind a group chip', () => {
    const store = setup({}, [kurs('group'), kurs('k1', ['group']), kurs('k2', ['group'])]);
    store.add({ id: 'group', kind: 'resource', label: 'Gruppe' });
    (render().querySelector('button') as HTMLButtonElement).click();
    expect(open).toHaveBeenCalledTimes(1);
    const data = (open.mock.calls[0] as unknown[])[1] as { data: { groupIds: string[]; semester: string } };
    expect([...data.data.groupIds].sort()).toEqual(['k1', 'k2']);
    expect(data.data.semester).toBe('WiSe 2026/27');
  });

  it('is not green for a loaded empty selection (0/0 is not done) — ported toolbar rule', () => {
    const store = setup({ worklist: signal({ groups: [], items: [] }) }, [kurs('k1')]);
    store.add({ id: 'k1', kind: 'resource', label: 'STG-WIB25B' });
    const badge = render().querySelector('.import-sync-badge') as HTMLElement;
    expect(badge.textContent?.trim()).toBe('0/0');
    expect(badge.classList.contains('done')).toBe(false);
  });
});
