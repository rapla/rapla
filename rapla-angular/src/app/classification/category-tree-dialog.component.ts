import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';

import type { CategoryRow } from './classification-schema';
import { TPipe } from '../i18n/i18n.service';

export interface CategoryTreeDialogData {
  label: string;
  rows: CategoryRow[];
  selected: string | null;
}

/**
 * PRD 096 Phase 5 — tree picker for an ORGANIZATION category attribute (Swing
 * AbstractSelectField.showDialog): root hidden, single selection, every node
 * selectable, double click on a leaf applies. Closes with the chosen id, null
 * for "Nichts ausgewählt" (Swing useNull, PRD 096 OQ4), or undefined on Abbrechen.
 */
@Component({
  selector: 'app-category-tree-dialog',
  standalone: true,
  imports: [TPipe, MatDialogModule, MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>{{ data.label }}</h2>
    <mat-dialog-content>
      <ul class="tree" role="tree" [attr.aria-label]="data.label">
        @for (r of visibleRows(); track r.id) {
          <li
            role="treeitem"
            [attr.aria-level]="r.depth + 1"
            [attr.aria-selected]="r.id === selected()"
            [attr.aria-expanded]="r.hasChildren ? expanded().has(r.id) : null"
            [class.sel]="r.id === selected()"
            [style.padding-left.rem]="r.depth * 1.25"
          >
            @if (r.hasChildren) {
              <button type="button" class="tog" (click)="toggle(r.id)">
                {{ expanded().has(r.id) ? '▾' : '▸' }}
              </button>
            } @else {
              <span class="tog"></span>
            }
            <button
              type="button"
              class="name"
              (click)="selected.set(r.id)"
              (dblclick)="leafApply(r)"
            >
              {{ r.name }}
            </button>
          </li>
        }
      </ul>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button matButton type="button" class="clear" (click)="ref.close(null)">
        {{ 'nothing_selected' | t }}
      </button>
      <button matButton type="button" class="cancel" (click)="ref.close()">
        {{ 'cancel' | t }}
      </button>
      <button
        matButton="filled"
        type="button"
        class="apply"
        [disabled]="!selected()"
        (click)="ref.close(selected() ?? undefined)"
      >
        {{ 'apply' | t }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .tree {
      list-style: none;
      margin: 0;
      padding: 0;
      min-width: 18rem;
    }
    li {
      display: flex;
      align-items: center;
      gap: 0.25rem;
      border-radius: 4px;
    }
    li.sel {
      background: color-mix(in srgb, var(--mat-sys-primary, #0061a4) 14%, transparent);
    }
    .tog {
      width: 1.5rem;
      flex: none;
      font: inherit;
      border: none;
      background: transparent;
      color: inherit;
      cursor: pointer;
    }
    .name {
      flex: 1;
      font: inherit;
      text-align: left;
      color: inherit;
      background: transparent;
      border: none;
      padding: 0.3rem 0.25rem;
      cursor: pointer;
      user-select: none;
    }
  `,
})
export class CategoryTreeDialogComponent {
  readonly data = inject<CategoryTreeDialogData>(MAT_DIALOG_DATA);
  readonly ref = inject<MatDialogRef<CategoryTreeDialogComponent, string | null>>(MatDialogRef);

  readonly selected = signal<string | null>(this.data.selected);
  readonly expanded = signal<ReadonlySet<string>>(this.ancestorsOf(this.data.selected));

  readonly visibleRows = computed(() => {
    const open = this.expanded();
    const shown = new Set<string>();
    return this.data.rows.filter((r) => {
      const visible = r.parentId === null || (shown.has(r.parentId) && open.has(r.parentId));
      if (visible) shown.add(r.id);
      return visible;
    });
  });

  toggle(id: string): void {
    const next = new Set(this.expanded());
    if (!next.delete(id)) next.add(id);
    this.expanded.set(next);
  }

  leafApply(r: CategoryRow): void {
    if (!r.hasChildren) this.ref.close(r.id);
  }

  private ancestorsOf(id: string | null): Set<string> {
    const byId = new Map(this.data.rows.map((r) => [r.id, r]));
    const out = new Set<string>();
    for (let p = byId.get(id ?? '')?.parentId; p; p = byId.get(p)?.parentId) out.add(p);
    return out;
  }
}
