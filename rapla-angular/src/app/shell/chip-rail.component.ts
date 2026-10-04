import { NgTemplateOutlet } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';

import { FilterStore } from '../state/filter-store';
import { TPipe } from '../i18n/i18n.service';

/**
 * The chip rail — the visible, removable filter (= what the view shows). One
 * chip per {@link FilterStore} entry, colour-coded, with a per-chip remove and
 * a clear-all. Resources and events share the rail.
 */
@Component({
  selector: 'app-chip-rail',
  imports: [NgTemplateOutlet, TPipe, MatChipsModule, MatIconModule, MatButtonModule],
  template: `
    <div class="rail" [class.is-empty]="store.isEmpty()">
      <span class="rlabel">{{ 'shell_filter_label' | t }}</span>
      @if (store.isEmpty()) {
        <span class="empty-hint">{{ 'shell_filter_empty' | t }}</span>
      } @else {
        <mat-chip-set>
          @if (resourceCount() >= FOLD_AT) {
            <mat-chip class="sum">
              {{ 'shell_n_resources' | t: resourceCount() }}
              <button
                class="chip-remove"
                type="button"
                [attr.aria-label]="
                  'shell_chip_remove' | t: ('shell_n_resources' | t: resourceCount())
                "
                (click)="removeResources()"
              >
                <mat-icon>cancel</mat-icon>
              </button>
            </mat-chip>
          }
          @for (e of resourceSingles(); track e.id) {
            <ng-container *ngTemplateOutlet="single; context: { $implicit: e }" />
          }
          @if (userCount() >= FOLD_AT) {
            <mat-chip class="sum">
              {{ 'shell_n_users' | t: userCount() }}
              <button
                class="chip-remove"
                type="button"
                [attr.aria-label]="'shell_chip_remove' | t: ('shell_n_users' | t: userCount())"
                (click)="removeUsers()"
              >
                <mat-icon>cancel</mat-icon>
              </button>
            </mat-chip>
          }
          @for (e of otherSingles(); track e.id) {
            <ng-container *ngTemplateOutlet="single; context: { $implicit: e }" />
          }
        </mat-chip-set>
        <ng-template #single let-e>
          <mat-chip>
            <span class="cd" [style.background]="e.color ?? 'transparent'"></span>
            {{ e.label }}
            <button
              class="chip-remove"
              type="button"
              [attr.aria-label]="'shell_chip_remove' | t: e.label"
              (click)="store.remove(e.id)"
            >
              <mat-icon>cancel</mat-icon>
            </button>
          </mat-chip>
        </ng-template>
        <button matButton class="clear-all" (click)="store.clear()">
          {{ 'shell_clear_all' | t }}
        </button>
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
  /** PRD 123 D8 — from this many resource chips on, they fold into one "N Ressourcen" chip; user chips fold the same way into "N Benutzer". */
  protected readonly FOLD_AT = 10;
  protected readonly resourceCount = computed(
    () => this.store.entries().filter((e) => e.kind === 'resource').length,
  );
  protected readonly userCount = computed(
    () => this.store.entries().filter((e) => e.kind === 'user').length,
  );
  protected readonly resourceSingles = computed(() =>
    this.resourceCount() >= this.FOLD_AT
      ? []
      : this.store.entries().filter((e) => e.kind === 'resource'),
  );
  /** User chips (unless folded) and events — after the resource chips (PRD 127 OQ6: resources before users). */
  protected readonly otherSingles = computed(() =>
    this.store
      .entries()
      .filter(
        (e) => e.kind !== 'resource' && (e.kind !== 'user' || this.userCount() < this.FOLD_AT),
      ),
  );

  protected removeResources(): void {
    this.store.setAll(this.store.entries().filter((e) => e.kind !== 'resource'));
  }

  protected removeUsers(): void {
    this.store.setAll(this.store.entries().filter((e) => e.kind !== 'user'));
  }
}
