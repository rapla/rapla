import { describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

import {
  CategoryTreeDialogComponent,
  type CategoryTreeDialogData,
} from './category-tree-dialog.component';
import { flattenCategoryTree } from './classification-schema';

const ROWS = flattenCategoryTree([
  { id: 'a', name: 'A - E', children: [] },
  {
    id: 'k',
    name: 'Kabel',
    children: [
      { id: 'h', name: 'HDMI Kabel' },
      { id: 'x', name: 'XLR Kabel' },
    ],
  },
]);

describe('CategoryTreeDialogComponent (PRD 096 Phase 5 — Swing AbstractSelectField tree)', () => {
  let closed: unknown[];

  async function create(selected: string | null) {
    closed = [];
    TestBed.resetTestingModule();
    const data: CategoryTreeDialogData = { label: 'Gruppierung', rows: ROWS, selected };
    await TestBed.configureTestingModule({
      imports: [CategoryTreeDialogComponent],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: data },
        { provide: MatDialogRef, useValue: { close: (v?: unknown) => closed.push(v) } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(CategoryTreeDialogComponent);
    fixture.detectChanges();
    return fixture;
  }

  const names = (el: HTMLElement) =>
    Array.from(el.querySelectorAll('[role="treeitem"] .name')).map((n) => n.textContent?.trim());

  it('starts collapsed except the path to the current value', async () => {
    expect(names((await create(null)).nativeElement)).toEqual(['A - E', 'Kabel']);
    expect(names((await create('h')).nativeElement)).toEqual([
      'A - E',
      'Kabel',
      'HDMI Kabel',
      'XLR Kabel',
    ]);
  });

  it('expands a node on its toggle and applies a selected inner node', async () => {
    const fixture = await create(null);
    const el = fixture.nativeElement as HTMLElement;
    (el.querySelector('button.tog') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(names(el)).toEqual(['A - E', 'Kabel', 'HDMI Kabel', 'XLR Kabel']);
    (el.querySelectorAll('.name')[1] as HTMLElement).click();
    fixture.detectChanges();
    (el.querySelector('.apply') as HTMLButtonElement).click();
    expect(closed).toEqual(['k']);
  });

  it('applies a leaf on double click; Abbrechen closes without a value', async () => {
    const fixture = await create('h');
    const el = fixture.nativeElement as HTMLElement;
    (el.querySelectorAll('.name')[3] as HTMLElement).dispatchEvent(new MouseEvent('dblclick'));
    expect(closed).toEqual(['x']);
    (el.querySelector('.cancel') as HTMLButtonElement).click();
    expect(closed).toEqual(['x', undefined]);
  });

  it('"Nichts ausgewählt" closes with null so the value is cleared (PRD 096 OQ4, Swing useNull)', async () => {
    const el = (await create('h')).nativeElement as HTMLElement;
    (el.querySelector('.clear') as HTMLButtonElement).click();
    expect(closed).toEqual([null]);
  });
});
