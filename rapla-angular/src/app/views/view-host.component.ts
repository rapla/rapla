import { Component, ViewChild, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { MatTableModule, MatTableDataSource } from '@angular/material/table';
import { MatSort, MatSortModule } from '@angular/material/sort';
import { MatMenuModule, MatMenuTrigger } from '@angular/material/menu';

import { MatDialog } from '@angular/material/dialog';

import { GraphqlService, type ViewMeta, type ViewColumn } from '../graphql/graphql.service';
import { renderCell } from '../graphql/view-render';
import { extractRowContext, extractSelectionContext } from './row-context';
import { TableSelection } from './table-selection';
import { ROW_MENU_PROVIDERS, type RowMenuItem } from './row-menu';
import { MonthGridComponent } from './month-grid.component';
import { monthGridWindow } from './month-chunks';
import { EventSheetComponent, type EventSheetDialogData } from '../event/event-sheet.component';
import { rangeScopedDraft } from '../event/event-draft';
import { MutationBus } from '../graphql/mutation-bus';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { groupByWeekday, groupByColumn } from '../graphql/weekday-grouping';
import { ViewStateStore, type DateWindow } from '../state/view-state-store';
import { FilterStore, type FilterEntry } from '../state/filter-store';
import { resolveWindowFromInputs } from './view-inputs';
import { buildVariablesByType } from './variable-binder';
import { LastViewStore } from './last-view-store';
import { formatGroupLabel } from './group-format';
import { isProjectedView, projectRow } from './stat-projection';

/** A rendered section: rows sharing one group value (or one flat group when ungrouped). */
interface Section {
  id: string;
  label: string;
  rows: Record<string, unknown>[];
}

/** The data envelope — root-field-agnostic: we take whatever array `data` carries
 *  (appointmentBlocks, appointmentBlockStats, …). */
type ViewData = Record<string, unknown>;

/**
 * The column to group weekday sections by: the first {@code Date}/
 * {@code LocalDateTime} column in the view meta (hidden or not), whatever its
 * alias. Falls back to {@code 'start'}. Without this the host would grope for a
 * fixed {@code 'start'} field and dump every row into "Ohne Datum" when a stored
 * view aliases its date column differently (e.g. {@code date}). Returns '' when
 * there is no date column (→ the view is not groupable, renders flat).
 */
export function pickDateAlias(columns: ViewColumn[]): string {
  return columns.find((c) => c.type === 'Date' || c.type === 'LocalDateTime')?.alias ?? '';
}

/**
 * Whether the active filter carries a SCOPE — at least one scoping chip. A
 * scope narrows the query to a selectable (resource / group) or a user; an
 * {@code event} chip is a navigation target, not a scope. No scope → the view
 * must not query (performance: an unscoped view is a full-window firehose).
 */
export function hasScope(chips: FilterEntry[]): boolean {
  return chips.some((c) => c.kind !== 'event');
}

/**
 * The generic view host (PRD 078 routing — {@code /app/views/:viewName}). One
 * component renders EVERY view: it looks up the view definition, resolves the
 * date window (runtime {@link ViewStateStore} window, else the view's
 * {@code inputs} defaults), executes the {@code @view} query, and renders
 * generically from {@code extensions.view}. Day-grouped views (no server
 * directive — PRD 074) get client-side {@link groupByWeekday} sectioning.
 *
 * The consumer path: {@code executeView(viewName)} runs the STORED query
 * (server-held) and the render-meta — columns, grouping, the variable signature —
 * arrives on {@code extensions.view}. There is no code-shipped fallback ViewMeta;
 * the server is the single source of truth for what a view looks like.
 */
@Component({
  selector: 'app-view-host',
  imports: [MatTableModule, MatSortModule, MatMenuModule, MonthGridComponent],
  template: `
    <section class="content">
      <h2 class="view-title">{{ meta()?.title ?? viewName() }}</h2>

      @if (noScope()) {
          <p class="empty">
            Wähle links eine Ressource, Gruppe oder Person als <strong>Scope</strong> (oder
            füge über die Suche einen Scope-Chip hinzu), um Termine zu laden.
          </p>
        } @else if (loading()) {
          <p class="meta">lädt…</p>
        } @else if (error()) {
          <p class="error">{{ error() }}</p>
        } @else {
          <p class="meta">
            {{ total() }} {{ rowLabelText() }}
            @if (groupLabelText()) {
              <span class="sep">·</span> {{ groupCount() }} {{ groupLabelText() }}
            }
          </p>

          @if (isMonth()) {
            <!-- PRD 095 — month calendar grid (spanning bars); replaces the table. -->
            <app-month-grid
              [rows]="displayRows()"
              [anchor]="monthAnchor()"
              (openEvent)="openEventSheet($event)"
              (createRange)="openCreateRange($event)"
            />
          } @else if (total() > 0) {
            <table
              mat-table
              [dataSource]="dataSource"
              matSort
              [matSortDisabled]="isGrouped()"
              [matSortActive]="isGrouped() ? '' : defaultSortAlias()"
              matSortDirection="asc"
              class="grid"
              tabindex="0"
              aria-multiselectable="true"
              [attr.aria-activedescendant]="activeRowId()"
              (keydown)="onTableKeydown($event)"
            >
              @for (col of tableColumns(); track col.alias) {
                <ng-container [matColumnDef]="col.alias">
                  <th mat-header-cell *matHeaderCellDef mat-sort-header [disabled]="isGrouped()">
                    {{ col.header ?? col.alias }}
                  </th>
                  <td mat-cell *matCellDef="let row">{{ cell(row, col) }}</td>
                </ng-container>
              }
              <!-- Row actions (PRD 094): ⋮ opens the shared row menu; only rows
                   with a typed subject (D4) get a button. -->
              <ng-container matColumnDef="__actions">
                <th mat-header-cell *matHeaderCellDef class="actions-col"></th>
                <td mat-cell *matCellDef="let row" class="actions-col">
                  @if (rowItems(row).length > 0) {
                    <button
                      type="button"
                      class="row-menu-btn"
                      aria-label="Aktionen"
                      [matMenuTriggerFor]="rowMenu"
                      (click)="prepareMenu(row); $event.stopPropagation()"
                    >
                      ⋮
                    </button>
                  }
                </td>
              </ng-container>
              <!-- Group-header row (grouped views): one cell spanning all columns. -->
              <ng-container matColumnDef="__groupHeader">
                <td mat-cell *matCellDef="let g" [attr.colspan]="displayedColumns().length" class="group-cell">
                  {{ g['__label'] }} <span class="cnt">({{ g['__count'] }})</span>
                </td>
              </ng-container>
              <tr mat-header-row *matHeaderRowDef="displayedColumns()"></tr>
              <tr mat-row *matRowDef="let row; columns: ['__groupHeader']; when: isGroupRow" class="group-row"></tr>
              <tr
                mat-row
                *matRowDef="let row; columns: displayedColumns(); when: isDataRow"
                [attr.id]="rowId(row)"
                [class.selected]="selection.isSelected(row)"
                [class.active-row]="selection.active() === row"
                [attr.aria-selected]="selection.isSelected(row)"
                (mousedown)="onRowMousedown($event)"
                (click)="onRowClick($event, row)"
                (contextmenu)="onContextMenu($event, row)"
                (dblclick)="onRowDblClick(row)"
              ></tr>
            </table>
          } @else {
            <p class="empty">Keine Termine im Zeitraum.</p>
          }
        }
      </section>
      <mat-menu #rowMenu="matMenu">
        @for (item of menuItems(); track item.id) {
          <button mat-menu-item type="button" (click)="item.run()">{{ item.label }}</button>
        }
      </mat-menu>
      <span
        class="ctx-anchor"
        [style.left.px]="menuX()"
        [style.top.px]="menuY()"
        [matMenuTriggerFor]="rowMenu"
        #ctxTrigger="matMenuTrigger"
      ></span>
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .content {
        margin: 1.25rem 0;
        padding: 0 1rem;
      }
      .view-title {
        font-size: 1.3rem;
        font-weight: 500;
        margin: 0 0 0.5rem;
      }
      .meta {
        color: rgba(0, 0, 0, 0.6);
        font-size: 0.85rem;
        margin: 0 0 0.75rem;
      }
      .meta .sep {
        margin: 0 0.3rem;
        opacity: 0.4;
      }
      .day {
        margin-bottom: 1.25rem;
      }
      .day h3 {
        font-size: 1rem;
        font-weight: 600;
        margin: 0 0 0.4rem;
        color: var(--mat-sys-primary, #3f51b5);
      }
      .day .cnt {
        color: rgba(0, 0, 0, 0.45);
        font-weight: 400;
        font-size: 0.85rem;
      }
      table.grid {
        width: 100%;
        border-collapse: collapse;
        font-size: 0.85rem;
      }
      .grid th,
      .grid td {
        text-align: left;
        padding: 0.35rem 0.6rem;
        border-bottom: 1px solid rgba(0, 0, 0, 0.08);
      }
      .grid th {
        font-weight: 600;
        color: rgba(0, 0, 0, 0.6);
      }
      table.grid:focus {
        outline: none;
      }
      .grid tr.selected td {
        background: var(--mat-sys-secondary-container, rgba(63, 81, 181, 0.12));
      }
      .grid tr.active-row td:first-child {
        box-shadow: inset 3px 0 0 var(--mat-sys-primary, #3f51b5);
      }
      .group-cell {
        font-weight: 600;
        color: var(--mat-sys-primary, #3f51b5);
        padding-top: 0.9rem;
      }
      .group-cell .cnt {
        color: rgba(0, 0, 0, 0.45);
        font-weight: 400;
      }
      .empty {
        color: rgba(0, 0, 0, 0.5);
        font-style: italic;
      }
      .error {
        color: #c62828;
      }
      .actions-col {
        width: 2.2rem;
        text-align: right;
      }
      .row-menu-btn {
        border: none;
        background: transparent;
        cursor: pointer;
        font-size: 1.1rem;
        line-height: 1;
        padding: 0.15rem 0.4rem;
        border-radius: 4px;
        color: rgba(0, 0, 0, 0.55);
        opacity: 0.25;
      }
      tr:hover .row-menu-btn,
      .row-menu-btn:focus-visible,
      .row-menu-btn[aria-expanded='true'] {
        opacity: 1;
        background: rgba(0, 0, 0, 0.05);
      }
      .ctx-anchor {
        position: fixed;
        width: 0;
        height: 0;
      }
    `,
  ],
})
export class ViewHostComponent {
  private readonly gql = inject(GraphqlService);
  private readonly dialog = inject(MatDialog);
  private readonly viewState = inject(ViewStateStore);
  private readonly filter = inject(FilterStore);
  private readonly lastView = inject(LastViewStore);

  /** Bound from the route param {@code :viewName} (withComponentInputBinding). */
  readonly viewName = input.required<string>();

  readonly meta = signal<ViewMeta | null>(null);
  readonly rows = signal<Record<string, unknown>[]>([]);
  readonly total = signal(0);
  readonly groupCount = signal(0);
  readonly rowLabelText = computed(() => {
    const parts = (this.meta()?.rowLabel ?? 'Eintrag|Einträge').split('|');
    return this.total() === 1 ? parts[0] : (parts[1] ?? parts[0]);
  });
  readonly groupLabelText = computed(() => {
    const label = this.meta()?.groupLabel;
    if (!label) return null;
    const parts = label.split('|');
    return this.groupCount() === 1 ? parts[0] : (parts[1] ?? parts[0]);
  });
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  /** True when no scope chip is set → the view shows a hint and fires NO query
   *  (performance: an unscoped view query is a full-window firehose). */
  readonly noScope = signal(false);

  /** Server render-info: the column to group by ({@code extensions.view.groupBy}),
   *  falling back to the first date column until the server emits groupBy. */
  readonly groupAlias = computed(
    () => this.meta()?.groupBy ?? pickDateAlias(this.meta()?.columns ?? []),
  );
  /** A view is groupable iff it has a group column. */
  readonly canGroup = computed(() => this.groupAlias() !== '');
  /** Group when in week mode AND the view actually has a group column. */
  readonly grouped = computed(() => this.viewState.renderMode() === 'week' && this.canGroup());
  /** Whether the view CAN group (declares a {@code @column(group: true)} → server
   *  emits {@code groupBy}). Drives the availability of the WEEK render mode. */
  readonly groupable = computed(() => !!this.meta()?.groupBy);
  /** Render as day/value BLOCKS (group-header rows) — only in WEEK mode AND when the
   *  view is groupable. TABLE mode (or a non-groupable view) → flat sortable table. */
  readonly isGrouped = computed(
    () => this.viewState.renderMode() === 'week' && this.groupable(),
  );
  /** PRD 095 — MONTH mode: render the calendar grid instead of the table. */
  readonly isMonth = computed(() => this.viewState.renderMode() === 'month');
  /** Anchor date for the month grid — the window's from (the control strip keeps
   *  the window month-aligned in month mode, D4). */
  readonly monthAnchor = computed(() => {
    const from = this.viewState.window()?.from;
    if (from) return from;
    const now = new Date();
    return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
  });

  readonly visibleColumns = computed<ViewColumn[]>(() =>
    (this.meta()?.columns ?? [])
      .filter((c) => !c.hidden)
      .sort((a, b) => (a.order ?? 999) - (b.order ?? 999)),
  );

  /** Columns for the flat sortable table: the visible columns, plus the (otherwise
   *  hidden) date/group column prepended so the day is still shown without grouping. */
  readonly tableColumns = computed<ViewColumn[]>(() => {
    const visible = this.visibleColumns();
    if (this.isGrouped()) return visible; // the date is the group header, not a column
    const alias = this.groupAlias();
    if (!alias || visible.some((c) => c.alias === alias)) return visible;
    const groupCol = (this.meta()?.columns ?? []).find((c) => c.alias === alias);
    return groupCol ? [{ ...groupCol, hidden: false }, ...visible] : visible;
  });
  readonly columnAliases = computed(() => this.tableColumns().map((c) => c.alias));

  /** PRD 099 — Swing/Excel row selection over the RENDERED order (fed from
   *  {@link dataSource}.connect() so shift-ranges follow the current sort;
   *  group-header rows never enter the key list). Keys are the row objects —
   *  a re-query mints new objects, so the selection clears itself. */
  readonly selection = new TableSelection<Record<string, unknown>>();
  private readonly renderedRows = signal<Record<string, unknown>[]>([]);
  private readonly rowIdByRow = computed(
    () => new Map(this.renderedRows().map((r, i) => [r, `vh-row-${i}`])),
  );
  readonly activeRowId = computed(() => {
    const active = this.selection.active();
    return active ? (this.rowIdByRow().get(active) ?? null) : null;
  });

  rowId(row: Record<string, unknown>): string | null {
    return this.rowIdByRow().get(row) ?? null;
  }

  /** Shift-click must range-select, not select text. */
  onRowMousedown(event: MouseEvent): void {
    if (event.shiftKey) event.preventDefault();
  }

  onRowClick(event: MouseEvent, row: Record<string, unknown>): void {
    this.selection.pointer(row, {
      shift: event.shiftKey,
      ctrl: event.ctrlKey || event.metaKey,
    });
  }

  onTableKeydown(event: KeyboardEvent): void {
    const handled = this.selection.key(event.key, {
      shift: event.shiftKey,
      ctrl: event.ctrlKey || event.metaKey,
    });
    if (!handled) return;
    event.preventDefault();
    const id = this.activeRowId();
    if (id) document.getElementById(id)?.scrollIntoView?.({ block: 'nearest' });
  }

  /** PRD 094 — row menu. Providers dispatch on the typed row subject (D4);
   *  the actions column only appears when at least one row has menu items. */
  private readonly menuProviders = inject(ROW_MENU_PROVIDERS, { optional: true }) ?? [];
  readonly menuItems = signal<RowMenuItem[]>([]);
  readonly menuX = signal(0);
  readonly menuY = signal(0);
  @ViewChild('ctxTrigger') private ctxTrigger?: MatMenuTrigger;

  rowItems(row: Record<string, unknown>): RowMenuItem[] {
    if (this.menuProviders.length === 0) return [];
    const ctx = extractRowContext(row, this.viewName(), this.meta()?.columns ?? []);
    if (!ctx.primary) return [];
    return this.menuProviders.flatMap((p) => p.items(ctx));
  }

  /** Menu items for the CURRENT selection (PRD 099) — the row is absorbed into
   *  the selection first (unselected target replaces it, Explorer convention). */
  private selectionItems(row: Record<string, unknown>): RowMenuItem[] {
    if (!this.selection.isSelected(row)) this.selection.pointer(row);
    if (this.menuProviders.length === 0) return [];
    const ctx = extractSelectionContext(
      this.selection.selectedKeys(),
      this.viewName(),
      this.meta()?.columns ?? [],
    );
    if (ctx.subjects.length === 0) return [];
    return this.menuProviders.flatMap((p) => p.items(ctx));
  }

  prepareMenu(row: Record<string, unknown>): void {
    this.menuItems.set(this.selectionItems(row));
  }

  /** Double-click = the menu's edit action (Anzeigen fallback on read-only
   *  rows) — single-row selections only (Swing parity). */
  onRowDblClick(row: Record<string, unknown>): void {
    if (this.selection.count() > 1) return;
    const items = this.rowItems(row);
    const action = items.find((i) => i.id === 'edit') ?? items.find((i) => i.id === 'view');
    action?.run();
  }

  onContextMenu(event: MouseEvent, row: Record<string, unknown>): void {
    const items = this.selectionItems(row);
    if (items.length === 0) return;
    event.preventDefault();
    this.menuItems.set(items);
    this.menuX.set(event.clientX);
    this.menuY.set(event.clientY);
    this.ctxTrigger?.openMenu();
  }

  private readonly hasRowMenu = computed(
    () => this.menuProviders.length > 0 && this.displayRows().some((r) => this.rowItems(r).length > 0),
  );
  /** Table columns incl. the trailing actions column when any row has a menu. */
  readonly displayedColumns = computed(() =>
    this.hasRowMenu() ? [...this.columnAliases(), '__actions'] : this.columnAliases(),
  );
  /** Default sort: the date/group column (chronological), else the first column. */
  readonly defaultSortAlias = computed(() => this.groupAlias() || this.tableColumns()[0]?.alias || '');

  /** Material table source — built-in client sort via {@link MatSort} (no hand-rolled
   *  sorting). Fed from {@link displayRows} by an effect in the constructor. */
  readonly dataSource = new MatTableDataSource<Record<string, unknown>>([]);
  private sortRef: MatSort | null = null;
  /** Connect MatSort once the table renders (it sits behind an {@code @if}). Sorting
   *  is connected ONLY for ungrouped views — sorting a grouped table would scramble
   *  the day blocks (an effect in the constructor reconciles on grouping change). */
  @ViewChild(MatSort) set matSort(sort: MatSort | undefined) {
    this.sortRef = sort ?? null;
    this.dataSource.sort = this.isGrouped() ? null : this.sortRef;
  }

  /** True for aggregation/pivot views (columns carry `kind`). Drives BOTH the
   *  row projection AND where the resource selection binds (allocatableFilter vs
   *  filter.allocatableIdsIn). A boolean computed → flips false→true once, no loop. */
  readonly aggregated = computed(() => isProjectedView(this.meta()?.columns ?? []));

  /** Stable key of the variable signature — flips once (null→signature) when the
   *  view's contract resolves, so the query effect re-runs WITHOUT looping on each
   *  response (a string computed memoizes by value). */
  private readonly bindingKey = computed(() => JSON.stringify(this.meta()?.variables ?? []));

  /** Rows projected into flat {alias: value} form. Flat views (no `kind`) pass
   *  through; aggregation/pivot views get projected via the column descriptors. */
  readonly displayRows = computed<Record<string, unknown>[]>(() => {
    const cols = this.meta()?.columns ?? [];
    const rows = this.rows();
    return this.aggregated() ? rows.map((r) => projectRow(r, cols)) : rows;
  });

  /** Day-sections (date group column → weekday headers), generic value sections
   *  (any other group column), or one flat group when ungrouped. */
  readonly groups = computed<Section[]>(() => {
    const rows = this.displayRows();
    if (!this.isGrouped()) return rows.length ? [{ id: '', label: '', rows }] : [];
    const meta = this.meta();
    const alias = this.groupAlias();

    // Server render-info: group by the marked column; format each header with the
    // opaque groupFormat token (e.g. "EE dd.MM" → "Mo 15.06").
    if (meta?.groupBy) {
      const fmt = meta.groupFormat;
      return groupByColumn(rows, alias).map((g) => ({
        id: g.key,
        label: fmt ? formatGroupLabel(g.key, fmt) : g.label,
        rows: g.rows,
      }));
    }

    // Fallback until the server emits groupBy: a date column → German weekday sections.
    const col = (meta?.columns ?? []).find((c) => c.alias === alias);
    if (col?.type === 'Date' || col?.type === 'LocalDateTime') {
      return groupByWeekday(rows, alias).map((g) => ({
        id: String(g.weekday),
        label: g.label,
        rows: g.rows,
      }));
    }
    return groupByColumn(rows, alias).map((g) => ({ id: g.key, label: g.label, rows: g.rows }));
  });

  /** The rows fed to the Material table. Flat views pass {@link displayRows}
   *  straight through; grouped views interleave a marker {@code __group} header
   *  row before each section so Material renders day/value BLOCKS via a `when`
   *  row predicate (sorting is disabled in this mode — at most by group). */
  readonly tableRows = computed<Record<string, unknown>[]>(() => {
    if (!this.isGrouped()) return this.displayRows();
    const out: Record<string, unknown>[] = [];
    for (const s of this.groups()) {
      out.push({ __group: true, __label: s.label, __count: s.rows.length });
      out.push(...s.rows);
    }
    return out;
  });

  /** Row-template predicates for the grouped Material table. */
  readonly isGroupRow = (_i: number, row: Record<string, unknown>): boolean => row['__group'] === true;
  readonly isDataRow = (_i: number, row: Record<string, unknown>): boolean => row['__group'] !== true;

  /** Monotonic request id — a slow (e.g. 500-row firehose) response from an OLDER
   *  query must not clobber a newer, filtered one. Stale responses are ignored. */
  private reqToken = 0;

  /** Bumped by MutationBus.mutated$ (any own mutation landed — edit / delete /
   *  undo / quick-create) → re-query. Interim manual trigger; a server change
   *  listener replaces the emitter later (see MutationBus). */
  private readonly refreshTick = signal(0);

  constructor() {
    inject(MutationBus)
      .mutated$.pipe(takeUntilDestroyed())
      .subscribe(() => this.refreshTick.update((n) => n + 1));
    // Material client sort: sort by the displayed text, EXCEPT date columns sort
    // by their raw ISO value (chronological, not by the formatted label).
    this.dataSource.sortingDataAccessor = (row, id) => {
      const col = (this.meta()?.columns ?? []).find((c) => c.alias === id);
      if (!col) return '';
      if (col.type === 'Date' || col.type === 'LocalDateTime') {
        const raw = row[id];
        return typeof raw === 'string' ? raw : '';
      }
      return renderCell(row, col).toLowerCase();
    };
    // Feed the table source — flat rows, or grouped rows with header markers.
    effect(() => {
      this.dataSource.data = this.tableRows();
    });
    // PRD 099 — mirror the RENDERED order (sort applied, group headers dropped)
    // into the selection model, so ranges + keyboard nav follow what's on screen.
    this.dataSource
      .connect()
      .pipe(takeUntilDestroyed())
      .subscribe((rendered) => {
        const dataRows = rendered.filter((r) => r['__group'] !== true);
        this.renderedRows.set(dataRows);
        this.selection.setRows(dataRows);
      });
    // Reconnect/disconnect sort when grouping changes (grouped = no column sort).
    effect(() => {
      this.dataSource.sort = this.isGrouped() ? null : this.sortRef;
    });

    // Persist the opened view so the default landing route restores it next time.
    effect(() => {
      const name = this.viewName();
      untracked(() => this.lastView.set(name));
    });

    // Re-query whenever the view, the window, OR the active filter (chips) change.
    // PERFORMANCE GATE: a view only queries when a SCOPE is set — at least one
    // scoping chip (resource / group / user; an `event` chip is a navigation
    // target, not a scope). With no scope we fire NO query (an unscoped view is a
    // full-window firehose) and show a hint instead.
    effect(() => {
      const viewName = this.viewName();
      const w = this.viewState.window();
      const chips = this.filter.entries();
      // PRD 095 D4 — the stored window is the ANCHOR; month mode queries the
      // padded 42-day grid range derived from it (the anchor is not rewritten).
      const month = this.isMonth();
      this.bindingKey(); // re-query when the variable signature resolves
      this.refreshTick(); // re-query after a main-view mutation (delete / undo)
      if (!hasScope(chips)) {
        this.clearForNoScope();
        return;
      }
      this.noScope.set(false);
      this.run(viewName, month && w ? monthGridWindow(w.from) : w, chips);
    });
  }

  /** No scope: drop any in-flight result, clear the table, show the hint. */
  private clearForNoScope(): void {
    this.reqToken++; // invalidate any in-flight response
    this.loading.set(false);
    this.error.set(null);
    this.rows.set([]);
    this.total.set(0);
    this.groupCount.set(0);
    this.noScope.set(true);
  }

  private run(viewName: string, window: DateWindow | null, chips: FilterEntry[]): void {
    const token = ++this.reqToken;
    this.loading.set(true);
    this.error.set(null);
    // Type-driven binding: fill each declared variable BY TYPE (ReservationFilter ←
    // window+selection, AllocatableFilter ← selection). The server emits the
    // variable signature on extensions.view.variables; the FIRST query (before meta
    // lands) sends {} → the server merges its stored defaults, then bindingKey
    // re-queries with the resolved signature.
    // untracked: run() executes INSIDE the query effect — reading meta() tracked
    // here would make the effect re-fire on every response (meta.set) → infinite
    // loop. The one-time re-query when the signature lands is driven by bindingKey.
    const signature = untracked(() => this.meta()?.variables) ?? [];
    const resourceIds = chips.filter((c) => c.kind === 'resource').map((c) => c.id);
    // ReservationFilter.ownerEq is single — take the first user chip (the pinned
    // self is the common case; multi-user scope would need ownerIdsIn server-side).
    const ownerId = chips.find((c) => c.kind === 'user')?.id ?? null;
    const variables = buildVariablesByType(signature, { window, resourceIds, ownerId });
    this.gql.executeView<ViewData>(viewName, variables).subscribe({
      next: (res) => {
        if (token !== this.reqToken) return; // a newer query superseded this one
        if (res.errors?.length) {
          this.error.set(res.errors.map((e) => e.message).join('; '));
          this.loading.set(false);
          return;
        }
        // Root-agnostic: take whatever array `data` carries, not a fixed field name.
        const rows =
          (Object.values(res.data ?? {}).find(Array.isArray) as
            | Record<string, unknown>[]
            | undefined) ?? [];
        const viewMeta = res.extensions?.view ?? null;
        this.meta.set(viewMeta);
        this.total.set(rows.length);
        const groupAlias = viewMeta?.groupBy;
        this.groupCount.set(
          groupAlias ? new Set(rows.map((r) => r[groupAlias])).size : 0,
        );
        this.rows.set(rows);
        this.loading.set(false);
        // Apply the view's supported render modes — keeps the user's remembered mode
        // (restored from localStorage) when this view supports it, else the view's
        // default. Idempotent per response.
        const renderModes = viewMeta?.renderModes ?? ['table' as const];
        untracked(() => this.viewState.applyViewModes(renderModes));
        // Seed the date-nav window from the server's input metadata, once.
        if (!window) {
          const seeded = resolveWindowFromInputs(res.extensions?.view?.inputs ?? []);
          if (seeded) untracked(() => this.viewState.setWindow(seeded));
        }
      },
      error: (err: { status?: number; error?: { message?: string } }) => {
        if (token !== this.reqToken) return; // superseded — ignore its failure too
        // 401 is owned by the auth refresh interceptor (PRD 072) — stay loading.
        if (err?.status === 401) return;
        this.error.set(err?.error?.message ?? `Request failed (HTTP ${err?.status ?? '?'})`);
        this.loading.set(false);
      },
    });
  }

  cell(row: Record<string, unknown>, col: ViewColumn): string {
    return renderCell(row, col);
  }

  /** PRD 095 — month-grid chip click → event sheet dialog (same shape as the row menu's edit). */
  openEventSheet(reservationId: string): void {
    this.dialog.open(EventSheetComponent, {
      data: { id: reservationId } satisfies EventSheetDialogData,
      width: '960px',
      maxWidth: '95vw',
      height: '90vh',
      restoreFocus: false,
    });
  }

  /** Memoized first creatable event type (same query the toolbar's "Neu" uses). */
  private eventTypeKey: string | null = null;

  /** PRD 095 Phase 3 — month-grid drag-create: day-range selection opens the
   *  event sheet PREFILLED (range-seeded scoped draft; nothing persists until save). */
  openCreateRange(range: { from: string; to: string }): void {
    const chips = this.filter.entries();
    const open = (typeKey: string) => {
      const draft = rangeScopedDraft(typeKey, chips, range.from, range.to);
      this.dialog.open(EventSheetComponent, {
        data: { id: draft.id, isNew: true, draft } satisfies EventSheetDialogData,
        width: '960px',
        maxWidth: '95vw',
        height: '90vh',
        restoreFocus: false,
      });
    };
    if (this.eventTypeKey) {
      open(this.eventTypeKey);
      return;
    }
    this.gql
      .query<{ types: { key: string; classificationType: string }[] }>(
        `query { types { key classificationType } }`,
      )
      .subscribe((resp) => {
        const key =
          (resp.data?.types ?? []).find((t) => t.classificationType === 'RESERVATION')?.key ??
          'event';
        this.eventTypeKey = key;
        open(key);
      });
  }
}
