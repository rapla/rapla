import { Component, ElementRef, computed, effect, inject, signal } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';

import {
  ResourceEditDialogComponent,
  type ResourceEditDialogData,
} from '../resource/resource-edit-dialog.component';
import { ResourceSelectionStore, type ResourceItem } from '../state/resource-selection-store';
import { FIRST_PAGE, PAGE_SIZE, filterRows, page, usersMatching } from '../state/resource-picker';
import {
  buildTree,
  filterTree,
  membersOf,
  pathKeysTo,
  visibleRows,
  type TreeNode,
  type NodeRow,
  type TreeRow,
} from '../state/resource-tree';
import { FilterStore, type FilterEntry } from '../state/filter-store';
import { AuthService, type Identity } from '../auth/auth.service';
import { entityIcon } from './entity-icon';
import { TableSelection } from '../views/table-selection';

/**
 * The persistent left ResourceSelection. PRD 119 Phase 1: chips pick the source (Alle,
 * Favoriten, Zuletzt, a loaded Gruppe, one chip per type) over the lean resource list; a
 * local query narrows it in the browser (no server call per keystroke). A plain click {@link FilterStore.replace}s the filter with that
 * resource — the click-to-step rhythm — and marks it active ("▶ gezeigt").
 */
@Component({
  selector: 'app-resource-selection',
  imports: [MatIconModule, MatMenuModule],
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
      <div class="chips">
        @for (c of store.chips(); track c.key) {
          <button
            [class.on]="store.activeChip() === c.key"
            [attr.aria-pressed]="store.activeChip() === c.key"
            (click)="store.setActiveChip(c.key)"
          >
            {{ c.label }}
          </button>
        }
      </div>
      @if (store.activeChip() === 'recents' && store.recents().length) {
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
      @for (row of rows(); track row.node ? row.node.key : 'more:' + row.more) {
        @if (!row.node) {
          <button
            type="button"
            class="showmore"
            [style.padding-left.rem]="0.9 + row.depth"
            (click)="showMoreChildren(row.more)"
          >
            Weitere {{ block(row.hidden) }} anzeigen
          </button>
        } @else if (row.node.kind === 'group') {
          <div class="grouprow" [style.padding-left.rem]="0.5 + row.depth">
            <button
              type="button"
              class="toggle"
              [attr.aria-expanded]="expandedGroups().has(row.node.key)"
              [attr.aria-label]="row.node.label"
              (click)="toggleGroup(row.node.key)"
            >
              {{ expandedGroups().has(row.node.key) ? '▾' : '▸' }}
            </button>
            <span class="glabel">{{ row.node.label }}</span>
            <span class="count">{{ row.node.count }}</span>
            <button
              type="button"
              class="selectall"
              [attr.aria-label]="'Alle in ' + row.node.label + ' wählen'"
              (click)="selectGroup(row.node)"
            >
              alle wählen
            </button>
          </div>
        } @else if (row.node?.item; as it) {
          <div
            class="item"
            role="button"
            tabindex="0"
            [class.active]="store.activeId() === it.id"
            [class.selected]="filter.has(it.id)"
            [style.padding-left.rem]="0.9 + row.depth"
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
              [attr.aria-label]="
                store.isFavorite(it.id) ? 'Aus Favoriten entfernen' : 'Zu Favoriten'
              "
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
        }
      } @empty {
        <div class="more">— leer</div>
      }
      @if (hiddenCount() > 0) {
        <button type="button" class="showmore" (click)="extra.update((n) => n + 1)">
          Weitere {{ block(hiddenCount()) }} anzeigen
        </button>
      }
      <mat-menu #itemMenu="matMenu">
        <ng-template matMenuContent let-item="item">
          <button mat-menu-item (click)="openResource(item, false)">Bearbeiten</button>
          <button mat-menu-item (click)="openResource(item, true)">Anzeigen</button>
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
      .chips {
        display: flex;
        flex-wrap: wrap;
        gap: 0.35rem;
        padding: 0 0.75rem 0.5rem;
      }
      .chips button {
        border: 1px solid rgba(0, 0, 0, 0.15);
        background: #fff;
        border-radius: 999px;
        padding: 0.25rem 0.6rem;
        font-size: 0.72rem;
        cursor: pointer;
        color: rgba(0, 0, 0, 0.55);
      }
      .chips button.on {
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
      .grouprow {
        display: flex;
        align-items: center;
        gap: 0.35rem;
        padding: 0.35rem 0.9rem 0.35rem 0.5rem;
        font-size: 0.8rem;
        font-weight: 600;
      }
      .grouprow .glabel {
        flex: 1;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      .grouprow .count {
        font-size: 0.7rem;
        color: rgba(0, 0, 0, 0.45);
      }
      .grouprow button {
        border: none;
        background: transparent;
        cursor: pointer;
        font: inherit;
        padding: 0 0.2rem;
      }
      .grouprow .selectall {
        font-size: 0.68rem;
        font-weight: 400;
        color: var(--mat-sys-primary, #3f51b5);
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
      .showmore {
        border: none;
        background: transparent;
        text-align: left;
        padding: 0.5rem 0.9rem;
        font-size: 0.75rem;
        cursor: pointer;
        color: var(--mat-sys-primary, #3f51b5);
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

  /** PRD 119 D12 — extra blocks of 100 revealed by "Weitere n anzeigen" for the current chip + query. */
  protected readonly extra = signal(0);
  /** PRD 119 D12 — per tree level (node key, '' = top), how many children are revealed. */
  private readonly limits = signal<ReadonlyMap<string, number>>(new Map());

  /** PRD 119 D1/D10 — rows of the active chip narrowed by the query; under Alle matching users follow. */
  private readonly filtered = computed<ResourceItem[]>(() => {
    const q = this.store.query();
    const rows = filterRows(this.store.activeList(), q);
    return this.store.activeChip() === 'all'
      ? [...rows, ...usersMatching(this.store.users(), q)]
      : rows;
  });

  /** PRD 119 D2/D11 — a type chip shows its resources as a collapsible group tree. */
  private readonly treeMode = computed(() => this.store.activeChip().startsWith('type:'));

  /** Groups the user opened (true) or closed (false); others follow the default (D12). */
  private readonly toggled = signal<ReadonlyMap<string, boolean>>(new Map());

  private readonly tree = computed(() => {
    const q = this.store.query();
    if (this.treeMode()) return filterTree(buildTree(this.store.activeList()), q);
    // Under Alle, groups whose own name matches the query appear as expandable rows.
    const hits: TreeNode[] = [];
    if (this.store.activeChip() === 'all' && q.trim()) {
      const walk = (nodes: TreeNode[]) =>
        nodes.forEach((n) => {
          if (n.kind !== 'group') return;
          if (filterRows([{ id: n.key, label: n.label }], q).length) hits.push(n);
          else walk(n.children);
        });
      walk(buildTree(this.store.resources()));
    }
    return { nodes: hits, expanded: new Set<string>() };
  });

  /** Default open: groups the query opened and the path to a selected resource (D12); user toggles win. */
  protected readonly expandedGroups = computed(() => {
    const selected = new Set(this.filter.entries().map((e) => e.id));
    const open = new Set([...this.tree().expanded, ...pathKeysTo(this.tree().nodes, selected)]);
    for (const [key, isOpen] of this.toggled()) {
      if (isOpen) open.add(key);
      else open.delete(key);
    }
    return open;
  });

  private readonly paged = computed(() =>
    this.treeMode()
      ? { shown: [], hidden: 0 }
      : page(this.filtered(), this.firstBlock() + this.extra() * PAGE_SIZE),
  );
  protected readonly hiddenCount = computed(() => this.paged().hidden);

  /** D12 — Alle without a query starts with 20 rows, every other list with 100. */
  private readonly firstBlock = computed(() =>
    this.store.activeChip() === 'all' && !this.store.query().trim() ? FIRST_PAGE : PAGE_SIZE,
  );

  protected readonly rows = computed<TreeRow[]>(() => [
    ...visibleRows(this.tree().nodes, this.expandedGroups(), this.limits()),
    ...this.paged().shown.map(
      (item): NodeRow => ({
        node: {
          key: `#${item.id}`,
          label: item.label,
          kind: 'resource',
          item,
          children: [],
          count: 1,
        },
        depth: 0,
      }),
    ),
  ]);

  /** The distinct resources on screen — the rows the selection engine steps. */
  protected readonly shown = computed(() => {
    const byId = new Map<string, ResourceItem>();
    for (const row of this.rows()) if (row.node?.item) byId.set(row.node.item.id, row.node.item);
    return [...byId.values()];
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
    this.store.ensureLoaded();
    effect(() => this.selection.setRows(this.shown().map((it) => it.id)));
    effect(() => {
      this.store.activeChip();
      this.store.query();
      this.extra.set(0);
      this.limits.set(new Map());
      this.toggled.set(new Map());
    });
    effect(() => {
      const request = this.store.pickerFocus();
      if (request === this.focusRequest) return;
      this.focusRequest = request;
      this.host.nativeElement.querySelector<HTMLElement>('.stepper')?.focus();
    });
  }

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  /** The focus request already served — the effect's first run must not steal focus. */
  private focusRequest = this.store.pickerFocus();

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
    const plain = !mods.shiftKey && !mods.ctrlKey && !mods.metaKey;
    this.applySelection(plain);
    this.store.setActive(it.id);
    // PRD 119 D5 — a selection pushes a recent; not while stepping the Zuletzt list itself.
    if (plain && this.store.activeChip() !== 'recents') this.store.pushRecent(it);
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
    const byId = new Map(this.shown().map((it) => [it.id, it]));
    const selected = this.selection
      .selectedKeys()
      .map((id) => byId.get(id))
      .filter((it): it is ResourceItem => !!it)
      .map((it) => this.entry(it));
    const kept = exclusive ? [] : this.filter.entries().filter((c) => !byId.has(c.id));
    this.filter.setAll([...kept, ...selected]);
  }

  toggleGroup(key: string): void {
    const open = this.expandedGroups().has(key);
    this.toggled.update((map) => new Map(map).set(key, !open));
  }

  /** D12 — reveal the next block of one tree level's children. */
  showMoreChildren(key: string): void {
    this.limits.update((map) => new Map(map).set(key, (map.get(key) ?? PAGE_SIZE) + PAGE_SIZE));
  }

  protected block(hidden: number): number {
    return Math.min(hidden, PAGE_SIZE);
  }

  /** "alle wählen" — the group's members replace the filter. */
  selectGroup(node: TreeNode): void {
    this.filter.setAll(membersOf(node).map((it) => this.entry(it)));
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
