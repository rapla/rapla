import {
  Component,
  ElementRef,
  computed,
  effect,
  inject,
  input,
  model,
  output,
  signal,
} from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

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
import { entityIcon } from '../shell/entity-icon';
import type { AvailabilityRow } from '../event/availability-search.service';

/** A row gesture — the host decides what it means (PRD 123 D2). */
export interface PickEvent {
  item: ResourceItem;
  ctrl: boolean;
  shift: boolean;
}

/**
 * PRD 123 D1 — the one resource picker. Chips Alle / ★ Favoriten / Zuletzt plus a type select
 * (D3) over the lean list; a type shows its group tree (PRD 119 D2), everything else a flat
 * list with the D12 caps. Two hosts: the left rail (`mode="rail"`: ▶ gezeigt, ⋮ menu, the
 * host's selection engine) and the event sheet (`mode="assign"`: checkbox rows with
 * availability pills). The picker never decides what a click means — it emits {@link pick}.
 */
@Component({
  selector: 'app-resource-picker',
  imports: [MatIconModule],
  template: `
    <div class="chips">
      @for (c of baseChips; track c.key) {
        <button
          type="button"
          [class.on]="chip() === c.key"
          [attr.aria-pressed]="chip() === c.key"
          (click)="chip.set(c.key)"
        >
          {{ c.label }}
        </button>
      }
      <select
        class="typesel"
        aria-label="Typ"
        [class.on]="treeMode()"
        [value]="treeMode() ? chip() : ''"
        (change)="onTypeChange($event)"
      >
        <option value="">Typ ▾</option>
        @for (t of typeChips(); track t.key) {
          <option [value]="t.key">{{ t.label }} ({{ t.count }})</option>
        }
      </select>
    </div>
    @if (chip() === 'recents' && store.recents().length) {
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
          @if (mode() === 'rail') {
            <button
              type="button"
              class="selectall"
              [attr.aria-label]="'Alle in ' + row.node.label + ' wählen'"
              (click)="selectGroup.emit(membersOf(row.node))"
            >
              alle wählen
            </button>
          }
        </div>
      } @else if (row.node?.item; as it) {
        <div
          class="item"
          role="button"
          tabindex="0"
          [attr.aria-pressed]="mode() === 'assign' ? checked().has(it.id) : null"
          [class.active]="activeId() === it.id"
          [class.selected]="checked().has(it.id)"
          [style.padding-left.rem]="0.9 + row.depth"
          (mousedown)="onItemMousedown($event)"
          (click)="emitPick($event, it)"
          (keydown)="onRowKeydown($event, it)"
        >
          @if (mode() === 'assign') {
            <span class="chk" aria-hidden="true">{{ checked().has(it.id) ? '✓' : '' }}</span>
          }
          <mat-icon class="ico" [style.color]="it.color || null">{{ icon(it) }}</mat-icon>
          <span class="lbl">{{ it.label }}</span>
          @if (activeId() === it.id) {
            <span class="now">▶ gezeigt</span>
          }
          @if (mode() === 'assign' && availability().get(it.id); as st) {
            <span class="pill" [class]="'pill ' + st.status">{{ pillLabel(st) }}</span>
          }
          <button
            type="button"
            class="fav"
            [class.on]="store.isFavorite(it.id)"
            [attr.aria-label]="store.isFavorite(it.id) ? 'Aus Favoriten entfernen' : 'Zu Favoriten'"
            [title]="store.isFavorite(it.id) ? 'Aus Favoriten entfernen' : 'Zu Favoriten'"
            (click)="toggleFav($event, it)"
          >
            {{ store.isFavorite(it.id) ? '★' : '☆' }}
          </button>
          @if (mode() === 'rail' && (it.kind ?? 'resource') === 'resource') {
            <button
              type="button"
              class="act"
              aria-label="Aktionen"
              title="Aktionen"
              (click)="$event.stopPropagation(); menu.emit({ item: it, anchor: $event.target })"
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
  `,
  styles: [
    `
      :host {
        display: flex;
        flex-direction: column;
        min-width: 0;
      }
      .chips {
        display: flex;
        flex-wrap: wrap;
        gap: 0.35rem;
        align-items: center;
        padding: 0 0.75rem 0.5rem;
      }
      .chips button,
      .chips select {
        border: 1px solid rgba(0, 0, 0, 0.15);
        background: #fff;
        border-radius: 999px;
        padding: 0.25rem 0.6rem;
        font-size: 0.72rem;
        cursor: pointer;
        color: rgba(0, 0, 0, 0.55);
        max-width: 11rem;
      }
      .chips button.on,
      .chips select.on {
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
        background: rgba(0, 0, 0, 0.04);
        font-size: 0.68rem;
        font-weight: 600;
        color: rgba(0, 0, 0, 0.55);
      }
      .grouphdr .clr {
        cursor: pointer;
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
      .item .chk {
        flex: none;
        width: 0.95rem;
        height: 0.95rem;
        border: 1px solid rgba(0, 0, 0, 0.4);
        border-radius: 3px;
        font-size: 0.7rem;
        line-height: 0.95rem;
        text-align: center;
      }
      .item.selected .chk {
        background: var(--mat-sys-primary, #3f51b5);
        border-color: transparent;
        color: #fff;
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
      .item .pill {
        font-size: 0.68rem;
        padding: 0.05rem 0.45rem;
        border-radius: 999px;
        background: rgba(46, 125, 50, 0.12);
        color: #2e7d32;
      }
      .item .pill.PARTIAL,
      .item .pill.CONFLICT,
      .item .pill.FORBIDDEN {
        background: rgba(198, 40, 40, 0.1);
        color: #c62828;
      }
      .item .pill.REQUEST_ONLY {
        background: rgba(183, 129, 3, 0.12);
        color: #8a5a00;
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
export class ResourcePickerComponent {
  protected readonly store = inject(ResourceSelectionStore);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  protected readonly membersOf = membersOf;

  readonly mode = input<'rail' | 'assign'>('rail');
  /** The active chip: 'all' | 'favorites' | 'recents' | 'type:<key>' — owned by the host. */
  readonly chip = model('all');
  readonly query = model('');
  /** Rows shown as selected (rail: the filter; assign: the assigned resources). */
  readonly checked = input<ReadonlySet<string>>(new Set<string>());
  /** Rail: the "▶ gezeigt" row. */
  readonly activeId = input<string | null>(null);
  /** Assign: availability per resource id for the pill. */
  readonly availability = input<ReadonlyMap<string, AvailabilityRow>>(new Map());

  readonly pick = output<PickEvent>();
  readonly selectGroup = output<ResourceItem[]>();
  readonly menu = output<{ item: ResourceItem; anchor: EventTarget | null }>();

  protected readonly baseChips = [
    { key: 'all', label: 'Alle' },
    { key: 'favorites', label: '★ Favoriten' },
    { key: 'recents', label: 'Zuletzt' },
  ];

  /** PRD 123 D3 — the types fold into one select, A–Z with their counts. */
  protected readonly typeChips = computed(() => {
    const counts = new Map<string, number>();
    for (const it of this.store.resources()) {
      if (it.typeKey) counts.set(it.typeKey, (counts.get(it.typeKey) ?? 0) + 1);
    }
    return this.store
      .chips()
      .filter((c) => c.key.startsWith('type:'))
      .map((c) => ({ ...c, count: counts.get(c.key.slice('type:'.length)) ?? 0 }));
  });

  protected readonly treeMode = computed(() => this.chip().startsWith('type:'));

  /** PRD 119 D12 — extra blocks of 100 revealed by "Weitere n anzeigen" for the current chip + query. */
  protected readonly extra = signal(0);
  private readonly limits = signal<ReadonlyMap<string, number>>(new Map());
  private readonly toggled = signal<ReadonlyMap<string, boolean>>(new Map());

  private readonly list = computed(() => this.store.listFor(this.chip()));

  private readonly filtered = computed<ResourceItem[]>(() => {
    const q = this.query();
    const rows = filterRows(this.list(), q);
    return this.chip() === 'all' && this.mode() === 'rail'
      ? [...rows, ...usersMatching(this.store.users(), q)]
      : rows;
  });

  private readonly tree = computed(() => {
    const q = this.query();
    if (this.treeMode()) return filterTree(buildTree(this.list()), q);
    const hits: TreeNode[] = [];
    if (this.chip() === 'all' && q.trim()) {
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

  protected readonly expandedGroups = computed(() => {
    const open = new Set([
      ...this.tree().expanded,
      ...pathKeysTo(this.tree().nodes, this.checked()),
    ]);
    for (const [key, isOpen] of this.toggled()) {
      if (isOpen) open.add(key);
      else open.delete(key);
    }
    return open;
  });

  private readonly firstBlock = computed(() =>
    this.chip() === 'all' && !this.query().trim() ? FIRST_PAGE : PAGE_SIZE,
  );

  private readonly paged = computed(() =>
    this.treeMode()
      ? { shown: [], hidden: 0 }
      : page(this.filtered(), this.firstBlock() + this.extra() * PAGE_SIZE),
  );
  protected readonly hiddenCount = computed(() => this.paged().hidden);

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

  /** The distinct resources on screen — the host's selection engine and availability fetch use it. */
  readonly shown = computed(() => {
    const byId = new Map<string, ResourceItem>();
    for (const row of this.rows()) if (row.node?.item) byId.set(row.node.item.id, row.node.item);
    return [...byId.values()];
  });

  constructor() {
    this.store.ensureLoaded();
    effect(() => {
      this.chip();
      this.query();
      this.extra.set(0);
      this.limits.set(new Map());
      this.toggled.set(new Map());
    });
  }

  protected icon(it: ResourceItem): string {
    return entityIcon(it.kind ?? 'resource', it.typeKey);
  }

  protected pillLabel(st: AvailabilityRow): string {
    return st.status === 'AVAILABLE' ? 'frei' : st.status === 'REQUEST_ONLY' ? 'Anfrage' : 'belegt';
  }

  protected onTypeChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.chip.set(value || 'all');
  }

  /** Shift-click must range-select, not select text. */
  protected onItemMousedown(event: MouseEvent): void {
    if (event.shiftKey) event.preventDefault();
  }

  protected emitPick(event: MouseEvent | KeyboardEvent, item: ResourceItem): void {
    this.pick.emit({ item, ctrl: event.ctrlKey || event.metaKey, shift: event.shiftKey });
  }

  /** Assign mode steps rows itself; the rail's selection engine owns the keys there. */
  protected onRowKeydown(event: KeyboardEvent, item: ResourceItem): void {
    if (event.key === 'Enter') {
      event.preventDefault();
      this.emitPick(event, item);
      return;
    }
    if (this.mode() !== 'assign') return;
    const dir = event.key === 'ArrowDown' ? 1 : event.key === 'ArrowUp' ? -1 : 0;
    if (!dir) return;
    // Group rows and "Weitere…" sit between items — step to the next ITEM, not the next sibling.
    const items = Array.from(this.host.nativeElement.querySelectorAll<HTMLElement>('.item'));
    const next = items[items.indexOf(event.target as HTMLElement) + dir];
    if (next) {
      event.preventDefault();
      next.focus();
    }
  }

  protected toggleGroup(key: string): void {
    const open = this.expandedGroups().has(key);
    this.toggled.update((map) => new Map(map).set(key, !open));
  }

  protected showMoreChildren(key: string): void {
    this.limits.update((map) => new Map(map).set(key, (map.get(key) ?? PAGE_SIZE) + PAGE_SIZE));
  }

  protected block(hidden: number): number {
    return Math.min(hidden, PAGE_SIZE);
  }

  protected toggleFav(event: Event, it: ResourceItem): void {
    event.stopPropagation();
    this.store.toggleFavorite(it);
  }
}
