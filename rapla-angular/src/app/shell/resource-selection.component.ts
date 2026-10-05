import {
  Component,
  ElementRef,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatMenuModule, MatMenuTrigger } from '@angular/material/menu';

import {
  ResourceEditDialogComponent,
  type ResourceEditDialogData,
} from '../resource/resource-edit-dialog.component';
import { ResourceDataService } from '../resource/resource-data.service';
import { ResourcePickerComponent, type PickEvent } from '../resource/resource-picker.component';
import { ResourceSelectionStore, type ResourceItem } from '../state/resource-selection-store';
import { FilterStore, type FilterEntry } from '../state/filter-store';
import { ReviewStore } from '../state/review-store';
import type { SelectionSource } from '../views/view-catalog.service';
import { ViewStateStore } from '../state/view-state-store';
import { ONE_WEEK, todayWindow } from './view-control-strip.component';
import { TableSelection } from '../views/table-selection';
import { TPipe } from '../i18n/i18n.service';

/**
 * The persistent left ResourceSelection: a header row with "+ Neu" (PRD 122; no label since
 * PRD 127) and the shared {@link ResourcePickerComponent} in rail mode (PRD 123 D1).
 * A plain click {@link FilterStore.replace}s the filter with that resource — the
 * click-to-step rhythm — and marks it active ("▶ gezeigt"); Strg/Shift follow the selection
 * engine (PRD 099).
 */
@Component({
  selector: 'app-resource-selection',
  imports: [TPipe, MatMenuModule, ResourcePickerComponent],
  template: `
    <div class="stepper" tabindex="0" (keydown)="onListKeydown($event)">
      <div class="sthead">
        <input
          class="pksearch"
          type="search"
          [placeholder]="'event_resource_search_placeholder' | t"
          [value]="store.query()"
          (input)="store.setQuery($any($event.target).value)"
          (keydown)="onSearchKeydown($event)"
        />
        @if (source() === 'resources' && newTypeKey()) {
          <button
            type="button"
            class="newbtn"
            (click)="newResource()"
            [title]="'shell_new_resource_tooltip' | t"
          >
            + {{ 'new' | t }}
          </button>
        }
      </div>
      <app-resource-picker
        mode="rail"
        [source]="source()"
        [chip]="store.activeChip() ?? 'none'"
        (chipChange)="store.setActiveChip($event)"
        [query]="store.query()"
        (queryChange)="store.setQuery($event)"
        [checked]="selectedIds()"
        [activeId]="store.activeId()"
        (pick)="step($event)"
        (selectGroup)="selectGroup($event)"
        (menu)="openMenu($event)"
        (typeClick)="lastType.set($event)"
        (reviewPick)="reviewPick($event)"
      />
      <mat-menu #itemMenu="matMenu">
        <ng-template matMenuContent let-item="item">
          <button mat-menu-item (click)="openResource(item, false)">{{ 'edit' | t }}</button>
          <button mat-menu-item (click)="openResource(item, true)">{{ 'shell_show' | t }}</button>
        </ng-template>
      </mat-menu>
      <span
        class="menu-anchor"
        [style.left.px]="menuX()"
        [style.top.px]="menuY()"
        [matMenuTriggerFor]="itemMenu"
        #menuTrigger="matMenuTrigger"
      ></span>
    </div>
  `,
  styles: [
    `
      .stepper {
        width: 100%;
        display: flex;
        flex-direction: column;
        overflow: auto;
        height: 100%;
      }
      .stepper:focus {
        outline: none;
      }
      .sthead {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        padding: 0.75rem 0.9rem 0.5rem;
        font-size: 0.7rem;
        font-weight: 700;
        letter-spacing: 0.04em;
        color: rgba(0, 0, 0, 0.55);
      }
      .pksearch {
        flex: 1;
        min-width: 0;
        height: 2.5rem;
        border: 1px solid rgba(0, 0, 0, 0.15);
        border-radius: 8px;
        padding: 0 0.9rem;
        font: inherit;
        font-size: 0.95rem;
        font-weight: 400;
        letter-spacing: normal;
        color: rgba(0, 0, 0, 0.87);
        background: #fff;
      }
      .newbtn {
        flex: none;
        height: 2.5rem;
        font: inherit;
        font-size: 0.95rem;
        padding: 0 0.9rem;
        border: 1px solid rgba(0, 0, 0, 0.15);
        border-radius: 8px;
        background: transparent;
        color: inherit;
        cursor: pointer;
      }
      .menu-anchor {
        position: fixed;
        width: 0;
        height: 0;
      }
    `,
  ],
})
export class ResourceSelectionComponent {
  protected readonly store = inject(ResourceSelectionStore);
  protected readonly filter = inject(FilterStore);
  private readonly dialog = inject(MatDialog);
  private readonly resourceData = inject(ResourceDataService);
  private readonly review = inject(ReviewStore);
  /** PRD 128 D7 — the open view's selection source. */
  readonly source = input<SelectionSource>('resources');
  private readonly viewState = inject(ViewStateStore);
  private readonly picker = viewChild.required(ResourcePickerComponent);
  private readonly menuTrigger = viewChild.required<MatMenuTrigger>('menuTrigger');

  protected readonly selectedIds = computed(() => new Set(this.filter.entries().map((e) => e.id)));

  protected readonly menuX = signal(0);
  protected readonly menuY = signal(0);

  /**
   * PRD 099 Phase 4 — the shared {@link TableSelection} engine (Swing tree
   * parity, DISCONTIGUOUS_TREE_SELECTION semantics): plain click = REPLACE,
   * Strg/⌘ = TOGGLE, Shift = RANGE from the anchor (replacing), arrows step,
   * Shift+arrows extend. The FilterStore chips stay the source of truth —
   * the model re-syncs from them before every interaction, the interaction
   * result mirrors back ({@link applySelection}).
   *
   * Stepping only views — it never reorders the list (Recents stay stable).
   */
  private readonly selection = new TableSelection<string>();

  constructor() {
    this.review.ensureLoaded();
    this.resourceData.creatableTypes().subscribe({
      next: (types) => this.creatable.set(types),
      error: () => this.creatable.set([]),
    });
    effect(() =>
      this.selection.setRows(
        this.picker()
          .shown()
          .map((it) => it.id),
      ),
    );
    effect(() => {
      const request = this.store.pickerFocus();
      if (request === this.focusRequest) return;
      this.focusRequest = request;
      this.host.nativeElement.querySelector<HTMLElement>('input.pksearch')?.focus();
    });
    // PRD 123 D4 — Enter in the search field steps the first row the picker shows.
    effect(() => {
      const request = this.store.activateFirst();
      if (request.n === this.activateRequest) return;
      this.activateRequest = request.n;
      const first = untracked(() => this.picker().shown()[0]);
      if (first) untracked(() => this.step({ item: first, ctrl: request.ctrl, shift: false }));
    });
  }

  private activateRequest = this.store.activateFirst().n;

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  /** The focus request already served — the effect's first run must not steal focus. */
  private focusRequest = this.store.pickerFocus();

  /** PRD 123 D4 — Enter steps the first shown row (Ctrl/⌘ = add), ↓ focuses the list, Esc clears. */
  onSearchKeydown(event: KeyboardEvent): void {
    event.stopPropagation();
    if (event.key === 'Escape') this.store.setQuery('');
    else if (event.key === 'ArrowDown') {
      event.preventDefault();
      this.host.nativeElement.querySelector<HTMLElement>('.stepper')?.focus();
    } else if (event.key === 'Enter') {
      event.preventDefault();
      this.store.requestActivateFirst(event.ctrlKey || event.metaKey);
    }
  }

  /** PRD 128 D1/D3/OQ15 — a conflict/request click replaces the chips of its context (Ctrl toggles) and jumps to its date. */
  reviewPick(e: { entry: FilterEntry; date: string | null; ctrl: boolean }): void {
    if (!e.ctrl) this.filter.replace(e.entry);
    else if (this.filter.has(e.entry.id)) this.filter.remove(e.entry.id);
    else this.filter.add(e.entry);
    if (e.date)
      this.viewState.setWindow(
        todayWindow(
          this.viewState.window() ?? ONE_WEEK,
          new Date(`${e.date}Z`),
          this.viewState.renderMode(),
        ),
      );
  }

  private entry(r: ResourceItem): FilterEntry {
    return { id: r.id, kind: r.kind ?? 'resource', label: r.label, color: r.color };
  }

  step(e: PickEvent): void {
    // A pick in the same tick as a list switch ("meine") must not wait for the setRows effect.
    this.selection.setRows(
      this.picker()
        .shown()
        .map((it) => it.id),
    );
    this.selection.syncSelected(this.filter.entries().map((e) => e.id));
    this.selection.pointer(e.item.id, { shift: e.shift, ctrl: e.ctrl });
    const plain = !e.shift && !e.ctrl;
    this.applySelection(plain);
    this.store.setActive(e.item.id);
    // PRD 119 D5 — a selection pushes a recent; not while stepping the Zuletzt list itself.
    if (plain && this.store.activeChip() !== 'recents') this.store.pushRecent(e.item);
  }

  onListKeydown(event: KeyboardEvent): void {
    // The tab/★/⋮ buttons and the type select own their keys.
    if (
      event.target instanceof HTMLInputElement ||
      event.target instanceof HTMLButtonElement ||
      event.target instanceof HTMLSelectElement
    )
      return;
    if (event.key === 'Enter') return; // the picker row emits pick itself
    this.selection.syncSelected(this.filter.entries().map((e) => e.id));
    const handled = this.selection.key(event.key, {
      shift: event.shiftKey,
      ctrl: event.ctrlKey || event.metaKey,
    });
    if (!handled) return;
    event.preventDefault();
    this.applySelection(!event.shiftKey && !event.ctrlKey && !event.metaKey);
    const active = this.selection.active();
    if (active) this.store.setActive(active);
  }

  /** Mirror the selection into the filter. Exclusive (plain click / arrow —
   *  the step rhythm) replaces the WHOLE filter; modifier gestures keep chips
   *  that don't belong to the visible list (search chips, other tabs). */
  private applySelection(exclusive: boolean): void {
    const byId = new Map(
      this.picker()
        .shown()
        .map((it) => [it.id, it]),
    );
    const selected = this.selection
      .selectedKeys()
      .map((id) => byId.get(id))
      .filter((it): it is ResourceItem => !!it)
      .map((it) => this.entry(it));
    const kept = exclusive ? [] : this.filter.entries().filter((c) => !byId.has(c.id));
    this.filter.setAll([...kept, ...selected]);
  }

  /** PRD 123 D8 — "alle wählen" (a group or every search hit). */
  selectGroup(e: { items: ResourceItem[]; ctrl: boolean }): void {
    this.filter.selectGroup(
      e.items.map((it) => this.entry(it)),
      e.ctrl,
    );
    this.store.setActive(null);
  }

  /** PRD 122 D4 — creatable types from the server; empty hides the button. */
  private readonly creatable = signal<{ key: string; name: string }[]>([]);

  /** PRD 127 D7 — the type of the last clicked type folder or resource (Swing: the focused tree node). */
  protected readonly lastType = signal<string | null>(null);

  /** PRD 122 D3 / PRD 127 D7 — the last clicked type preselects (if creatable), else the first creatable one. */
  protected readonly newTypeKey = computed(() => {
    const types = this.creatable();
    return types.find((t) => t.key === this.lastType())?.key ?? types[0]?.key ?? null;
  });

  newResource(): void {
    const typeKey = this.newTypeKey();
    if (!typeKey) return;
    this.dialog
      .open(ResourceEditDialogComponent, {
        data: { create: { typeKey } } satisfies ResourceEditDialogData,
        maxWidth: '95vw',
        restoreFocus: false,
      })
      .afterClosed()
      .subscribe((result) => {
        if (result === 'saved') this.store.reload();
      });
  }

  /** The picker's ⋮ button — open the Bearbeiten/Anzeigen menu at that button. */
  openMenu(e: { item: ResourceItem; anchor: EventTarget | null }): void {
    const rect = e.anchor instanceof HTMLElement ? e.anchor.getBoundingClientRect() : null;
    this.menuX.set(rect?.left ?? 0);
    this.menuY.set(rect?.bottom ?? 0);
    const trigger = this.menuTrigger();
    trigger.menuData = { item: e.item }; // the input binding would only land on the next CD
    trigger.openMenu();
  }

  /** PRD 096 Phase 4 — Anzeigen/Bearbeiten on a resource item (⋮ menu). */
  openResource(it: ResourceItem, readOnly: boolean): void {
    this.dialog
      .open(ResourceEditDialogComponent, {
        data: { id: it.id, readOnly } satisfies ResourceEditDialogData,
        maxWidth: '95vw',
        restoreFocus: false,
      })
      .afterClosed()
      .subscribe((result) => {
        if (result === 'saved') this.store.reload();
      });
  }
}
