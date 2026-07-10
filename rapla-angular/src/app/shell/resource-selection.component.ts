import { Component, computed, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';

import {
  AllocatableEditDialogComponent,
  type AllocatableEditDialogData,
} from '../allocatable/allocatable-edit-dialog.component';
import {
  ResourceSelectionStore,
  type ResourceSelectionTab,
  type ResourceItem,
} from '../state/resource-selection-store';
import { FilterStore, type FilterEntry } from '../state/filter-store';
import { AuthService, type Identity } from '../auth/auth.service';
import { entityIcon } from './entity-icon';
import { TableSelection } from '../views/table-selection';

/**
 * The persistent left ResourceSelection. Tabs pick the source (Zuletzt/
 * Favoriten/Gruppe); a local query narrows long lists (e.g. hundreds of
 * lecturers). A plain click {@link FilterStore.replace}s the filter with that
 * resource — the click-to-step rhythm — and marks it active ("▶ gezeigt").
 */
@Component({
  selector: 'app-resource-selection',
  imports: [FormsModule, MatIconModule, MatMenuModule],
  template: `
    <div class="stepper" tabindex="0" (keydown)="onListKeydown($event)">
      @if (me(); as user) {
        <div
          class="pinned"
          role="button"
          tabindex="0"
          [class.active]="meActive()"
          (click)="scopeToMe(user)"
          (keydown.enter)="scopeToMe(user)"
          title="Auf meine eigenen Veranstaltungen filtern"
        >
          <span class="dot person">👤</span>
          <span class="lbl">{{ user.name || user.username }}</span>
          <span class="meta">meine</span>
        </div>
      }
      <div class="sthead">DURCHSTEPPEN</div>
      <div class="tabs">
        @for (t of tabs; track t.key) {
          <button [class.on]="store.activeTab() === t.key" (click)="store.setActiveTab(t.key)">
            {{ t.label }}
          </button>
        }
      </div>
      @if (store.activeTab() === 'group' && store.groupLabel()) {
        <div class="grouphdr">
          <span>{{ store.groupLabel() }}</span>
          <span
            class="clr"
            role="button"
            tabindex="0"
            (click)="store.clearGroup()"
            (keydown.enter)="store.clearGroup()"
            >× leeren</span
          >
        </div>
      }
      @if (store.activeTab() === 'recents' && store.recents().length) {
        <div class="grouphdr recents-hdr">
          <span>Zuletzt verwendet</span>
          <span
            class="clr"
            role="button"
            tabindex="0"
            (click)="store.clearRecents()"
            (keydown.enter)="store.clearRecents()"
            >× leeren</span
          >
        </div>
      }
      <input
        class="stsearch"
        placeholder="in der Liste filtern…"
        [ngModel]="query()"
        (ngModelChange)="query.set($event)"
      />
      @for (it of visible(); track it.id; let i = $index) {
        <div
          class="item"
          role="button"
          tabindex="0"
          [class.active]="store.activeId() === it.id"
          [class.selected]="filter.has(it.id)"
          (mousedown)="onItemMousedown($event)"
          (click)="step($event, it)"
          (keydown.enter)="step($event, it)"
        >
          <mat-icon class="ico" [style.color]="it.color || null">{{ icon(it) }}</mat-icon>
          <span class="lbl">{{ it.label }}</span>
          @if (store.activeId() === it.id) {
            <span class="now">▶ gezeigt</span>
          }
          <button
            class="fav"
            [class.on]="store.isFavorite(it.id)"
            [attr.aria-label]="store.isFavorite(it.id) ? 'Aus Favoriten entfernen' : 'Zu Favoriten'"
            [title]="store.isFavorite(it.id) ? 'Aus Favoriten entfernen' : 'Zu Favoriten'"
            (click)="toggleFav($event, it)"
          >
            {{ store.isFavorite(it.id) ? '★' : '☆' }}
          </button>
          @if ((it.kind ?? 'resource') === 'resource') {
            <button
              class="act"
              aria-label="Aktionen"
              title="Aktionen"
              [matMenuTriggerFor]="itemMenu"
              [matMenuTriggerData]="{ item: it }"
              (click)="$event.stopPropagation()"
            >
              ⋮
            </button>
          }
        </div>
      } @empty {
        <div class="more">— leer</div>
      }
      <mat-menu #itemMenu="matMenu">
        <ng-template matMenuContent let-item="item">
          <button mat-menu-item (click)="openAllocatable(item, false)">Bearbeiten</button>
          <button mat-menu-item (click)="openAllocatable(item, true)">Anzeigen</button>
        </ng-template>
      </mat-menu>
    </div>
  `,
  styles: [
    `
      .stepper {
        width: 250px;
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
        padding: 0.75rem 0.9rem 0.5rem;
        font-size: 0.7rem;
        font-weight: 700;
        letter-spacing: 0.04em;
        color: rgba(0, 0, 0, 0.55);
      }
      .tabs {
        display: flex;
        gap: 0.35rem;
        padding: 0 0.75rem 0.5rem;
      }
      .tabs button {
        flex: 1;
        border: 1px solid rgba(0, 0, 0, 0.15);
        background: #fff;
        border-radius: 6px;
        padding: 0.35rem 0.25rem;
        font-size: 0.72rem;
        cursor: pointer;
        color: rgba(0, 0, 0, 0.55);
      }
      .tabs button.on {
        background: var(--mat-sys-primary, #3f51b5);
        color: #fff;
        border-color: transparent;
        font-weight: 600;
      }
      .grouphdr {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 0.35rem 0.9rem;
        background: rgba(106, 27, 154, 0.08);
        font-size: 0.68rem;
        font-weight: 700;
        color: #6a1b9a;
      }
      .grouphdr .clr {
        cursor: pointer;
      }
      .grouphdr.recents-hdr {
        background: rgba(0, 0, 0, 0.04);
        color: rgba(0, 0, 0, 0.55);
        font-weight: 600;
      }
      .stsearch {
        margin: 0 0.75rem 0.5rem;
        height: 1.9rem;
        border: 1px solid rgba(0, 0, 0, 0.15);
        border-radius: 6px;
        padding: 0 0.6rem;
        font-size: 0.75rem;
      }
      .item {
        display: flex;
        align-items: center;
        gap: 0.55rem;
        padding: 0.5rem 0.9rem;
        font-size: 0.84rem;
        cursor: pointer;
        border-left: 3px solid transparent;
      }
      .item:hover {
        background: rgba(63, 81, 181, 0.06);
      }
      .item.selected {
        background: var(--mat-sys-secondary-container, rgba(63, 81, 181, 0.12));
      }
      .item.active {
        background: rgba(46, 125, 50, 0.1);
        border-left-color: #2e7d32;
        font-weight: 600;
      }
      .item .ico {
        flex: none;
        font-size: 1.15rem;
        width: 1.15rem;
        height: 1.15rem;
        color: rgba(0, 0, 0, 0.5);
      }
      .item .lbl {
        flex: 1;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      .item .now {
        font-size: 0.62rem;
        font-weight: 700;
        color: #2e7d32;
      }
      .item .fav {
        border: none;
        background: transparent;
        cursor: pointer;
        font-size: 0.95rem;
        line-height: 1;
        padding: 0 0.1rem;
        color: rgba(0, 0, 0, 0.3);
      }
      .item .fav.on {
        color: #f5a623;
      }
      .item:hover .fav {
        color: rgba(0, 0, 0, 0.45);
      }
      .item:hover .fav.on {
        color: #f5a623;
      }
      .item .act {
        border: none;
        background: transparent;
        cursor: pointer;
        font-size: 0.95rem;
        line-height: 1;
        padding: 0 0.1rem;
        color: transparent;
      }
      .item:hover .act {
        color: rgba(0, 0, 0, 0.45);
      }
      .more {
        padding: 0.5rem 0.9rem;
        font-size: 0.75rem;
        color: rgba(0, 0, 0, 0.45);
      }
    `,
  ],
})
export class ResourceSelectionComponent {
  protected readonly store = inject(ResourceSelectionStore);
  protected readonly filter = inject(FilterStore);
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(MatDialog);

  /** The logged-in user, pinned at the top for a one-click "my events" scope. */
  protected readonly me = this.auth.identity;
  /** True when a `user` scope chip for the logged-in user is active. */
  protected readonly meActive = computed(() => {
    const id = this.auth.identity()?.userId;
    return !!id && this.filter.entries().some((e) => e.kind === 'user' && e.id === id);
  });

  protected readonly query = signal('');

  protected readonly tabs: { key: ResourceSelectionTab; label: string }[] = [
    { key: 'recents', label: 'Zuletzt' },
    { key: 'favorites', label: 'Favoriten' },
    { key: 'group', label: 'Gruppe' },
  ];

  protected readonly visible = computed<ResourceItem[]>(() => {
    const q = this.query().trim().toLowerCase();
    const list = this.store.activeList();
    return q ? list.filter((x) => x.label.toLowerCase().includes(q)) : list;
  });

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
    effect(() => this.selection.setRows(this.visible().map((it) => it.id)));
  }

  /** Material icon for a list item (by kind + rapla type key). */
  protected icon(it: ResourceItem): string {
    return entityIcon(it.kind ?? 'resource', it.typeKey);
  }

  private entry(r: ResourceItem): FilterEntry {
    return { id: r.id, kind: r.kind ?? 'resource', label: r.label, color: r.color };
  }

  /** Shift-click must range-select, not select text. */
  onItemMousedown(event: MouseEvent): void {
    if (event.shiftKey) event.preventDefault();
  }

  step(event: Event, it: ResourceItem): void {
    // click gives a MouseEvent, (keydown.enter) a KeyboardEvent — both carry the modifier flags.
    const mods = event as MouseEvent | KeyboardEvent;
    this.selection.syncSelected(this.filter.entries().map((e) => e.id));
    this.selection.pointer(it.id, {
      shift: mods.shiftKey,
      ctrl: mods.ctrlKey || mods.metaKey,
    });
    this.applySelection(!mods.shiftKey && !mods.ctrlKey && !mods.metaKey);
    this.store.setActive(it.id);
  }

  onListKeydown(event: KeyboardEvent): void {
    // The search input and the tab/★/⋮ buttons own their keys.
    if (event.target instanceof HTMLInputElement || event.target instanceof HTMLButtonElement)
      return;
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
    const byId = new Map(this.visible().map((it) => [it.id, it]));
    const selected = this.selection
      .selectedKeys()
      .map((id) => byId.get(id))
      .filter((it): it is ResourceItem => !!it)
      .map((it) => this.entry(it));
    const kept = exclusive ? [] : this.filter.entries().filter((c) => !byId.has(c.id));
    this.filter.setAll([...kept, ...selected]);
  }

  /** ★ pins/unpins to Favoriten without stepping the row. */
  toggleFav(event: Event, it: ResourceItem): void {
    event.stopPropagation();
    this.store.toggleFavorite(it);
  }

  /** Step to the logged-in user's own events — a `user` scope chip (ownerEq:<me>). */
  scopeToMe(user: Identity): void {
    this.filter.replace({ id: user.userId, kind: 'user', label: user.name || user.username });
  }

  /** PRD 096 Phase 4 — Anzeigen/Bearbeiten on a resource item (⋮ menu). */
  openAllocatable(it: ResourceItem, readOnly: boolean): void {
    this.dialog.open(AllocatableEditDialogComponent, {
      data: { id: it.id, readOnly } satisfies AllocatableEditDialogData,
      maxWidth: '95vw',
      restoreFocus: false,
    });
  }
}
