import {
  Component,
  ElementRef,
  computed,
  effect,
  inject,
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
import { AuthService, type Identity } from '../auth/auth.service';
import { TableSelection } from '../views/table-selection';

/**
 * The persistent left ResourceSelection: the pinned "meine" card, the DURCHSTEPPEN header with
 * "+ Neu" (PRD 122) and the shared {@link ResourcePickerComponent} in rail mode (PRD 123 D1).
 * A plain click {@link FilterStore.replace}s the filter with that resource — the
 * click-to-step rhythm — and marks it active ("▶ gezeigt"); Strg/Shift follow the selection
 * engine (PRD 099).
 */
@Component({
  selector: 'app-resource-selection',
  imports: [MatMenuModule, ResourcePickerComponent],
  template: `
    <div class="stepper" tabindex="0" (keydown)="onListKeydown($event)">
      @if (me(); as user) {
        <div
          class="pinned"
          role="button"
          tabindex="0"
          [class.active]="meActive()"
          (click)="scopeToMe($event, user)"
          (keydown.enter)="scopeToMe($event, user)"
          title="Auf meine eigenen Veranstaltungen filtern (Strg: zur Auswahl hinzufügen, erneuter Klick: aufheben)"
        >
          <span class="dot person">👤</span>
          <span class="lbl">{{ user.name || user.username }}</span>
          <span class="meta">meine</span>
        </div>
      }
      <div class="sthead">
        DURCHSTEPPEN
        @if (newTypeKey()) {
          <button
            type="button"
            class="newbtn"
            (click)="newResource()"
            title="Neue Ressource anlegen"
          >
            + Neu
          </button>
        }
      </div>
      <app-resource-picker
        mode="rail"
        [chip]="store.activeChip()"
        (chipChange)="store.setActiveChip($event)"
        [query]="store.query()"
        (queryChange)="store.setQuery($event)"
        [checked]="selectedIds()"
        [activeId]="store.activeId()"
        (pick)="step($event)"
        (selectGroup)="selectGroup($event)"
        (menu)="openMenu($event)"
      />
      <mat-menu #itemMenu="matMenu">
        <ng-template matMenuContent let-item="item">
          <button mat-menu-item (click)="openResource(item, false)">Bearbeiten</button>
          <button mat-menu-item (click)="openResource(item, true)">Anzeigen</button>
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
        width: 290px;
        display: flex;
        flex-direction: column;
        overflow: auto;
        height: 100%;
      }
      .stepper:focus {
        outline: none;
      }
      .pinned {
        display: flex;
        align-items: center;
        gap: 0.55rem;
        margin: 0.6rem 0.6rem 0.2rem;
        padding: 0.5rem 0.6rem;
        border: 1px solid rgba(63, 81, 181, 0.25);
        border-radius: 6px;
        background: rgba(63, 81, 181, 0.06);
        cursor: pointer;
        font-size: 0.84rem;
      }
      .pinned:hover {
        background: rgba(63, 81, 181, 0.12);
      }
      .pinned.active {
        background: rgba(46, 125, 50, 0.12);
        border-color: #2e7d32;
        font-weight: 600;
      }
      .pinned .dot.person {
        font-size: 0.9rem;
        line-height: 1;
      }
      .pinned .lbl {
        flex: 1;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      .pinned .meta {
        font-size: 0.62rem;
        font-weight: 700;
        color: var(--mat-sys-primary, #3f51b5);
        text-transform: uppercase;
      }
      .sthead {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 0.75rem 0.9rem 0.5rem;
        font-size: 0.7rem;
        font-weight: 700;
        letter-spacing: 0.04em;
        color: rgba(0, 0, 0, 0.55);
      }
      .newbtn {
        font: inherit;
        padding: 1px 8px;
        border: 1px solid rgba(0, 0, 0, 0.25);
        border-radius: 999px;
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
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(MatDialog);
  private readonly resourceData = inject(ResourceDataService);
  private readonly picker = viewChild.required(ResourcePickerComponent);
  private readonly menuTrigger = viewChild.required<MatMenuTrigger>('menuTrigger');

  /** The logged-in user, pinned at the top for a one-click "my events" scope. */
  protected readonly me = this.auth.identity;
  /** True when a `user` scope chip for the logged-in user is active. */
  protected readonly meActive = computed(() => {
    const id = this.auth.identity()?.userId;
    return !!id && this.filter.entries().some((e) => e.kind === 'user' && e.id === id);
  });

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
      this.host.nativeElement.querySelector<HTMLElement>('.stepper')?.focus();
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

  private entry(r: ResourceItem): FilterEntry {
    return { id: r.id, kind: r.kind ?? 'resource', label: r.label, color: r.color };
  }

  step(e: PickEvent): void {
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

  /** "alle wählen" — the group's members replace the filter. */
  selectGroup(members: ResourceItem[]): void {
    this.filter.setAll(members.map((it) => this.entry(it)));
  }

  /**
   * Step to the logged-in user's own events — a `user` scope chip (ownerEq:<me>). Same gesture
   * rhythm as a list row (Swing parity, user ruling 2026-09-30): a plain click replaces the
   * selection, ctrl/cmd-click toggles the chip next to the selected resources.
   */
  scopeToMe(event: Event, user: Identity): void {
    const mods = event as MouseEvent | KeyboardEvent;
    const entry = { id: user.userId, kind: 'user' as const, label: user.name || user.username };
    if (this.filter.has(entry.id)) {
      this.filter.remove(entry.id); // a second click (plain or ctrl) clears the scope
    } else if (mods.ctrlKey || mods.metaKey) {
      this.filter.add(entry);
    } else {
      this.filter.replace(entry);
      this.store.setActive(null); // the stepped row is no longer shown (Swing: single selection)
    }
  }

  /** PRD 122 D4 — creatable types from the server; empty hides the button. */
  private readonly creatable = signal<{ key: string; name: string }[]>([]);

  /** PRD 122 D3 — an active type chip preselects its type (if creatable), else the first creatable one. */
  protected readonly newTypeKey = computed(() => {
    const types = this.creatable();
    const active = this.store.activeChip();
    const chipKey = active.startsWith('type:') ? active.slice('type:'.length) : null;
    return types.find((t) => t.key === chipKey)?.key ?? types[0]?.key ?? null;
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
