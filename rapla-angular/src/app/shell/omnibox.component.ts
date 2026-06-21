import { Component, ElementRef, HostListener, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { switchMap } from 'rxjs';

import { SearchService } from '../search/search.service';
import type { SearchResult, SearchResultGroup } from '../search/search.types';
import { FilterStore } from '../state/filter-store';
import { ResourceSelectionStore } from '../state/resource-selection-store';

/**
 * The omnibox — one search box over the whole corpus. Each hit renders one
 * button per {@code action} it carries; the buttons drive the same stores the
 * ResourceSelection does (filter step / accumulate, group-load). Search is the
 * STUB {@link SearchService} for now; result shape is stable.
 */
@Component({
  selector: 'app-omnibox',
  imports: [FormsModule],
  template: `
    <div class="omnibox">
      <input
        class="obsearch"
        placeholder="Suchen… (Ressourcen, Veranstaltungen, Termine, Gruppen)"
        [ngModel]="term()"
        (ngModelChange)="onType($event)"
        (focus)="open.set(true)"
      />
      @if (showResults()) {
        <div class="results">
          @for (g of groups(); track g.kind) {
            <div class="group">
              <div class="ghead">{{ g.heading }}</div>
              @for (r of g.results; track r.id) {
                <div class="row">
                  <span class="dot" [style.background]="r.color ?? 'transparent'"></span>
                  <span class="meta">
                    <span class="lbl">
                      {{ r.label }}
                      @if (r.count != null) {
                        <span class="cnt">({{ r.count }})</span>
                      }
                    </span>
                    @if (r.sublabel) {
                      <span class="sub">{{ r.sublabel }}</span>
                    }
                  </span>
                  <span class="acts">
                    @for (a of r.actions; track a) {
                      <button class="act" [attr.data-action]="a" (click)="run(a, r)">
                        {{ actionLabel(a) }}
                      </button>
                    }
                  </span>
                </div>
              }
            </div>
          } @empty {
            <div class="none">— keine Treffer</div>
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
      }
      .row:hover {
        background: rgba(63, 81, 181, 0.06);
      }
      .row .dot {
        width: 0.55rem;
        height: 0.55rem;
        border-radius: 50%;
        flex: none;
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
      .row .cnt {
        color: rgba(0, 0, 0, 0.5);
        font-weight: 600;
      }
      .row .sub {
        font-size: 0.68rem;
        color: rgba(0, 0, 0, 0.5);
      }
      .acts {
        display: flex;
        gap: 0.3rem;
        flex: none;
      }
      .act {
        border: 1px solid rgba(0, 0, 0, 0.15);
        background: #fff;
        border-radius: 6px;
        padding: 0.2rem 0.45rem;
        font-size: 0.72rem;
        cursor: pointer;
        color: rgba(0, 0, 0, 0.7);
        white-space: nowrap;
      }
      .act:hover {
        background: rgba(63, 81, 181, 0.1);
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
  private readonly filter = inject(FilterStore);
  private readonly resources = inject(ResourceSelectionStore);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  protected readonly term = signal('');

  /** Whether the results dropdown is open (closes on Escape / outside-click / action). */
  protected readonly open = signal(false);

  protected readonly groups = toSignal(
    toObservable(this.term).pipe(switchMap((t) => this.search.search(t))),
    { initialValue: [] as SearchResultGroup[] },
  );

  protected readonly showResults = computed(() => this.open() && this.term().trim().length > 0);

  /** Typing reopens the dropdown and updates the query. */
  onType(value: string): void {
    this.term.set(value);
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

  protected actionLabel(action: SearchResult['actions'][number]): string {
    switch (action) {
      case 'filter-replace':
        return 'Belegung';
      case 'filter-add':
        return '+';
      case 'navigate':
        return '↵';
      case 'edit':
        return '✏️';
      case 'load-group':
        return 'in Liste laden';
    }
  }

  run(action: SearchResult['actions'][number], r: SearchResult): void {
    switch (action) {
      case 'filter-replace':
        this.filter.replace({
          id: r.id,
          kind: r.kind === 'event' ? 'event' : 'resource',
          label: r.label,
          color: r.color,
        });
        this.rememberResource(r);
        break;
      case 'filter-add':
        this.filter.add({
          id: r.id,
          kind: r.kind === 'event' ? 'event' : 'resource',
          label: r.label,
          color: r.color,
        });
        this.rememberResource(r);
        return; // keep the dropdown open so the user can accumulate more with +
      case 'navigate':
        this.navigate(r);
        break;
      case 'edit':
        this.edit(r);
        break;
      case 'load-group':
        this.resources.loadGroup(r.label, [{ id: r.id, label: r.label, color: r.color }]);
        break;
    }
    this.open.set(false); // a terminal action closes the dropdown
  }

  navigate(_r: SearchResult): void {}

  edit(_r: SearchResult): void {}

  /** A found resource that the user acted on lands in the ResourceSelection "Zuletzt" list. */
  private rememberResource(r: SearchResult): void {
    if (r.kind === 'resource') {
      this.resources.pushRecent({ id: r.id, label: r.label, color: r.color });
    }
  }
}
