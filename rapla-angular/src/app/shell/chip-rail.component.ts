import { Component, inject } from '@angular/core';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';

import { FilterStore } from '../state/filter-store';

/**
 * The chip rail — the visible, removable filter (= what the view shows). One
 * chip per {@link FilterStore} entry, colour-coded, with a per-chip remove and
 * a clear-all. Resources and events share the rail.
 */
@Component({
  selector: 'app-chip-rail',
  imports: [MatChipsModule, MatIconModule, MatButtonModule],
  template: `
    <div class="rail" [class.is-empty]="store.isEmpty()">
      <span class="rlabel">FILTER:</span>
      @if (store.isEmpty()) {
        <span class="empty-hint">— nichts ausgewählt</span>
      } @else {
        <mat-chip-set>
          @for (e of store.entries(); track e.id) {
            <mat-chip>
              <span class="cd" [style.background]="e.color ?? 'transparent'"></span>
              {{ e.label }}
              <button
                class="chip-remove"
                type="button"
                [attr.aria-label]="'Entfernen: ' + e.label"
                (click)="store.remove(e.id)"
              >
                <mat-icon>cancel</mat-icon>
              </button>
            </mat-chip>
          }
        </mat-chip-set>
        <button matButton class="clear-all" (click)="store.clear()">× alle</button>
      }
    </div>
  `,
  styles: [
    `
      .rail {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        flex-wrap: wrap;
        padding: 0.5rem 1rem;
        border-bottom: 1px solid rgba(0, 0, 0, 0.08);
        min-height: 2.5rem;
      }
      .rlabel {
        font-size: 0.7rem;
        font-weight: 700;
        letter-spacing: 0.04em;
        color: rgba(0, 0, 0, 0.55);
      }
      .empty-hint {
        font-size: 0.8rem;
        color: rgba(0, 0, 0, 0.45);
      }
      .cd {
        display: inline-block;
        width: 0.55rem;
        height: 0.55rem;
        border-radius: 50%;
        margin-right: 0.4rem;
        vertical-align: middle;
      }
      .chip-remove {
        border: none;
        background: transparent;
        cursor: pointer;
        padding: 0 0 0 0.2rem;
        color: rgba(0, 0, 0, 0.5);
        display: inline-flex;
        align-items: center;
      }
      .chip-remove mat-icon {
        font-size: 1.05rem;
        height: 1.05rem;
        width: 1.05rem;
      }
      .clear-all {
        font-size: 0.75rem;
      }
    `,
  ],
})
export class ChipRailComponent {
  protected readonly store = inject(FilterStore);
}
