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
import { PAGE_SIZE, openSection, type Section } from '../state/resource-picker';
import {
  childIndex,
  membersOf,
  resourceNodes,
  searchGroups,
  typeFolders,
  visibleRows,
  type TreeNode,
  type TreeRow,
} from '../state/resource-tree';
import { entityIcon } from '../shell/entity-icon';
import type { AvailabilityRow } from '../event/availability-search.service';
import { TPipe, t as tr } from '../i18n/i18n.service';

/** A row gesture — the host decides what it means (PRD 123 D2). */
export interface PickEvent {
  item: ResourceItem;
  ctrl: boolean;
  shift: boolean;
}

const SECTION_ICONS: Record<string, string> = {
  favorites: 'star',
  recents: 'history',
  resources: 'inventory_2',
  persons: 'group',
  users: 'manage_accounts',
};

/**
 * PRD 123 D1 / PRD 127 D1 — the one resource picker: an accordion of Favoriten / Zuletzt / Ressourcen / Personen /
 * Benutzer, exactly one open; Ressourcen and Personen hold one folder per type (D5) with its group tree (PRD 119 D2,
 * PRD 120); a search lists the hits flat under one heading per type (D2). Two hosts: the left rail (`mode="rail"`: ▶ gezeigt, ⋮ menu, the
 * host's selection engine) and the event sheet (`mode="assign"`: checkbox rows with
 * availability pills). The picker never decides what a click means — it emits {@link pick}.
 */
@Component({
  selector: 'app-resource-picker',
  imports: [TPipe, MatIconModule],
  template: `
    @if (query().trim()) {
      <div class="searchnote">
        <span>{{ 'resource_search_active' | t: query().trim() : allHits().length }}</span>
        @if (mode() === 'rail' && allHits().length) {
          <button type="button" class="listall" (click)="emitGroup($event, allHits())">
            {{ (allChecked() ? 'resource_deselect_all' : 'resource_select_all') | t }}
          </button>
        }
        <button
          type="button"
          class="clearsearch"
          [attr.aria-label]="'resource_search_clear' | t"
          [title]="'resource_search_clear' | t"
          (click)="query.set('')"
        >
          ×
        </button>
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
          {{ 'resource_show_more' | t: block(row.hidden) }}
        </button>
      } @else if (row.node.kind === 'section') {
        <div
          class="sechead"
          [class.open]="section() === row.node.key"
          [class.below]="stickyBelow().has(row.node.key)"
          [style.bottom]="bottomOf(row.node.key)"
        >
          <button
            type="button"
            class="secbtn"
            [attr.aria-expanded]="section() === row.node.key"
            (click)="chip.set(section() === row.node.key ? 'none' : row.node.key)"
          >
            <mat-icon class="ico">{{ sectionIcon(row.node.key) }}</mat-icon>
            <span class="glabel">{{ row.node.label }}</span>
            <span class="count">{{ row.node.count }}</span>
          </button>
          @if (row.node.key === 'recents' && section() === 'recents') {
            <button type="button" class="clr" (click)="store.clearRecents()">
              × {{ 'resource_clear' | t }}
            </button>
          }
        </div>
      } @else if (row.node.kind === 'heading') {
        <button
          type="button"
          class="grouphdr typehdr"
          [attr.aria-expanded]="expandedGroups().has(row.node.key)"
          (click)="toggleGroup(row.node)"
        >
          {{ expandedGroups().has(row.node.key) ? '▾' : '▸' }}
          <span class="glabel">{{ row.node.label }}</span>
          <span class="count">{{ row.node.count }}</span>
        </button>
      } @else if (row.node.kind === 'group' || row.node.kind === 'type') {
        <div class="grouprow" [style.padding-left.rem]="0.5 + row.depth" [title]="row.node.label">
          <button
            type="button"
            class="toggle"
            [attr.aria-expanded]="expandedGroups().has(row.node.key)"
            [attr.aria-label]="row.node.label"
            (click)="toggleGroup(row.node)"
          >
            {{ expandedGroups().has(row.node.key) ? '▾' : '▸' }}
          </button>
          @if (row.node.kind === 'type') {
            <mat-icon class="ico">{{
              expandedGroups().has(row.node.key) ? 'folder_open' : 'folder'
            }}</mat-icon>
          }
          <span class="glabel">{{ row.node.label }}</span>
          <span class="count">{{ row.node.count }}</span>
          @if (mode() === 'rail') {
            <button
              type="button"
              class="selectall"
              [attr.aria-label]="'resource_select_all_in' | t: row.node.label"
              (click)="emitGroup($event, membersOf(row.node))"
            >
              {{
                (groupChecked().get(row.node.key) ? 'resource_deselect_all' : 'resource_select_all')
                  | t
              }}
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
          [title]="it.label"
          (mousedown)="onItemMousedown($event)"
          (click)="emitPick($event, it)"
          (keydown)="onRowKeydown($event, it)"
        >
          @if (mode() === 'assign') {
            <span class="chk" aria-hidden="true">{{ checked().has(it.id) ? '✓' : '' }}</span>
          }
          @if (row.node.children.length) {
            <button
              type="button"
              class="toggle"
              [attr.aria-expanded]="expandedGroups().has(row.node.key)"
              [attr.aria-label]="it.label"
              (click)="$event.stopPropagation(); toggleGroup(row.node)"
              (keydown)="$event.stopPropagation()"
            >
              {{ expandedGroups().has(row.node.key) ? '▾' : '▸' }}
            </button>
          } @else if (nested()) {
            <span class="tspace" aria-hidden="true"></span>
          }
          <mat-icon class="ico" [style.color]="it.color || null">{{ icon(it) }}</mat-icon>
          <span class="lbl"
            >{{ it.label }}
            @if (searching() && row.depth === 1 && parentsOf(it); as parents) {
              <span class="path">{{ parents }}</span>
            }
          </span>
          @if (activeId() === it.id) {
            <span class="now">▶ {{ 'resource_shown' | t }}</span>
          }
          @if (mode() === 'assign' && availability().get(it.id); as st) {
            <span class="pill" [class]="'pill ' + st.status">{{ pillLabel(st) }}</span>
          }
          <button
            type="button"
            class="fav"
            [class.on]="store.isFavorite(it.id)"
            [attr.aria-label]="
              (store.isFavorite(it.id) ? 'resource_unfavorite' : 'resource_favorite') | t
            "
            [title]="(store.isFavorite(it.id) ? 'resource_unfavorite' : 'resource_favorite') | t"
            (click)="toggleFav($event, it)"
          >
            {{ store.isFavorite(it.id) ? '★' : '☆' }}
          </button>
          @if (mode() === 'rail' && (it.kind ?? 'resource') === 'resource') {
            <button
              type="button"
              class="act"
              [attr.aria-label]="'resource_actions' | t"
              [title]="'resource_actions' | t"
              (click)="$event.stopPropagation(); menu.emit({ item: it, anchor: $event.target })"
            >
              ⋮
            </button>
          }
        </div>
      }
    } @empty {
      <div class="more">{{ 'resource_empty' | t }}</div>
    }
    @if (!searching() && users()?.mine) {
      <div
        class="sechead mine"
        [class.below]="stickyBelow().has('mine')"
        [style.bottom]="bottomOf('mine')"
      >
        <button
          type="button"
          class="secbtn"
          [attr.aria-pressed]="mineChecked()"
          [title]="'shell_scope_to_me_tooltip' | t"
          (click)="activateMine($event)"
        >
          <mat-icon class="ico">person</mat-icon>
          <span class="glabel">{{ 'resource_my_bookings' | t }}</span>
        </button>
      </div>
    }
  `,
  styles: [
    `
      :host {
        --sechead-h: 2.25rem;
        display: flex;
        flex-direction: column;
        min-width: 0;
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
      .sechead .clr {
        border: none;
        background: transparent;
        cursor: pointer;
        font: inherit;
        color: var(--mat-sys-primary, #3f51b5);
        padding: 0;
      }
      .typehdr {
        justify-content: flex-start;
        gap: 0.35rem;
        width: 100%;
        border: none;
        background: transparent;
        cursor: pointer;
        font-family: inherit;
        text-align: left;
        padding-left: 0.9rem;
      }
      .typehdr .glabel {
        flex: 1;
      }
      .sechead {
        box-sizing: border-box;
        height: var(--sechead-h);
        display: flex;
        align-items: center;
        gap: 0.35rem;
        padding: 0 0.9rem 0 0.25rem;
        border-top: 1px solid rgba(0, 0, 0, 0.06);
      }
      .sechead.open {
        position: sticky;
        top: 0;
        z-index: 1;
        background: var(--mat-sys-secondary-container, #e8eaf6);
      }
      .sechead.below {
        position: sticky;
        z-index: 1;
        background: var(--mat-sys-surface, #fff);
      }
      .sechead .secbtn {
        flex: 1;
        display: flex;
        align-items: center;
        gap: 0.45rem;
        min-width: 0;
        border: none;
        background: transparent;
        cursor: pointer;
        font: inherit;
        font-size: 0.84rem;
        font-weight: 600;
        padding: 0.5rem 0.25rem;
        text-align: left;
      }
      .sechead.mine .secbtn[aria-pressed='true'] {
        color: var(--mat-sys-primary, #3f51b5);
      }
      .sechead .glabel,
      .grouprow .glabel {
        flex: 1;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      .sechead .count {
        font-size: 0.7rem;
        font-weight: 400;
        color: rgba(0, 0, 0, 0.45);
      }
      .sechead .ico,
      .grouprow .ico {
        flex: none;
        font-size: 1.15rem;
        width: 1.15rem;
        height: 1.15rem;
        color: rgba(0, 0, 0, 0.5);
      }
      .item .path {
        display: block;
        font-size: 0.68rem;
        color: rgba(0, 0, 0, 0.45);
        overflow: hidden;
        text-overflow: ellipsis;
      }
      .searchnote {
        display: flex;
        align-items: center;
        gap: 0.35rem;
        padding: 0.25rem 0.9rem;
        font-size: 0.75rem;
        background: var(--mat-sys-secondary-container, #fff3cd);
      }
      .searchnote span {
        flex: 1;
      }
      .searchnote button {
        border: none;
        background: transparent;
        cursor: pointer;
        font: inherit;
        font-size: 1rem;
        padding: 0 0.2rem;
      }
      .grouprow {
        display: flex;
        align-items: center;
        gap: 0.35rem;
        padding: 0.35rem 0.9rem 0.35rem 0.5rem;
        font-size: 0.8rem;
        font-weight: 600;
      }
      .grouprow .count {
        font-size: 0.7rem;
        color: rgba(0, 0, 0, 0.45);
      }
      .item .toggle,
      .item .tspace {
        flex: none;
        width: 1rem;
      }
      .grouprow button,
      .item .toggle {
        border: none;
        background: transparent;
        cursor: pointer;
        font: inherit;
        padding: 0 0.2rem;
      }
      .searchnote .listall {
        flex: none;
        font-size: 0.75rem;
        color: var(--mat-sys-primary, #3f51b5);
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
  /** PRD 127 D1 — the open section as chosen ('' = start section); resolved by {@link section}. Owned by the host. */
  readonly chip = model('');
  readonly query = model('');
  /** Rows shown as selected (rail: the filter; assign: the assigned resources). */
  readonly checked = input<ReadonlySet<string>>(new Set<string>());
  /** Rail: the "▶ gezeigt" row. */
  readonly activeId = input<string | null>(null);
  /** Assign: availability per resource id for the pill. */
  readonly availability = input<ReadonlyMap<string, AvailabilityRow>>(new Map());

  readonly pick = output<PickEvent>();
  /** A group's members or every hit of the list (PRD 123 D8) — the host replaces, adds (Ctrl) or clears. */
  readonly selectGroup = output<{ items: ResourceItem[]; ctrl: boolean }>();
  readonly menu = output<{ item: ResourceItem; anchor: EventTarget | null }>();
  /** PRD 127 D7 — the type of the last clicked type folder or resource (Swing: the focused tree node). */
  readonly typeClick = output<string>();

  /** PRD 123 D9 / PRD 127 OQ4 — rail only (accounts cannot be assigned); `mine` = only the own account is readable. */
  protected readonly users = computed(() =>
    this.mode() === 'rail' ? this.store.usersChip() : null,
  );

  protected readonly section = computed<Section | null>(() =>
    openSection(this.chip(), {
      favorites: this.store.favorites().length,
      recents: this.store.recents().length,
      persons: this.store.listFor('persons').length,
      users: !!this.users() && !this.users()!.mine,
    }),
  );

  protected readonly searching = computed(() => !!this.query().trim());

  private readonly limits = signal<ReadonlyMap<string, number>>(new Map());
  /** Expansions below the sections; kept across section changes, dropped by "zuklappen". A host that re-creates the
   *  picker (the event sheet) keeps them by binding it. */
  readonly expanded = model<ReadonlyMap<string, boolean>>(new Map());
  /** PRD 127 D2 — expansions made during a search, dropped when the query changes. */
  private readonly searchToggled = signal<ReadonlyMap<string, boolean>>(new Map());

  private readonly childrenOf = computed(() => childIndex(this.store.resources()));

  private readonly sections = computed<TreeNode[]>(() => {
    const all = this.store.resources();
    const childrenOf = this.childrenOf();
    const section = (key: Section, label: string, children: TreeNode[], count: number) => ({
      key,
      label: tr(label),
      kind: 'section' as const,
      children,
      count,
    });
    const flat = (key: Section, label: string) => {
      const items = this.store.listFor(key);
      return section(key, label, resourceNodes(items, all, childrenOf, key), items.length);
    };
    const folders = (key: Section, label: string) => {
      const items = this.store.listFor(key);
      return section(key, label, typeFolders(items, all, childrenOf), items.length);
    };
    const persons = this.store.listFor('persons').length > 0;
    const users = this.users();
    return [
      ...(this.store.favorites().length ? [flat('favorites', 'resource_section_favorites')] : []),
      ...(this.store.recents().length ? [flat('recents', 'resource_section_recents')] : []),
      folders('resources', 'resource_section_resources'),
      ...(persons ? [folders('persons', 'resource_section_persons')] : []),
      ...(users && !users.mine ? [flat('users', 'resource_section_users')] : []),
    ];
  });

  private readonly search = computed(() =>
    searchGroups(
      this.store.resources(),
      this.mode() === 'rail' ? this.store.users() : [],
      this.query(),
      tr('resource_section_users'),
    ),
  );

  private readonly tree = computed(() => (this.searching() ? this.search() : this.sections()));

  protected readonly expandedGroups = computed(() => {
    const section = this.section();
    const open = new Set<string>(
      this.searching() ? this.search().map((g) => g.key) : section ? [section] : [],
    );
    for (const [key, isOpen] of this.searching() ? this.searchToggled() : this.expanded()) {
      if (isOpen) open.add(key);
      else open.delete(key);
    }
    return open;
  });

  /** PRD 127 D1 — the heads below the open one stick to the bottom, stacked: key → heads still below it. */
  protected readonly stickyBelow = computed(() => {
    const heads = [...this.sections().map((n) => n.key), ...(this.users()?.mine ? ['mine'] : [])];
    const open = heads.indexOf(this.section() ?? '');
    const below = new Map<string, number>();
    if (open < 0) return below;
    heads.slice(open + 1).forEach((key, i, rest) => below.set(key, rest.length - 1 - i));
    return below;
  });

  protected bottomOf(key: string): string | null {
    const n = this.stickyBelow().get(key);
    return n === undefined ? null : `calc(var(--sechead-h) * ${n})`;
  }

  protected readonly rows = computed<TreeRow[]>(() =>
    visibleRows(this.tree(), this.expandedGroups(), this.limits()),
  );

  /** A row with a toggle shifts its icon; the other rows get a spacer once any row has one. */
  protected readonly nested = computed(() =>
    this.rows().some((row) => row.node?.kind === 'resource' && row.node.children.length),
  );

  /** PRD 123 D8 — what the count row's "alle wählen" takes: every search hit, else every resource and person. */
  protected readonly allHits = computed<ResourceItem[]>(() => {
    const byId = new Map<string, ResourceItem>();
    const nodes = this.searching()
      ? this.search()
      : this.sections().filter((n) => n.key === 'resources' || n.key === 'persons');
    for (const node of nodes) for (const it of membersOf(node)) byId.set(it.id, it);
    return [...byId.values()];
  });

  protected readonly allChecked = computed(() => {
    const checked = this.checked();
    return this.allHits().every((it) => checked.has(it.id));
  });

  /** PRD 127 D8 — per visible type folder / group: are all its members selected? (checked is a Set: O(members)). */
  protected readonly groupChecked = computed(() => {
    const checked = this.checked();
    const all = new Map<string, boolean>();
    for (const row of this.rows()) {
      const node = row.node;
      if (node && (node.kind === 'group' || node.kind === 'type'))
        all.set(
          node.key,
          membersOf(node).every((it) => checked.has(it.id)),
        );
    }
    return all;
  });

  protected readonly mineChecked = computed(() => {
    const own = this.store.listFor('users')[0];
    return !!own && this.checked().has(own.id);
  });

  private readonly labelById = computed(
    () => new Map(this.store.resources().map((it) => [it.id, it.label])),
  );

  /** The distinct resources on screen — the host's selection engine and availability fetch use it; the own account
   *  while the "Meine Buchungen" row stands for it. */
  readonly shown = computed(() => {
    const byId = new Map<string, ResourceItem>();
    for (const row of this.rows()) if (row.node?.item) byId.set(row.node.item.id, row.node.item);
    const own = !this.searching() && this.users()?.mine ? this.store.listFor('users')[0] : null;
    if (own) byId.set(own.id, own);
    return [...byId.values()];
  });

  constructor() {
    this.store.ensureLoaded();
    effect(() => {
      this.query();
      this.limits.set(new Map());
      this.searchToggled.set(new Map());
    });
  }

  protected sectionIcon(key: string): string {
    return SECTION_ICONS[key] ?? 'folder';
  }

  /** PRD 127 D2 — a hit's parents in small text below it. */
  protected parentsOf(it: ResourceItem): string {
    const labels = this.labelById();
    return (it.parentIds ?? [])
      .map((id) => labels.get(id))
      .filter((label): label is string => !!label)
      .join(' · ');
  }

  protected icon(it: ResourceItem): string {
    return entityIcon(it.kind ?? 'resource', it.typeKey);
  }

  protected pillLabel(st: AvailabilityRow): string {
    return tr(
      st.status === 'AVAILABLE'
        ? 'event_free'
        : st.status === 'REQUEST_ONLY'
          ? 'resource_request'
          : 'event_booked',
    );
  }

  /** Shift-click must range-select, not select text. */
  protected onItemMousedown(event: MouseEvent): void {
    if (event.shiftKey) event.preventDefault();
  }

  /** PRD 127 OQ4 — "Meine Buchungen" picks the own account like a row click (Ctrl adds); when it is selected a click
   *  deselects it. */
  protected activateMine(event: MouseEvent): void {
    const own = this.store.listFor('users')[0];
    if (!own) return;
    this.pick.emit({
      item: own,
      ctrl: this.mineChecked() || event.ctrlKey || event.metaKey,
      shift: false,
    });
  }

  protected emitGroup(event: MouseEvent, items: ResourceItem[]): void {
    this.selectGroup.emit({ items, ctrl: event.ctrlKey || event.metaKey });
  }

  protected emitPick(event: MouseEvent | KeyboardEvent, item: ResourceItem): void {
    if (item.typeKey) this.typeClick.emit(item.typeKey);
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

  protected toggleGroup(node: TreeNode): void {
    if (node.kind === 'type') this.typeClick.emit(node.key.slice(1));
    else if (node.item?.typeKey) this.typeClick.emit(node.item.typeKey);
    const open = this.expandedGroups().has(node.key);
    (this.searching() ? this.searchToggled : this.expanded).update((map) =>
      new Map(map).set(node.key, !open),
    );
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
