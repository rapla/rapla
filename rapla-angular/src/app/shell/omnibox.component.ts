import { Component, ElementRef, HostListener, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { asyncScheduler, switchMap, throttleTime } from 'rxjs';
import { SearchService, MIN_QUERY_LENGTH } from '../search/search.service';
import type { SearchResult, SearchResultGroup } from '../search/search.types';
import { ResourceSelectionStore } from '../state/resource-selection-store';
import { ViewStateStore } from '../state/view-state-store';
import { EventSheetComponent, type EventSheetDialogData } from '../event/event-sheet.component';
import { todayWindow } from './view-control-strip.component';
import { entityIcon } from './entity-icon';

/** Span used when no window is set yet: one week (Monday to Monday). */
const ONE_WEEK = { from: '2026-01-05T00:00:00', to: '2026-01-12T00:00:00' };

/** PRD 106 pattern — at most one server search per interval while typing, last term always sent. */
const SEARCH_THROTTLE_MS = 300;

/**
 * The one search field of the shell (PRD 119 D3/D4). Every keystroke narrows the resource picker
 * on the left (shared query, no server call); from {@link MIN_QUERY_LENGTH} characters the
 * dropdown lists matching EVENTS — a hit jumps to its week and opens its sheet. A first row points
 * to the resources and groups the picker now shows.
 */
@Component({
  selector: 'app-omnibox',
  imports: [FormsModule, MatIconModule],
  template: `
    <div class="omnibox">
      <input
        class="obsearch"
        placeholder="Suchen… (Ressourcen, Veranstaltungen)"
        [ngModel]="term()"
        (ngModelChange)="onType($event)"
        (focus)="open.set(true)"
      />
      @if (showResults()) {
        <div class="results">
          <button type="button" class="countrow" (click)="focusPicker()">
            {{ store.matchCount() }} Ressourcen und Gruppen in der Liste links
          </button>
          @for (g of groups(); track g.kind) {
            <div class="group">
              <div class="ghead">{{ g.heading }}</div>
              @for (r of g.results; track r.id) {
                <button type="button" class="row" (click)="openEvent(r)">
                  <mat-icon class="ico" [style.color]="r.color || null">{{ icon(r) }}</mat-icon>
                  <span class="meta">
                    <span class="lbl">{{ r.label }}</span>
                    @if (r.sublabel) {
                      <span class="sub">{{ r.sublabel }}</span>
                    }
                  </span>
                </button>
              }
            </div>
          } @empty {
            <div class="none">— keine Veranstaltungen</div>
          }
        </div>
      }
    </div>
  `,
  styles: [
    `
      :host {
        display: block;
        width: 100%;
      }
      .omnibox {
        position: relative;
        width: 100%;
      }
      .obsearch {
        width: 100%;
        height: 2.5rem;
        border: 1px solid rgba(0, 0, 0, 0.15);
        border-radius: 8px;
        padding: 0 0.9rem;
        font-size: 0.95rem;
        background: #fff;
      }
      .results {
        position: absolute;
        top: calc(100% + 4px);
        left: 0;
        right: 0;
        z-index: 1000;
        background: #fff;
        border: 1px solid rgba(0, 0, 0, 0.12);
        border-radius: 8px;
        box-shadow: 0 6px 18px rgba(0, 0, 0, 0.18);
        max-height: 60vh;
        overflow: auto;
      }
      .countrow,
      .row {
        width: 100%;
        border: none;
        background: transparent;
        text-align: left;
        font: inherit;
      }
      .countrow {
        padding: 0.55rem 0.9rem;
        font-size: 0.8rem;
        cursor: pointer;
        color: var(--mat-sys-primary, #3f51b5);
        border-bottom: 1px solid rgba(0, 0, 0, 0.08);
      }
      .countrow:hover {
        background: rgba(63, 81, 181, 0.06);
      }
      .group {
        padding-bottom: 0.4rem;
      }
      .ghead {
        padding: 0.4rem 0.9rem 0.25rem;
        font-size: 0.68rem;
        font-weight: 700;
        letter-spacing: 0.04em;
        color: rgba(0, 0, 0, 0.55);
      }
      .row {
        display: flex;
        align-items: center;
        gap: 0.55rem;
        padding: 0.4rem 0.9rem;
        font-size: 0.84rem;
        cursor: pointer;
      }
      .row:hover {
        background: rgba(63, 81, 181, 0.06);
      }
      .row .ico {
        flex: none;
        font-size: 1.15rem;
        width: 1.15rem;
        height: 1.15rem;
        color: rgba(0, 0, 0, 0.55);
      }
      .row .meta {
        flex: 1;
        display: flex;
        flex-direction: column;
        overflow: hidden;
      }
      .row .lbl {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      .row .sub {
        font-size: 0.68rem;
        color: rgba(0, 0, 0, 0.5);
      }
      .none {
        padding: 0.6rem 0.9rem;
        font-size: 0.78rem;
        color: rgba(0, 0, 0, 0.45);
      }
    `,
  ],
})
export class OmniboxComponent {
  private readonly search = inject(SearchService);
  protected readonly store = inject(ResourceSelectionStore);
  private readonly viewState = inject(ViewStateStore);
  private readonly dialog = inject(MatDialog);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  protected readonly term = signal('');
  /** Whether the results dropdown is open (closes on Escape / outside-click / action). */
  protected readonly open = signal(false);

  protected readonly groups = toSignal(
    toObservable(this.term).pipe(
      throttleTime(SEARCH_THROTTLE_MS, asyncScheduler, { leading: true, trailing: true }),
      switchMap((t) => this.search.search(t)),
    ),
    { initialValue: [] as SearchResultGroup[] },
  );

  protected readonly showResults = computed(
    () => this.open() && this.term().length >= MIN_QUERY_LENGTH,
  );

  /** Typing narrows the picker at once (no minimum) and reopens the dropdown. */
  onType(value: string): void {
    this.term.set(value);
    this.store.setQuery(value);
    this.open.set(true);
  }

  @HostListener('document:keydown.escape')
  protected closeOnEscape(): void {
    this.open.set(false);
  }

  @HostListener('document:click', ['$event'])
  protected closeOnOutsideClick(event: MouseEvent): void {
    if (!this.host.nativeElement.contains(event.target as Node)) {
      this.open.set(false);
    }
  }

  /** Material icon for a result row (by kind + rapla type key). */
  protected icon(r: SearchResult): string {
    return entityIcon(r.kind, r.sublabel);
  }

  /** The count row sends the user to the picker, where the resource hits live. */
  focusPicker(): void {
    this.store.requestPickerFocus();
    this.open.set(false);
  }

  /** PRD 119 D4 — jump to the week of the event's first occurrence and open its sheet. */
  openEvent(r: SearchResult): void {
    if (r.start) {
      this.viewState.setWindow(
        todayWindow(this.viewState.window() ?? ONE_WEEK, new Date(`${r.start}Z`)),
      );
    }
    this.dialog.open(EventSheetComponent, {
      data: { id: r.id, searchHint: true } satisfies EventSheetDialogData,
      width: '960px',
      maxWidth: '95vw',
      height: '90vh',
      restoreFocus: false,
    });
    this.open.set(false);
  }
}
