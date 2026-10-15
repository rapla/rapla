import {
  Component,
  DestroyRef,
  ElementRef,
  computed,
  effect,
  inject,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import {
  MAT_DIALOG_DATA,
  MatDialog,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { from, map, mergeMap } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { ImportWorklistService, type TemplateSuggestion } from './import-worklist.service';
import { ParkedEventsService } from './parked-events.service';
import { BindPickService } from './bind-pick.service';
import { BindConfirmDialogComponent } from './bind-confirm-dialog.component';
import { NgTemplateOutlet } from '@angular/common';
import { MatMenuModule } from '@angular/material/menu';
import { MatChipsModule } from '@angular/material/chips';
import { StagedDetailsButtonComponent } from './staged-details.component';
import {
  BindTargetDialogComponent,
  type BindTargetDialogData,
  type BindTargetResult,
} from './bind-target-dialog.component';
import {
  changedItems,
  currentSemester,
  linkedOfGroups,
  openItemsOfGroups,
  semesterIndex,
  semesterRange,
  shiftSemester,
  type BindCandidate,
  type DefaultTemplates,
  type LinkedEvent,
  type WorklistItem,
} from './import-models';
import { EventSheetComponent, type EventSheetDialogData } from '../event/event-sheet.component';
import { ViewStateStore } from '../state/view-state-store';
import { NewEventOptionsService, type EventTemplate } from '../event/new-event-options.service';
import { TPipe, t as tr } from '../i18n/i18n.service';

export interface ImportSyncDialogData {
  groupIds: string[];
  groupLabel: string;
  semester: string;
  /** ISO LocalDateTime where created events materialize (v1 placement: visible week). */
  defaultStart: string;
  /** Kurs id → display name, from the chips (fallback while the worklist is not loaded). */
  groupNames?: Record<string, string>;
}

/**
 * PRD 104 v3 — the "Dualis-Sync" dialog: ONE entry point for every per-Kurs
 * import question. Tabs: Offen (template pick + type-grouped checkbox list),
 * Verknüpft (stamped reservations — durable source, 2026-08-11 redesign — with
 * inline "geändert" / "nicht mehr im Export" states). Source-neutral —
 * every label derives from the worklist's sourceName.
 *
 * The Übernehmen action itself waits for the generic staging mutations
 * (`createFromStagedItems` / `bindStagedEvent`, see the consumer contract in
 * the PRD); until they land the footer states so instead of pretending.
 */
@Component({
  selector: 'app-import-sync-dialog',
  imports: [
    NgTemplateOutlet,
    MatChipsModule,
    MatMenuModule,
    StagedDetailsButtonComponent,
    MatDialogModule,
    MatIconModule,
    MatButtonModule,
    TPipe,
  ],
  template: `
    <div class="title-row">
      <h2 mat-dialog-title>{{ title() }}</h2>
      <!-- dhbwrapla PRD 004 § H5a: exactly one semester, stepped; default from the calendar week -->
      <div class="semester-step">
        <button
          matIconButton
          [disabled]="!canStep(-1)"
          (click)="stepSemester(-1)"
          [title]="'import_sync_semester_prev' | t"
        >
          <mat-icon>chevron_left</mat-icon>
        </button>
        <button matButton class="semester" [matMenuTriggerFor]="semMenu">{{ semester() }}</button>
        <mat-menu #semMenu="matMenu">
          @for (s of semesters(); track s) {
            <button mat-menu-item (click)="goSemester(s)">{{ s }}</button>
          }
        </mat-menu>
        <button
          matIconButton
          [disabled]="!canStep(1)"
          (click)="stepSemester(1)"
          [title]="'import_sync_semester_next' | t"
        >
          <mat-icon>chevron_right</mat-icon>
        </button>
      </div>
    </div>

    @if (courseGroups().length > 1) {
      <!-- H5a: Kurs multi-filter, from the chips' Kurse -->
      <mat-chip-listbox class="kurs-filter" multiple [attr.aria-label]="'import_kurs_filter' | t">
        @for (g of courseGroups(); track g.id) {
          <mat-chip-option
            [selected]="!kursOff().has(g.id)"
            (selectionChange)="setKurs(g.id, $event.selected)"
          >
            {{ g.name }}
          </mat-chip-option>
        }
      </mat-chip-listbox>
    }

    <mat-dialog-content>
      <div class="select-all">
        <!-- same toggle and texts as the resource search ("alle wählen" / "Auswahl aufheben") -->
        <button type="button" class="listall" (click)="allChecked() ? selectNone() : selectAll()">
          {{ (allChecked() ? 'resource_deselect_all' : 'resource_select_all') | t }}
        </button>
      </div>
      <div class="list">
        <div class="group-head">
          <input
            type="checkbox"
            class="group-check"
            [attr.aria-label]="'import_select_group' | t"
            [checked]="groupState('v') === 'all'"
            [indeterminate]="groupState('v') === 'some'"
            (change)="toggleGroupSelection('v')"
          />
          <button type="button" class="section" (click)="toggleGroup('lv')">
            {{ collapsed().has('lv') ? '▸' : '▾' }}
            {{ 'import_group_open_lectures' | t: lectures().length }}
          </button>
          <span class="group-all">{{ groupSelLabel('v') }}</span>
          <button
            type="button"
            class="tpl-chip group-chip"
            [disabled]="selectedIn('v').length === 0"
            [matMenuTriggerFor]="tplMenu"
            (menuOpened)="focusMenuSearch()"
            (click)="menuTarget.set({ group: 'v' })"
          >
            {{ groupChipLabel('v') }} ▾
          </button>
        </div>
        @if (!collapsed().has('lv')) {
          @for (item of lectures(); track item.sourceId) {
            <ng-container *ngTemplateOutlet="openRow; context: { $implicit: item }" />
          }
        }
        <div class="group-head">
          <input
            type="checkbox"
            class="group-check"
            [attr.aria-label]="'import_select_group' | t"
            [checked]="groupState('p') === 'all'"
            [indeterminate]="groupState('p') === 'some'"
            (change)="toggleGroupSelection('p')"
          />
          <button type="button" class="section" (click)="toggleGroup('pr')">
            {{ collapsed().has('pr') ? '▸' : '▾' }}
            {{ 'import_group_open_exams' | t: exams().length }}
          </button>
          <span class="group-all">{{ groupSelLabel('p') }}</span>
          <button
            type="button"
            class="tpl-chip group-chip"
            [disabled]="selectedIn('p').length === 0"
            [matMenuTriggerFor]="tplMenu"
            (menuOpened)="focusMenuSearch()"
            (click)="menuTarget.set({ group: 'p' })"
          >
            {{ groupChipLabel('p') }} ▾
          </button>
        </div>
        @if (!collapsed().has('pr')) {
          @for (item of exams(); track item.sourceId) {
            <ng-container *ngTemplateOutlet="openRow; context: { $implicit: item }" />
          }
        }
        <button type="button" class="section" (click)="toggleGroup('chg')">
          {{ collapsed().has('chg') ? '▸' : '▾' }}
          {{ 'import_group_changed' | t: changedLinked().length }}
        </button>
        @if (!collapsed().has('chg')) {
          @for (e of changedLinked(); track e.id) {
            @if (changedOf(e.id); as ch) {
              <div class="row2">
                <div class="l1">
                  <app-staged-details-button
                    [label]="ch.unit ?? ch.name"
                    [sourceItemId]="ch.sourceId"
                    [changedSince]="ch.changedSince"
                  />
                  <span class="name">{{ e.name }}</span>
                  <span class="acts">
                    <button type="button" class="act" (click)="applyChanges(e, ch)">
                      {{ 'import_apply' | t }}
                    </button>
                    <button type="button" class="act quiet" (click)="dismiss(ch)">
                      {{ 'import_dismiss' | t }}
                    </button>
                  </span>
                </div>
                <div class="l2">
                  {{ ch.groupName }} · {{ 'import_changed_since' | t: ch.changedSince }}
                </div>
              </div>
            }
          }
        }
        <button type="button" class="section" (click)="toggleGroup('lnk')">
          {{ collapsed().has('lnk') ? '▸' : '▾' }}
          {{ 'import_group_linked' | t: unchangedLinked().length }}
        </button>
        @if (!collapsed().has('lnk')) {
          @for (e of unchangedLinked(); track e.id) {
            <div class="row2">
              <div class="l1">
                <span class="name">{{ e.name }}</span>
                <button
                  type="button"
                  class="act"
                  [title]="'import_linked_tooltip' | t"
                  (click)="jumpToEvent(e)"
                  (dblclick)="editEvent(e)"
                >
                  ↗ {{ 'import_open_event' | t }}
                </button>
              </div>
              <div class="l2">{{ semesterOf(e) }}</div>
            </div>
          }
        }
      </div>

      <mat-menu #tplMenu="matMenu" (closed)="menuQuery.set('')">
        <div class="menu-search" role="presentation" (click)="$event.stopPropagation()">
          <input
            #menuSearch
            [placeholder]="'import_template_search' | t"
            [value]="menuQuery()"
            (input)="menuQuery.set($any($event.target).value)"
            (keydown)="onMenuSearchKey($event)"
          />
        </div>
        @if (menuTarget().item; as item) {
          <button mat-menu-item (click)="openBindDialog(item)">
            🔗 {{ 'import_bind_existing' | t }}
          </button>
          @if (rowTemplate().has(item.sourceId)) {
            <button mat-menu-item (click)="resetRow(item)">
              ↺ {{ 'import_template_back_to_auto' | t }}
            </button>
          }
        }
        @if (!menuQuery()) {
          @for (sug of menuSuggestions(); track sug.id) {
            <button mat-menu-item class="suggested" (click)="pickTemplate(sug)">
              ★ {{ sug.name }} <span class="reason">{{ 'import_reason_' + sug.reason | t }}</span>
            </button>
          }
          @if (menuSuggestions().length) {
            <div class="menu-more">{{ 'import_all_templates' | t }}</div>
          }
        }
        @for (tpl of menuTemplates().shown; track tpl.id) {
          <button mat-menu-item (click)="pickTemplate(tpl)">{{ tpl.name }}</button>
        }
        @if (menuTemplates().more > 0) {
          <div class="menu-more">{{ 'import_more_templates' | t: menuTemplates().more }}</div>
        }
      </mat-menu>

      <ng-template #openRow let-item>
        <div class="row2" [class.off]="!checked().has(item.sourceId)">
          <div class="l1">
            <input
              type="checkbox"
              [checked]="checked().has(item.sourceId)"
              (change)="toggle(item.sourceId)"
            />
            <!-- the Dualis number opens the details (user 2026-10-07: "die Nummer linkbar") -->
            <app-staged-details-button
              [label]="item.unit ?? item.name"
              [sourceItemId]="item.sourceId"
              [changedSince]="item.changedSince"
            />
            <span class="name">{{ item.name }}</span>
            @if (topCandidate(item); as cand) {
              <button
                type="button"
                class="act suggestion"
                [title]="'import_bind_tooltip' | t"
                (click)="openBindDialog(item)"
              >
                🔗 {{ cand.name }} · {{ 'import_suggestion' | t }}
              </button>
            } @else if (templateFor(item); as tpl) {
              <button
                type="button"
                class="tpl-chip"
                [class.manual]="tpl.manual"
                [matMenuTriggerFor]="tplMenu"
                (menuOpened)="focusMenuSearch()"
                (click)="menuTarget.set({ item })"
              >
                {{ tpl.manual ? tpl.name + ' ↺' : tpl.name }}
              </button>
            } @else {
              <button
                type="button"
                class="tpl-chip missing"
                [matMenuTriggerFor]="tplMenu"
                (menuOpened)="focusMenuSearch()"
                (click)="menuTarget.set({ item })"
              >
                ⚠ {{ 'import_choose_template' | t }}
              </button>
            }
          </div>
          <div class="l2">{{ item.groupName }}</div>
        </div>
      </ng-template>
    </mat-dialog-content>

    <mat-dialog-actions>
      <button mat-dialog-close>{{ 'close' | t }}</button>
      @if (error(); as e) {
        <span class="pending error">{{ e }}</span>
      } @else {
        <span class="pending">{{ 'import_park_hint' | t }}</span>
      }
      <button class="primary" [disabled]="readyCount() === 0 || busy()" (click)="submit()">
        {{
          busy()
            ? ('import_running' | t)
            : ('import_create_selected' | t: readyCount() : checkedVisible())
        }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .row2 {
      padding: 4px 8px;
      border-bottom: 1px solid var(--mat-sys-outline-variant);
    }
    .row2.off {
      opacity: 0.6;
    }
    .row2 .l1 {
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .row2 .l1 .name {
      flex: 1;
      font-weight: 400;
    }
    .row2 .l2 {
      margin-left: 24px;
      font-size: 12px;
      color: var(--mat-sys-on-surface-variant);
    }
    .nr {
      font-family: monospace;
      font-size: 12px;
      cursor: copy;
    }
    .act {
      white-space: nowrap;
    }
    .act.quiet {
      opacity: 0.7;
    }
    .menu-search {
      padding: 4px 12px;
    }
    .menu-search input {
      width: 100%;
      box-sizing: border-box;
    }
    .suggested .reason {
      font-size: 11px;
      color: var(--mat-sys-on-surface-variant);
      margin-left: 6px;
    }
    .menu-more {
      padding: 4px 16px;
      font-size: 12px;
      color: var(--mat-sys-on-surface-variant);
    }
    .info {
      border: none;
      background: none;
      cursor: pointer;
      padding: 0 2px;
    }
    .details {
      padding: 8px 16px;
      font-size: 13px;
      max-width: 360px;
    }
    .tpl-chip {
      border-radius: 12px;
      padding: 1px 8px;
      font-size: 12px;
      white-space: nowrap;
    }
    .tpl-chip.manual {
      font-weight: 600;
    }
    .tpl-chip.missing {
      color: var(--mat-sys-error);
    }
    .kurs-filter {
      display: flex;
      flex-wrap: wrap;
      gap: 4px;
      padding: 0 24px 8px;
    }
    .kchip {
      border-radius: 12px;
      padding: 2px 10px;
      opacity: 0.5;
    }
    .kchip.on {
      opacity: 1;
    }
    /* group headers must read as headers (user 2026-10-07): tinted bar, bold, no button frame */
    button.section {
      width: 100%;
      text-align: left;
      border: none;
      background: var(--mat-sys-surface-container-high);
      font-weight: 600;
      font-size: 13px;
      padding: 6px 8px;
      cursor: pointer;
      color: var(--mat-sys-on-surface);
    }
    /* H5a :403 — group template chip in the header row, right-aligned above the row chips */
    .group-head {
      display: flex;
      align-items: center;
      gap: 8px;
      padding-right: 8px;
      background: var(--mat-sys-surface-container-high);
    }
    .select-all {
      display: flex;
      gap: 12px;
      margin: 0 0 4px;
    }
    .select-all .listall {
      border: none;
      background: none;
      padding: 0;
      font-size: 0.75rem;
      color: var(--mat-sys-primary, #3f51b5);
      cursor: pointer;
    }
    .group-check {
      margin-left: 8px;
    }
    .group-all {
      font-size: 12px;
      color: var(--mat-sys-on-surface-variant);
    }
    .group-head button.section {
      flex: 1;
      width: auto;
    }
    mat-dialog-actions .pending {
      flex: 1;
    }
    :host {
      display: block;
      width: min(560px, 92vw);
    }
    /* desktop first (user 2026-10-07): wide dialog, list scrolls inside up to ~85vh */
    @media (min-width: 720px) {
      :host {
        width: min(1040px, 92vw);
      }
      /* fixed height relative to the screen: the dialog keeps its size and position when the
         semester or the Kurs filter changes the list length (user 2026-10-07) */
      :host .list {
        height: calc(85vh - 300px);
        max-height: none;
      }
      /* exactly one scroll area (user 2026-10-07 "2 scrollbars"): on desktop the list scrolls,
         the dialog body does not */
      :host mat-dialog-content {
        max-height: none;
        overflow: hidden;
      }
    }
    @media (max-width: 719px) {
      /* on phones the dialog body scrolls and the list grows */
      :host .list {
        max-height: none;
        overflow: visible;
      }
    }
    .title-row {
      display: flex;
      align-items: center;
      gap: 8px;
      padding-right: 16px;
    }
    .title-row h2 {
      flex: 1;
    }
    .semester-step {
      display: flex;
      align-items: center;
    }
    .tabs {
      display: flex;
      gap: 4px;
      margin: 0 24px;
      border-bottom: 1px solid var(--mat-sys-outline-variant);
    }
    .tabs button {
      font: inherit;
      font-size: 13px;
      border: 0;
      background: none;
      color: inherit;
      padding: 6px 14px;
      border-radius: 6px 6px 0 0;
      cursor: pointer;
    }
    .tabs button.on {
      background: var(--mat-sys-secondary-container);
      font-weight: 600;
    }
    h4 {
      margin: 10px 0 4px;
      font-size: 11.5px;
      text-transform: uppercase;
      letter-spacing: 0.07em;
      color: var(--mat-sys-on-surface-variant);
    }
    input[type='search'] {
      width: 100%;
      font: inherit;
      font-size: 13px;
      padding: 7px 10px;
      border: 1px solid var(--mat-sys-outline-variant);
      border-radius: 6px;
      background: transparent;
      color: inherit;
      box-sizing: border-box;
    }
    .list {
      border: 1px solid var(--mat-sys-outline-variant);
      border-radius: 7px;
      margin-top: 8px;
      max-height: 340px;
      overflow: auto;
    }
    .tpl-clear {
      border: 0;
      background: none;
      color: inherit;
      cursor: pointer;
      padding: 0 8px;
    }
    .row.openable {
      cursor: pointer;
      user-select: none;
    }
    .full {
      display: block;
      font-size: 11px;
      color: var(--mat-sys-on-surface-variant);
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .bind-btn {
      font: inherit;
      font-size: 11.5px;
      border: 1px solid var(--mat-sys-outline-variant);
      background: none;
      color: var(--mat-sys-primary);
      border-radius: 10px;
      padding: 1px 8px;
      cursor: pointer;
      white-space: nowrap;
    }
    .nr {
      font-size: 11.5px;
      font-family: monospace;
      color: var(--mat-sys-on-surface-variant);
      white-space: nowrap;
    }
    .row.openable:hover {
      background: var(--mat-sys-surface-container-high);
    }
    .row {
      display: flex;
      align-items: center;
      gap: 9px;
      width: 100%;
      padding: 6px 10px;
      border: 0;
      border-bottom: 1px solid var(--mat-sys-outline-variant);
      background: none;
      color: inherit;
      font: inherit;
      font-size: 12.5px;
      text-align: left;
      cursor: pointer;
      box-sizing: border-box;
    }
    .row:last-child {
      border-bottom: 0;
    }
    .row.sel {
      background: var(--mat-sys-secondary-container);
    }
    .row.item.off {
      opacity: 0.45;
    }
    .section {
      display: flex;
      gap: 10px;
      padding: 5px 10px;
      font-size: 10.5px;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      color: var(--mat-sys-on-surface-variant);
      background: var(--mat-sys-surface-container);
      border-bottom: 1px solid var(--mat-sys-outline-variant);
    }
    .section-tpl {
      text-transform: none;
      letter-spacing: 0;
      margin-left: auto;
    }
    .name {
      flex: 0 1 auto;
      font-weight: 600;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .tag {
      font-size: 10.5px;
      font-weight: 600;
      border-radius: 9px;
      padding: 0 7px;
      white-space: nowrap;
    }
    .tag.new {
      background: var(--mat-sys-secondary-container);
      color: var(--mat-sys-on-secondary-container);
    }
    .tag.warn {
      background: var(--mat-sys-error-container);
      color: var(--mat-sys-on-error-container);
    }
    .note {
      display: block;
      font-weight: 400;
      font-size: 11px;
      color: var(--mat-sys-on-surface-variant);
    }
    .unit {
      margin-left: auto;
      font-size: 10.5px;
      color: var(--mat-sys-on-surface-variant);
      white-space: nowrap;
    }
    .ok {
      color: var(--mat-sys-primary);
    }
    .warn-ic {
      color: var(--mat-sys-error);
    }
    .tag.gone {
      background: var(--mat-sys-surface-variant);
      color: var(--mat-sys-on-surface-variant);
    }
    .empty {
      padding: 14px;
      color: var(--mat-sys-on-surface-variant);
      text-align: center;
    }
    .pending {
      font-size: 11.5px;
      color: var(--mat-sys-on-surface-variant);
      margin-right: auto;
    }
    .pending.error {
      color: var(--mat-sys-error);
    }
    button.primary {
      font: inherit;
      border: 0;
      border-radius: 6px;
      padding: 7px 16px;
      background: var(--mat-sys-primary);
      color: var(--mat-sys-on-primary);
    }
    button.primary[disabled] {
      opacity: 0.5;
    }
    mat-dialog-actions button[mat-dialog-close] {
      font: inherit;
      border: 1px solid var(--mat-sys-outline-variant);
      border-radius: 6px;
      padding: 6px 14px;
      background: none;
      color: inherit;
      cursor: pointer;
    }
  `,
})
export class ImportSyncDialogComponent {
  private readonly service = inject(ImportWorklistService);
  private readonly newOptions = inject(NewEventOptionsService);
  private readonly parked = inject(ParkedEventsService);
  private readonly viewState = inject(ViewStateStore);
  private readonly matDialog = inject(MatDialog);
  private readonly bindPick = inject(BindPickService);
  private readonly snackBar = inject(MatSnackBar);

  /** Bind proposals per OPEN item (server-ranked inside the CURRENT calendar
   *  window; empty ≠ "no match exists" — a wider window may find more). Loaded
   *  once per open-item set; the top candidate renders under the row. */
  readonly candidates = signal<Record<string, BindCandidate[]>>({});
  /** WP23x C3: lazily, only for rows of expanded groups, at most 20 queries in flight,
   *  cached per sourceId (a collapsed group asks nothing). */
  private readonly requested = new Set<string>();
  private candidateWindowKey = '';
  /** The candidate window follows the dialog's semester: the calendar week while it is the
   *  calendar's semester, else the whole stepped semester. A new window clears the cache
   *  (review round 18 M1). */
  private readonly candidateWindow = computed(() =>
    this.semester() === this.data.semester && this.viewState.window()
      ? this.viewState.window()
      : semesterRange(shiftSemester(this.semester(), 0)),
  );
  private readonly destroyRef = inject(DestroyRef);
  private readonly candidatesLoader = effect(() => {
    const w = this.candidateWindow();
    if (!w) return;
    const key = `${w.from}|${w.to}`;
    if (key !== this.candidateWindowKey) {
      this.candidateWindowKey = key;
      this.requested.clear();
      untracked(() => this.candidates.set({}));
    }
    const items = [
      ...(this.collapsed().has('lv') ? [] : this.lectures()),
      ...(this.collapsed().has('pr') ? [] : this.exams()),
    ].filter((i) => !this.requested.has(i.sourceId));
    if (items.length === 0) return;
    items.forEach((i) => this.requested.add(i.sourceId));
    untracked(() => {
      from(items)
        .pipe(
          mergeMap(
            (i) =>
              this.service
                .bindCandidates(i.sourceId, w.from, w.to)
                .pipe(map((c) => [i.sourceId, c] as const)),
            20,
          ),
          takeUntilDestroyed(this.destroyRef),
        )
        .subscribe(([id, c]) => {
          // a reply for an older window must not land in the new one
          if (this.candidateWindowKey === key)
            this.candidates.update((all) => ({ ...all, [id]: c }));
        });
    });
  });

  topCandidate(item: WorklistItem): BindCandidate | undefined {
    return this.candidates()[item.sourceId]?.[0];
  }

  /** THE one "verknüpfen" flow (Option B, user decision 2026-08-11): every bind
   *  from an open row goes through the BindTargetDialog — proposals + calendar
   *  pick + overwrite warning in ONE consistent step. Only the explicit
   *  calendar-pick choice closes the sync dialog. */
  openBindDialog(item: WorklistItem): void {
    this.matDialog
      .open(BindTargetDialogComponent, {
        data: {
          sourceLabel: item.name,
          candidates: this.candidates()[item.sourceId] ?? [],
        } satisfies BindTargetDialogData,
        autoFocus: false,
      })
      .afterClosed()
      .subscribe((result?: BindTargetResult) => {
        if (!result) return;
        if (result === 'pick') {
          this.bindPick.arm(item.sourceId, item.name);
          this.dialogRef.close();
          return;
        }
        this.service.bindStagedEvent(item.sourceId, result.reservationId).subscribe((ok) => {
          this.snackBar.open(
            ok
              ? tr('view_linked_with', item.name, result.targetName)
              : tr('view_link_failed', item.name),
            undefined,
            { duration: 4000 },
          );
        });
      });
  }

  private readonly dialogRef = inject(MatDialogRef<ImportSyncDialogComponent>);
  readonly data = inject<ImportSyncDialogData>(MAT_DIALOG_DATA);
  /** The dialog's one semester (H5a): starts at the calendar's, stepped with ‹ ›. */
  readonly semester = signal(this.data.semester);

  /** H5a addendum 2: only semesters with entries for the dialog's Kurse, plus the calendar's. */
  readonly semesters = computed(() => {
    const groups = new Set(this.activeGroupIds());
    const set = new Set<string>([this.data.semester, this.semester()]);
    for (const i of this.service.worklist()?.items ?? []) {
      if (i.semester && i.groupIds.some((g) => groups.has(g))) set.add(i.semester);
    }
    return [...set]
      .filter((s) => semesterIndex(s) >= 0)
      .sort((a, b) => semesterIndex(a) - semesterIndex(b));
  });

  canStep(delta: number): boolean {
    const list = this.semesters();
    const i = list.indexOf(this.semester()) + delta;
    return i >= 0 && i < list.length;
  }

  stepSemester(delta: number): void {
    const list = this.semesters();
    const target = list[list.indexOf(this.semester()) + delta];
    if (target) this.goSemester(target);
  }

  goSemester(label: string): void {
    this.showSemester(shiftSemester(label, 0));
  }

  private showSemester(date: Date): void {
    this.semester.set(currentSemester(date));
    // the linked-reservations query follows the dialog's semester
    this.service.windowRange.set(semesterRange(date));
    this.service.ensureLoaded().subscribe({ error: () => undefined });
  }

  /** H5a Kurs multi-filter: Kurse switched off in the header. */
  readonly kursOff = signal<Set<string>>(new Set());
  private readonly activeGroupIds = computed(() =>
    this.data.groupIds.filter((g) => !this.kursOff().has(g)),
  );
  readonly courseGroups = computed(() => {
    const names = new Map(
      (this.service.worklist()?.groups ?? []).map((g) => [g.id, g.name] as const),
    );
    return this.data.groupIds.map((id) => ({
      id,
      name: names.get(id) ?? this.data.groupNames?.[id] ?? id,
    }));
  });
  setKurs(id: string, selected: boolean): void {
    const next = new Set(this.kursOff());
    if (selected) next.delete(id);
    else next.add(id);
    this.kursOff.set(next);
  }
  toggleKurs(id: string): void {
    const next = new Set(this.kursOff());
    if (next.has(id)) next.delete(id);
    else next.add(id);
    this.kursOff.set(next);
  }

  /** Collapsed list groups: lv / pr / chg / lnk. */
  readonly collapsed = signal<Set<string>>(new Set());
  toggleGroup(key: string): void {
    const next = new Set(this.collapsed());
    if (next.has(key)) next.delete(key);
    else next.add(key);
    this.collapsed.set(next);
  }

  private readonly groupItems = computed(() =>
    (this.service.worklist()?.items ?? []).filter((i) =>
      i.groupIds.some((g) => this.activeGroupIds().includes(g)),
    ),
  );

  readonly openItems = computed(() =>
    // Worklist loads UNSCOPED (join safety) — the Offen tab narrows to the
    // dialog's semester here.
    openItemsOfGroups(this.service.worklist()?.items ?? [], this.activeGroupIds(), this.semester()),
  );
  readonly lectures = computed(() => this.openItems().filter((i) => i.kind === 'v'));
  readonly exams = computed(() => this.openItems().filter((i) => i.kind === 'p'));

  /** Verknüpft = stamped reservations (durable source), NOT Halde rows. */
  readonly linkedItems = computed(() =>
    linkedOfGroups(this.service.linked(), this.activeGroupIds()),
  );
  readonly changedLinked = computed(() => this.linkedItems().filter((e) => !!this.changedOf(e.id)));
  readonly unchangedLinked = computed(() =>
    this.linkedItems().filter((e) => !this.changedOf(e.id)),
  );

  /** H5a per-row template: a manual pick per item; otherwise the type default
   *  (the header field's choice or the server's auto template). */
  readonly rowTemplate = signal<Map<string, EventTemplate>>(new Map());
  readonly menuTarget = signal<{ item?: WorklistItem; group?: 'v' | 'p' }>({});

  /** Type-ahead in the template menu (user 2026-10-07: "Dropdown nicht suchbar"); only the
   *  first 50 matches render. */
  readonly menuQuery = signal('');
  /** Ranked suggestions per type, loaded once for the dialog's Kurse. */
  private readonly suggestions = signal<Record<'v' | 'p', TemplateSuggestion[]>>({ v: [], p: [] });
  readonly menuSuggestions = computed(() => {
    const target = this.menuTarget();
    const kind = target.item?.kind ?? target.group ?? 'v';
    return this.suggestions()[kind];
  });
  private readonly menuSearch = viewChild<ElementRef<HTMLInputElement>>('menuSearch');
  readonly menuTemplates = computed(() => {
    const q = this.menuQuery().trim().toLowerCase();
    const all = this.newOptions.templates();
    const hits = q ? all.filter((t) => t.name.toLowerCase().includes(q)) : all;
    return { shown: hits.slice(0, 50), more: Math.max(0, hits.length - 50) };
  });
  focusMenuSearch(): void {
    setTimeout(() => this.menuSearch()?.nativeElement.focus());
  }
  onMenuSearchKey(event: KeyboardEvent): void {
    if (event.key === 'Escape' && this.menuQuery()) {
      this.menuQuery.set('');
      event.preventDefault();
    }
    // keep typing in the field: the menu's own key handling would steal letters
    if (event.key !== 'Escape' && event.key !== 'Tab') event.stopPropagation();
  }

  private defaultTemplate(kind: 'v' | 'p'): { id: string; name: string } | null {
    const auto = this.autoTemplates();
    const id = kind === 'p' ? auto.examTemplateId : auto.lectureTemplateId;
    const name = kind === 'p' ? auto.examTemplateName : auto.lectureTemplateName;
    return id ? { id, name: name ?? id } : null;
  }

  templateFor(item: WorklistItem): { id: string; name: string; manual: boolean } | null {
    const manual = this.rowTemplate().get(item.sourceId);
    if (manual) return { id: manual.id, name: manual.name, manual: true };
    const d = this.defaultTemplate(item.kind);
    return d ? { ...d, manual: false } : null;
  }

  /** H5a addendum 3: the group chip acts on the CHECKED rows of the group only. */
  selectedIn(kind: 'v' | 'p'): WorklistItem[] {
    const ids = this.checked();
    return (kind === 'p' ? this.exams() : this.lectures()).filter((i) => ids.has(i.sourceId));
  }

  groupState(kind: 'v' | 'p'): 'none' | 'some' | 'all' {
    const total = (kind === 'p' ? this.exams() : this.lectures()).length;
    const n = this.selectedIn(kind).length;
    return n === 0 ? 'none' : n === total ? 'all' : 'some';
  }

  groupSelLabel(kind: 'v' | 'p'): string {
    return this.groupState(kind) === 'all'
      ? tr('import_group_all')
      : tr('import_group_selected', this.selectedIn(kind).length);
  }

  toggleGroupSelection(kind: 'v' | 'p'): void {
    const rows = kind === 'p' ? this.exams() : this.lectures();
    const next = new Set(this.checked());
    const all = this.groupState(kind) === 'all';
    for (const i of rows) {
      if (all) next.delete(i.sourceId);
      else next.add(i.sourceId);
    }
    this.checked.set(next);
  }

  readonly allChecked = computed(() => {
    const ids = this.checked();
    const open = this.openItems();
    return open.length > 0 && open.every((i) => ids.has(i.sourceId));
  });

  selectAll(): void {
    this.checked.set(new Set(this.openItems().map((i) => i.sourceId)));
  }

  selectNone(): void {
    this.checked.set(new Set());
  }

  groupChipLabel(kind: 'v' | 'p'): string {
    const rows = this.selectedIn(kind);
    const names = new Set(rows.map((i) => this.templateFor(i)?.name ?? ''));
    if (names.size > 1) return tr('import_template_mixed');
    const [only] = names;
    if (names.size === 1 && only) return only;
    const d = this.defaultTemplate(kind);
    return d ? d.name : tr('import_choose_template');
  }

  pickTemplate(tpl: EventTemplate): void {
    const target = this.menuTarget();
    const next = new Map(this.rowTemplate());
    if (target.item) next.set(target.item.sourceId, tpl);
    else if (target.group) {
      for (const i of this.selectedIn(target.group)) next.set(i.sourceId, tpl);
    }
    this.rowTemplate.set(next);
  }

  resetRow(item: WorklistItem): void {
    const next = new Map(this.rowTemplate());
    next.delete(item.sourceId);
    this.rowTemplate.set(next);
  }

  /** Checked rows that are still visible (Kurs filter + semester); review 20 M1. */
  readonly checkedVisible = computed(() => {
    const ids = this.checked();
    return this.openItems().filter((i) => ids.has(i.sourceId)).length;
  });

  /** "n von m bereit": checked items that have a template. */
  readonly readyCount = computed(() => {
    const ids = this.checked();
    return this.openItems().filter((i) => ids.has(i.sourceId) && this.templateFor(i) !== null)
      .length;
  });

  /** H3a "Übernehmen": confirm (names + overwrite warning), then applyStagedChanges. */
  applyChanges(e: LinkedEvent, ch: WorklistItem): void {
    this.matDialog
      .open(BindConfirmDialogComponent, {
        data: {
          sourceLabel: ch.name,
          targetName: e.name,
          note: tr('import_apply_note'),
          confirmLabel: tr('import_apply'),
          changedSince: ch.changedSince,
        },
      })
      .afterClosed()
      .subscribe((ok) => {
        if (!ok) return;
        this.service.applyStagedChanges(ch.sourceId).subscribe((outcome) => {
          if (outcome === 'refused') this.error.set(tr('import_apply_failed'));
          else if (outcome === 'error') this.error.set(tr('import_action_error'));
        });
      });
  }

  /** H3a "Verwerfen": clears the marker immediately, no confirmation. */
  dismiss(ch: WorklistItem): void {
    this.service.dismissStagedChanges([ch.sourceId]).subscribe((n) => {
      if (n === null) this.error.set(tr('import_action_error'));
    });
  }

  /** Worklist "geändert" rows keyed by their bound reservation id — the join
   *  that decorates the reservation-sourced Verknüpft rows. */
  private readonly changedByResId = computed(() => {
    const m = new Map<string, WorklistItem>();
    for (const i of changedItems(this.groupItems())) {
      if (i.boundReservationId) m.set(i.boundReservationId, i);
    }
    return m;
  });
  changedOf(reservationId: string): WorklistItem | undefined {
    return this.changedByResId().get(reservationId);
  }

  semesterOf(e: LinkedEvent): string {
    return currentSemester(new Date(e.firstDate));
  }

  readonly title = computed(() =>
    tr(
      'import_sync_title',
      this.service.sourceName() || tr('import_default_source'),
      this.data.groupLabel,
    ),
  );

  /** Server-resolved defaults per type (naming rules live in the deployment, PRD 104 v3). */
  private readonly autoTemplates = signal<DefaultTemplates>({});

  readonly checked = signal<Set<string>>(new Set());

  constructor() {
    // H5a addendum 1: nothing is preselected on open
    this.service.ensureLoaded().subscribe();
    this.newOptions.ensureLoaded().subscribe();
    this.service.defaultTemplates(this.data.groupIds).subscribe((t) => this.autoTemplates.set(t));
    for (const kind of ['v', 'p'] as const) {
      this.service
        .templateSuggestions(this.data.groupIds, kind)
        .subscribe((list) => this.suggestions.update((all) => ({ ...all, [kind]: list })));
    }
  }

  /** Single click = jump the calendar to the bound event's FIRST appointment
   *  (navigable week view); double click = edit sheet. The click waits 250 ms so
   *  a double click never also navigates (user rule 2026-08-11). */
  private clickTimer: ReturnType<typeof setTimeout> | null = null;

  jumpToEvent(e: LinkedEvent): void {
    if (this.clickTimer) clearTimeout(this.clickTimer);
    this.clickTimer = setTimeout(() => {
      this.clickTimer = null;
      const first = e.firstDate;
      if (!first) return;
      const monday = new Date(`${first.slice(0, 10)}T00:00:00Z`);
      monday.setUTCDate(monday.getUTCDate() - ((monday.getUTCDay() + 6) % 7));
      const from = `${monday.toISOString().slice(0, 10)}T00:00:00`;
      const w = this.viewState.window();
      const days = w
        ? Math.max(
            7,
            Math.round(
              (Date.parse(`${w.to.slice(0, 10)}T00:00:00Z`) -
                Date.parse(`${w.from.slice(0, 10)}T00:00:00Z`)) /
                86400000,
            ),
          )
        : 7;
      const toDate = new Date(monday.getTime() + days * 86400000);
      this.viewState.setRenderMode('week');
      this.viewState.setWindow({ from, to: `${toDate.toISOString().slice(0, 10)}T00:00:00` });
      this.dialogRef.close();
    }, 250);
  }

  editEvent(e: LinkedEvent): void {
    const id = e.id;
    if (this.clickTimer) {
      clearTimeout(this.clickTimer);
      this.clickTimer = null;
    }
    this.matDialog.open(EventSheetComponent, {
      data: { id } satisfies EventSheetDialogData,
      width: '960px',
      maxWidth: '95vw',
      height: '90vh',
    });
  }

  toggle(sourceId: string): void {
    const next = new Set(this.checked());
    if (!next.delete(sourceId)) next.add(sourceId);
    this.checked.set(next);
  }

  readonly busy = signal(false);
  readonly error = signal<string | null>(null);

  /** Create as a standard undoable command (PRD 094 rule: every mutating action).
   *  Inverse = delete the created reservations — the external-id stamp dies with
   *  them, so the derived binding reopens the staged items automatically. */
  submit(): void {
    const ids = [...this.checked()];
    if (ids.length === 0 || this.busy()) return;
    const auto = this.autoTemplates();
    const lectureId = auto.lectureTemplateId ?? null;
    const examId = auto.examTemplateId ?? null;
    // NOTHING is stored here (user rule 2026-08-11 "beim Parken soll noch gar
    // nichts gespeichert werden"): taking over only parks client-side. The
    // server-side create happens when a chip is DROPPED — that drop is the one
    // undoable "platziert" action; reload/Abbrechen just forgets the strip.
    const bySource = new Map(this.openItems().map((i) => [i.sourceId, i]));
    this.parked.add(
      ids
        .map((id) => bySource.get(id))
        .filter((i): i is WorklistItem => !!i)
        .map((i) => ({
          sourceId: i.sourceId,
          name: i.name,
          kind: i.kind,
          lectureTemplateId: i.kind === 'v' ? (this.templateFor(i)?.id ?? lectureId) : lectureId,
          examTemplateId: i.kind === 'p' ? (this.templateFor(i)?.id ?? examId) : examId,
          groups: i.groupIds.map((id) => ({ id, name: i.groupName })),
        })),
    );
    this.dialogRef.close('parked');
  }
}
