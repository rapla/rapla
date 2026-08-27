import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import {
  MAT_DIALOG_DATA,
  MatDialog,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { forkJoin, map } from 'rxjs';
import { MatIconModule } from '@angular/material/icon';

import { ImportWorklistService } from './import-worklist.service';
import { ParkedEventsService } from './parked-events.service';
import { BindPickService } from './bind-pick.service';
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
  type BindCandidate,
  type DefaultTemplates,
  type LinkedEvent,
  type WorklistItem,
} from './import-models';
import { EventSheetComponent, type EventSheetDialogData } from '../event/event-sheet.component';
import { ViewStateStore } from '../state/view-state-store';
import { NewEventOptionsService, type EventTemplate } from '../event/new-event-options.service';

export interface ImportSyncDialogData {
  groupIds: string[];
  groupLabel: string;
  semester: string;
  /** ISO LocalDateTime where created events materialize (v1 placement: visible week). */
  defaultStart: string;
}

type SyncTab = 'open' | 'linked';

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
    MatAutocompleteModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
  ],
  template: `
    <h2 mat-dialog-title>{{ title() }}</h2>
    <div class="sub">{{ data.semester }} · aus der aktuellen Auswahl</div>

    <div class="tabs">
      <button [class.on]="tab() === 'open'" (click)="tab.set('open')">
        Offen ({{ openItems().length }})
      </button>
      <button [class.on]="tab() === 'linked'" (click)="tab.set('linked')">
        Verknüpft ({{ linkedItems().length }})
      </button>
    </div>

    <mat-dialog-content>
      @switch (tab()) {
        @case ('open') {
          <mat-form-field class="tpl-select" appearance="outline" subscriptSizing="dynamic">
            <mat-label>Vorlage (leer = automatisch)</mat-label>
            <input
              matInput
              placeholder="Vorlage suchen…"
              [value]="templateQuery()"
              (input)="templateQuery.set($any($event.target).value)"
              [matAutocomplete]="tplAuto"
            />
            @if (selectedTemplate() || templateQuery()) {
              <button
                matSuffix
                type="button"
                class="tpl-clear"
                aria-label="Vorlagenwahl zurücksetzen"
                (click)="clearTemplate()"
              >
                ✕
              </button>
            }
            <mat-autocomplete
              #tplAuto="matAutocomplete"
              [displayWith]="templateName"
              (optionSelected)="onTemplatePicked($event.option.value)"
            >
              @for (t of templates(); track t.id) {
                <mat-option [value]="t">{{ t.name }}</mat-option>
              } @empty {
                <mat-option disabled>Keine Vorlage gefunden</mat-option>
              }
            </mat-autocomplete>
          </mat-form-field>

          <div class="list">
            <div class="section">
              Veranstaltungen ({{ lectures().length }})
              <span class="section-tpl">Vorlage: {{ lectureTemplateLabel() }}</span>
            </div>
            @for (item of lectures(); track item.sourceId) {
              <label class="row item" [class.off]="!checked().has(item.sourceId)">
                <input
                  type="checkbox"
                  [checked]="checked().has(item.sourceId)"
                  (change)="toggle(item.sourceId)"
                />
                <span class="name">
                  {{ item.name }}
                  @if (item.fullName && item.fullName !== item.name) {
                    <span class="full">{{ item.fullName }}</span>
                  }
                </span>
                <span class="tag new">neu</span>
                <button
                  type="button"
                  class="bind-btn"
                  title="Mit bestehender Veranstaltung verknüpfen (Vorschläge oder Kalender-Auswahl)"
                  (click)="openBindDialog(item); $event.preventDefault()"
                >
                  verknüpfen…
                </button>
                <span class="unit">{{ item.unit }}</span>
              </label>
            }
            <div class="section">
              Prüfungen ({{ exams().length }})
              <span class="section-tpl">Vorlage: {{ examTemplateLabel() }}</span>
            </div>
            @for (item of exams(); track item.sourceId) {
              <label class="row item" [class.off]="!checked().has(item.sourceId)">
                <input
                  type="checkbox"
                  [checked]="checked().has(item.sourceId)"
                  (change)="toggle(item.sourceId)"
                />
                <span class="name">
                  {{ item.name }}
                  @if (item.fullName && item.fullName !== item.name) {
                    <span class="full">{{ item.fullName }}</span>
                  }
                </span>
                <span class="tag new">neu</span>
                <button
                  type="button"
                  class="bind-btn"
                  title="Mit bestehender Veranstaltung verknüpfen (Vorschläge oder Kalender-Auswahl)"
                  (click)="openBindDialog(item); $event.preventDefault()"
                >
                  verknüpfen…
                </button>
                <span class="unit">{{ item.unit }}</span>
              </label>
            }
          </div>
        }
        @case ('linked') {
          <div class="list">
            @for (e of linkedItems(); track e.id) {
              <div
                class="row item openable"
                title="Klick: zum ersten Termin springen · Doppelklick: bearbeiten"
                role="button"
                tabindex="0"
                (click)="jumpToEvent(e)"
                (dblclick)="editEvent(e)"
                (keydown.enter)="jumpToEvent(e)"
              >
                @if (changedOf(e.id); as ch) {
                  <mat-icon class="warn-ic" inline>warning</mat-icon>
                } @else {
                  <mat-icon class="ok" inline>check</mat-icon>
                }
                <span class="name">
                  {{ e.name }}
                  @if (changedOf(e.id); as ch) {
                    <span class="tag warn">geändert</span>
                    <span class="note">geändert seit {{ ch.changedSince }}</span>
                  } @else if (isGone(e.id)) {
                    <span class="tag gone">nicht mehr im Export</span>
                  }
                </span>
                <span class="unit">{{ semesterOf(e) }}</span>
              </div>
            } @empty {
              <p class="empty">Noch nichts verknüpft.</p>
            }
          </div>
        }
      }
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      @if (tab() === 'open') {
        @if (error(); as e) {
          <span class="pending error">{{ e }}</span>
        } @else {
          <span class="pending"
            >Übernehmen parkt nur — gespeichert wird erst beim Platzieren im Kalender</span
          >
        }
        <button mat-dialog-close>Schließen</button>
        <button class="primary" [disabled]="checked().size === 0 || busy()" (click)="submit()">
          {{ busy() ? 'läuft…' : checked().size + ' übernehmen' }}
        </button>
      } @else {
        <button mat-dialog-close>Schließen</button>
      }
    </mat-dialog-actions>
  `,
  styles: `
    :host {
      display: block;
      width: min(560px, 92vw);
    }
    .sub {
      color: var(--mat-sys-on-surface-variant);
      font-size: 12.5px;
      margin: -8px 24px 8px;
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
    .tpl-select {
      width: 100%;
      margin-top: 4px;
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
  private candidatesKey = '';
  private readonly candidatesLoader = effect(() => {
    const items = this.openItems();
    const key = items.map((i) => i.sourceId).join(',');
    if (!key || key === this.candidatesKey) return;
    this.candidatesKey = key;
    const w = this.viewState.window();
    if (!w) return;
    untracked(() => {
      forkJoin(
        items.map((i) =>
          this.service
            .bindCandidates(i.sourceId, w.from, w.to)
            .pipe(map((c) => [i.sourceId, c] as const)),
        ),
      ).subscribe((pairs) => this.candidates.set(Object.fromEntries(pairs)));
    });
  });

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
              ? `„${item.name}" mit „${result.targetName}" verknüpft`
              : `„${item.name}" konnte nicht verknüpft werden`,
            undefined,
            { duration: 4000 },
          );
        });
      });
  }

  private readonly dialogRef = inject(MatDialogRef<ImportSyncDialogComponent>);
  readonly data = inject<ImportSyncDialogData>(MAT_DIALOG_DATA);

  readonly tab = signal<SyncTab>('open');
  readonly templateQuery = signal('');
  readonly selectedTemplate = signal<EventTemplate | null>(null);

  private readonly groupItems = computed(() =>
    (this.service.worklist()?.items ?? []).filter((i) =>
      i.groupIds.some((g) => this.data.groupIds.includes(g)),
    ),
  );

  readonly openItems = computed(() =>
    // Worklist loads UNSCOPED (join safety) — the Offen tab narrows to the
    // dialog's semester here.
    openItemsOfGroups(this.service.worklist()?.items ?? [], this.data.groupIds, this.data.semester),
  );
  readonly lectures = computed(() => this.openItems().filter((i) => i.kind === 'v'));
  readonly exams = computed(() => this.openItems().filter((i) => i.kind === 'p'));

  /** Verknüpft = stamped reservations (durable source), NOT Halde rows. */
  readonly linkedItems = computed(() => linkedOfGroups(this.service.linked(), this.data.groupIds));

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

  /** Bound reservation ids the Halde still knows. A stamped reservation OUTSIDE
   *  this set is "nicht mehr im Export" — deliberately neutral wording: absence
   *  from the export window is NOT a cancellation signal (Verwaist removal,
   *  2026-08-11). Only meaningful when the worklist actually loaded. */
  private readonly haldeBoundIds = computed(() => {
    const items = this.service.worklist()?.items ?? [];
    return new Set(items.map((i) => i.boundReservationId).filter((x): x is string => !!x));
  });
  isGone(reservationId: string): boolean {
    return this.service.worklist() !== null && !this.haldeBoundIds().has(reservationId);
  }

  semesterOf(e: LinkedEvent): string {
    return currentSemester(new Date(e.firstDate));
  }

  readonly title = computed(
    () => `${this.service.sourceName() || 'Import'}-Sync — ${this.data.groupLabel}`,
  );

  readonly templates = computed(() => {
    const q = this.templateQuery().trim().toLowerCase();
    const all = this.newOptions.templates();
    return (q ? all.filter((t) => t.name.toLowerCase().includes(q)) : all).slice(0, 50);
  });

  /** Server-resolved defaults per type (naming rules live in the deployment, PRD 104 v3). */
  private readonly autoTemplates = signal<DefaultTemplates>({});

  readonly lectureTemplateLabel = computed(() => {
    const sel = this.selectedTemplate();
    if (sel && !sel.name.toLowerCase().includes('pruefung')) return sel.name;
    const auto = this.autoTemplates();
    return auto.lectureTemplateId
      ? `${auto.lectureTemplateName} (automatisch)`
      : 'ohne Vorlage (keine passende gefunden)';
  });
  readonly examTemplateLabel = computed(() => {
    const sel = this.selectedTemplate();
    if (sel && sel.name.toLowerCase().includes('pruefung')) return sel.name;
    const auto = this.autoTemplates();
    return auto.examTemplateId
      ? `${auto.examTemplateName} (automatisch)`
      : 'ohne Vorlage (keine passende gefunden)';
  });

  readonly checked = signal<Set<string>>(new Set());

  constructor() {
    this.service.ensureLoaded().subscribe(() => {
      this.checked.set(new Set(this.openItems().map((i) => i.sourceId)));
    });
    this.newOptions.ensureLoaded().subscribe();
    this.service.defaultTemplates(this.data.groupIds).subscribe((t) => this.autoTemplates.set(t));
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

  readonly templateName = (t: EventTemplate | null): string => t?.name ?? '';

  onTemplatePicked(t: EventTemplate): void {
    this.selectedTemplate.set(t);
    this.templateQuery.set(t.name);
  }

  clearTemplate(): void {
    this.selectedTemplate.set(null);
    this.templateQuery.set('');
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
    const sel = this.selectedTemplate();
    const isExamTpl = !!sel && sel.name.toLowerCase().includes('pruefung');
    const auto = this.autoTemplates();
    const lectureId = sel && !isExamTpl ? sel.id : (auto.lectureTemplateId ?? null);
    const examId = sel && isExamTpl ? sel.id : (auto.examTemplateId ?? null);
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
          lectureTemplateId: lectureId,
          examTemplateId: examId,
          groups: i.groupIds.map((id) => ({ id, name: i.groupName })),
        })),
    );
    this.dialogRef.close('parked');
  }
}
