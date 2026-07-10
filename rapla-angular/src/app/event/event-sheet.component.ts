import { CommonModule } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  HostListener,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Location } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatTimepickerModule } from '@angular/material/timepicker';
import { Subject, catchError, debounceTime, of, switchMap, timeout } from 'rxjs';

import {
  ClassificationEditComponent,
  type ClassificationPatch,
} from '../classification/classification-edit.component';
import { EntityIdChipComponent } from '../common/entity-id-chip.component';
import { remapValues } from '../classification/classification-schema';
import { ClassificationSchemaService } from '../classification/classification-schema.service';
import { GraphqlService } from '../graphql/graphql.service';
import type { MutationIssue } from '../graphql/mutation-result';
import { AvailabilitySearchService, type AvailabilityRow } from './availability-search.service';
import { DraftHistory, type DraftContent } from './draft-history';
import { EventDataService } from './event-data.service';
import {
  generateAppointmentId,
  isDirty,
  newDraft,
  snapshot,
  withEnd,
  withStart,
  type DraftAllocation,
  type DraftAppointment,
  type EventDraft,
} from './event-draft';
import { OccurrencePreviewService, type OccurrenceRow } from './occurrence-preview.service';
import {
  RAPLA_WEEKDAYS_MONDAY_FIRST,
  defaultRule,
  endModeOf,
  ruleSummary,
  toggleException,
  toggleWeekday,
  withCount,
  withEndMode,
  withInterval,
  withUntil,
  type EndMode,
  type RepeatType,
} from './repeating-edit';

/**
 * PRD 091 Phase 2.3–2.6 — the event sheet (single route `/app/event/:id`).
 *
 * UI contract from the mockup/prototype round (all locked 2026-07-06):
 * compact header (type · name, click to expand), sections "Termine" and
 * "Ressourcen", active-section principle (only the focused section shows its
 * edit affordances), add mode without route change (ONE Auswählbar list with
 * pins stuck on top, closes only via Fertig/Esc/save), "gilt für" date picker
 * per allocation, sticky save bar. Recurrence editing via the per-row panel
 * (Phase 4.3, ↻ toggle — preview from `expandOccurrences`, click-to-skip
 * exceptions); unknown classification attributes pass through untouched (2.0b).
 */

interface CandidateVm extends AvailabilityRow {
  pinned: boolean;
}

/** Dialog-mode inputs — the editor is a dialog OVER the current view; the
 *  route form (/app/event/:id) stays as the deep-link only. */
export interface EventSheetDialogData {
  id: string;
  isNew?: boolean;
  draft?: EventDraft;
  /** Force read-only regardless of the caller's canModify ("Anzeigen", PRD 094). */
  readOnly?: boolean;
}

const CIRCLED = '①②③④⑤⑥⑦⑧⑨⑩';

/** PRD 099 — prototype values worth seeding: nulls stay OMITTED on the wire
 *  (explicit null means "clear" since the null-semantics fix). */
function nonNullEntries(values: Record<string, unknown>): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(values)) {
    if (v !== null && v !== undefined) out[k] = v;
  }
  return out;
}

@Component({
  selector: 'app-event-sheet',
  standalone: true,
  imports: [
    ClassificationEditComponent,
    CommonModule,
    EntityIdChipComponent,
    FormsModule,
    MatDatepickerModule,
    MatFormFieldModule,
    MatInputModule,
    MatTimepickerModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './event-sheet.component.html',
  styleUrl: './event-sheet.component.css',
  host: { '[class.in-dialog]': 'inDialog' },
})
export class EventSheetComponent {
  private readonly data = inject(EventDataService);
  private readonly availability = inject(AvailabilitySearchService);
  private readonly occurrences = inject(OccurrencePreviewService);
  private readonly classificationSchema = inject(ClassificationSchemaService);
  private readonly gql = inject(GraphqlService);
  private readonly location = inject(Location);
  private readonly destroyRef = inject(DestroyRef);
  private readonly dialogData = inject<EventSheetDialogData | null>(MAT_DIALOG_DATA, {
    optional: true,
  });
  private readonly dialogRef = inject<MatDialogRef<EventSheetComponent> | null>(MatDialogRef, {
    optional: true,
  });

  readonly inDialog = this.dialogRef !== null;

  /** Route param (withComponentInputBinding); dialog mode passes the id via data. */
  readonly id = input<string>('');

  private eventId(): string {
    return this.dialogData?.id ?? this.id();
  }

  readonly draft = signal<EventDraft | null>(null);
  readonly canModify = signal(!this.dialogData?.readOnly);
  readonly notFound = signal(false);
  readonly loading = signal(true);

  readonly headerOpen = signal(false);
  readonly active = signal<'header' | 'termine' | 'ressourcen' | null>(null);
  readonly addMode = signal(false);
  readonly searchText = signal('');
  readonly pins = signal<string[]>([]);
  readonly giltOpen = signal<string | null>(null);
  readonly editingAppointment = signal<string | null>(null);

  // PRD 091 Phase 4.3 — recurrence panel (one open at a time, per appointment).
  readonly repeatingOpen = signal<string | null>(null);
  readonly previewRows = signal<OccurrenceRow[]>([]);
  readonly weekdayOptions = RAPLA_WEEKDAYS_MONDAY_FIRST;

  readonly saving = signal(false);
  readonly concurrent = signal(false);
  readonly issues = signal<MutationIssue[]>([]);
  readonly notice = signal('');

  readonly typeOptions = signal<{ key: string; name: string }[]>([]);

  /** Title attributes of the current type (PRD 096 D5 revision —
   *  @editView(value: "title"), derived from the nameformat's direct attribute
   *  references; composites yield several). Rendered as prominent header
   *  fields in attribute order. Empty when the type has none: the header then
   *  shows no title fields and the classification component renders all
   *  attributes. */
  readonly titleAttrs = computed(() => {
    const d = this.draft();
    if (!d) return [];
    return (
      this.classificationSchema
        .typeMap()
        ?.get(d.typeKey)
        ?.attributes.filter((a) => a.editView === 'title') ?? []
    );
  });
  readonly titleExcludeKeys = computed(() => this.titleAttrs().map((t) => t.key));

  private baseline = '';
  readonly dirty = computed(() => {
    const d = this.draft();
    return d !== null && isDirty(d, this.baseline);
  });

  private typeSwitchToken = 0;

  // PRD 091 D5 — in-sheet undo/redo (memento stack, pre-save only).
  private readonly history = new DraftHistory();
  private readonly historyTick = signal(0);
  readonly canUndo = computed(() => this.historyTick() >= 0 && this.history.canUndo());
  readonly canRedo = computed(() => this.historyTick() >= 0 && this.history.canRedo());
  readonly undoLabel = computed(() => (this.historyTick() >= 0 ? this.history.undoLabel() : ''));
  readonly redoLabel = computed(() => (this.historyTick() >= 0 ? this.history.redoLabel() : ''));

  /** Statuses for assigned rows + pins, keyed by allocatable id. */
  readonly statusById = signal<Map<string, AvailabilityRow>>(new Map());
  /** Ranked search hits (excluding pinned + assigned, computed in the vm). */
  readonly hits = signal<AvailabilityRow[]>([]);

  readonly candidates = computed<CandidateVm[]>(() => {
    const d = this.draft();
    if (!d) return [];
    const assigned = new Set(d.allocations.map((a) => a.allocatableId));
    const pinned = this.pins()
      .filter((p) => !assigned.has(p))
      .map((p) => {
        const row = this.statusById().get(p);
        return row ? { ...row, pinned: true } : null;
      })
      .filter((r): r is CandidateVm => r !== null);
    const pinnedIds = new Set(pinned.map((p) => p.id));
    const rest = this.hits()
      .filter((h) => !assigned.has(h.id) && !pinnedIds.has(h.id))
      .map((h) => ({ ...h, pinned: false }));
    return [...pinned, ...rest];
  });

  private readonly refresh$ = new Subject<void>();
  private readonly preview$ = new Subject<void>();

  constructor() {
    this.refresh$
      .pipe(
        debounceTime(250),
        switchMap(() => {
          const d = this.draft();
          if (!d || !this.hasValidAppointments(d)) return [];
          const ids = [...new Set([...this.pins(), ...d.allocations.map((a) => a.allocatableId)])];
          return this.availability.search(
            d.appointments,
            this.addMode() ? this.searchText() : '',
            ids,
            d.persisted ? d.id : null,
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((result) => {
        this.statusById.set(result.byId);
        this.hits.set(result.hits);
      });
    // occurrence preview for the open recurrence panel — server-owned
    // expansion (Phase 4.1); re-fetched on every draft change like the
    // availability pills (current truth, never snapshot-time)
    this.preview$
      .pipe(
        debounceTime(250),
        switchMap(() => {
          const a = this.openRepeatingAppointment();
          if (!a?.repeating) return of([] as OccurrenceRow[]);
          return this.occurrences.expand(a);
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((rows) => this.previewRows.set(rows));
    this.loadTypes();
    // descriptors must be ready for setTypeKey's remap even if the header
    // (and with it the classification component) was never opened
    this.classificationSchema.load();
  }

  // eslint-disable-next-line @angular-eslint/use-lifecycle-interface
  ngOnInit(): void {
    const isNew = this.dialogData
      ? this.dialogData.isNew === true
      : history.state?.['isNew'] === true;
    if (isNew) {
      const typeKey = (history.state?.['typeKey'] as string | undefined) ?? 'event';
      // "Mehr Optionen" in the quick-create window carries its draft along.
      const carried =
        this.dialogData?.draft ?? (history.state?.['draft'] as EventDraft | undefined);
      const d =
        carried && carried.id === this.eventId()
          ? carried
          : newDraft(typeKey, new Date(), this.eventId());
      this.draft.set(d);
      this.baseline = snapshot(d);
      this.headerOpen.set(true);
      this.active.set('header');
      this.loading.set(false);
      this.refresh$.next();
      this.seedDefaults(d.typeKey);
      return;
    }
    this.data
      .load(this.eventId())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((loaded) => {
        this.loading.set(false);
        if (!loaded) {
          this.notFound.set(true);
          return;
        }
        this.draft.set(loaded.draft);
        this.canModify.set(loaded.canModify && !this.dialogData?.readOnly);
        this.baseline = snapshot(loaded.draft);
        this.refresh$.next();
      });
  }

  /**
   * PRD 099 Phase 4 — seed a NEW draft with the server-computed type defaults
   * (`reservationPrototype`). Applies only while the draft is still pristine
   * (no history, not dirty) so a fast-typing user is never overwritten; the
   * baseline includes the seeds (a fresh draft is NOT dirty). Fetch failed →
   * no seeds, the server still applies defaults at create.
   */
  private seedDefaults(typeKey: string): void {
    this.classificationSchema
      .prototype(typeKey)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((defaults) => {
        const d = this.draft();
        if (!defaults || !d || d.persisted || d.typeKey !== typeKey) return;
        if (this.history.canUndo() || this.dirty()) return;
        const seeds = nonNullEntries(defaults);
        if (Object.keys(seeds).length === 0) return;
        const seeded = { ...d, values: { ...seeds, ...d.values } };
        this.draft.set(seeded);
        this.baseline = snapshot(seeded);
      });
  }

  private loadTypes(): void {
    this.gql
      .query<{ types: { key: string; name: string; classificationType: string }[] }>(
        `query { types { key name classificationType } }`,
      )
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((resp) => {
        const all = resp.data?.types ?? [];
        this.typeOptions.set(
          all
            .filter((t) => t.classificationType === 'RESERVATION')
            .map((t) => ({ key: t.key, name: t.name })),
        );
      });
  }

  private hasValidAppointments(d: EventDraft): boolean {
    return d.appointments.length > 0 && d.appointments.every((a) => a.start < a.end);
  }

  // ------------------------------------------------------------ helpers

  circled(i: number): string {
    return CIRCLED.charAt(i) || `#${i + 1}`;
  }

  appointmentIndex(id: string): number {
    return this.draft()?.appointments.findIndex((a) => a.id === id) ?? -1;
  }

  giltLabel(al: DraftAllocation): string {
    if (!al.appointmentIds) return 'alle Termine';
    const d = this.draft();
    if (!d) return '';
    const marks = d.appointments
      .map((a, i) => (al.appointmentIds!.includes(a.id) ? this.circled(i) : null))
      .filter((m): m is string => m !== null);
    return marks.length ? `Termine ${marks.join(' ')}` : 'keine Termine';
  }

  statusFor(id: string): AvailabilityRow | undefined {
    return this.statusById().get(id);
  }

  conflictMarks(row: AvailabilityRow | undefined): string {
    if (!row) return '';
    return row.conflictingAppointmentIds
      .map((id) => this.circled(this.appointmentIndex(id)))
      .join(' ');
  }

  displayName(d: EventDraft): string {
    const joined = this.titleAttrs()
      .map((t) => {
        const v = d.values[t.key];
        if (typeof v !== 'string' || v.length === 0) return null;
        return t.enumValues?.find((e) => e.key === v)?.label ?? v;
      })
      .filter((v): v is string => v !== null)
      .join(' ');
    return joined.length > 0 ? joined : 'ohne Namen';
  }

  typeName(key: string): string {
    return this.typeOptions().find((t) => t.key === key)?.name ?? key;
  }

  isRepeating(a: DraftAppointment): boolean {
    return a.repeating !== null;
  }

  repeatingLabel(a: DraftAppointment): string {
    if (!a.repeating) return 'Einzeltermin';
    const names: Record<string, string> = {
      DAILY: 'täglich',
      WEEKLY: 'wöchentlich',
      MONTHLY: 'monatlich',
      YEARLY: 'jährlich',
    };
    return `Serie · ${names[a.repeating.type] ?? a.repeating.type}`;
  }

  // ------------------------------------------------------------ actions

  activate(section: 'header' | 'termine' | 'ressourcen'): void {
    if (!this.canModify()) return;
    this.active.set(section);
  }

  openHeader(): void {
    if (!this.canModify()) return;
    this.headerOpen.set(true);
    this.active.set('header');
  }

  private contentOf(d: EventDraft): DraftContent {
    return {
      typeKey: d.typeKey,
      values: d.values,
      appointments: d.appointments,
      allocations: d.allocations,
    };
  }

  /** THE single mutation funnel — every edit records its pre-state (D5). */
  private mutateDraft(
    fn: (d: EventDraft) => void,
    label = 'Änderung',
    coalesceKey: string | null = null,
  ): void {
    const d = this.draft();
    if (!d) return;
    this.history.push(this.contentOf(d), label, coalesceKey, Date.now());
    fn(d);
    this.draft.set({ ...d });
    this.historyTick.update((v) => v + 1);
    this.refresh$.next();
    this.preview$.next();
  }

  undo(): void {
    const d = this.draft();
    if (!d) return;
    this.applyRestored(this.history.undo(this.contentOf(d)));
  }

  redo(): void {
    const d = this.draft();
    if (!d) return;
    this.applyRestored(this.history.redo(this.contentOf(d)));
  }

  private applyRestored(restored: DraftContent | null): void {
    const d = this.draft();
    if (!restored || !d) return;
    this.draft.set({ ...d, ...restored });
    this.historyTick.update((v) => v + 1);
    // availability pills + occurrence preview re-fetch — always current
    // truth, never snapshot-time
    this.refresh$.next();
    this.preview$.next();
  }

  private resetHistory(): void {
    this.history.clear();
    this.historyTick.update((v) => v + 1);
  }

  setTitle(key: string, value: string): void {
    const t = this.titleAttrs().find((a) => a.key === key);
    if (!t) return;
    this.mutateDraft((d) => (d.values[t.key] = value), t.label, 'values:' + t.key);
  }

  /**
   * PRD 096 — type change with client-side attribute remapping (Swing
   * newClassificationFrom parity): same-key same-type values survive, the
   * rest is dropped — the target @oneOf input only knows its own fields.
   * Server accepts the switch since PRD 056 OQ1.c revision (2026-07-07).
   */
  setTypeKey(key: string): void {
    const current = this.draft();
    if (!current || key === current.typeKey) return;
    // PRD 099 Phase 4 — await the target type's prototype (cache or fetch)
    // BEFORE mutating so remap + default gap-fill stay ONE undoable step.
    // Latest-wins on rapid double-switches; timeout/error → remap-only.
    const token = ++this.typeSwitchToken;
    this.classificationSchema
      .prototype(key)
      .pipe(
        timeout({ first: 3000 }),
        catchError(() => of(null)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((defaults) => {
        if (token !== this.typeSwitchToken) return;
        this.mutateDraft((d) => {
          const map = this.classificationSchema.typeMap();
          const from = map?.get(d.typeKey)?.attributes ?? [];
          const to = map?.get(key)?.attributes;
          // schema not loaded yet → keep nothing (never invent keys the target
          // @oneOf variant might not have — PRD 096 C)
          d.values = to ? remapValues(d.values, from, to) : {};
          if (to && defaults) {
            const seeds = nonNullEntries(defaults);
            for (const [k, v] of Object.entries(seeds)) {
              if (!(k in d.values)) d.values[k] = v;
            }
          }
          d.typeKey = key;
        }, 'Veranstaltungstyp');
      });
  }

  applyClassificationPatch(p: ClassificationPatch): void {
    this.mutateDraft((d) => (d.values[p.key] = p.value), p.label, p.coalesceKey);
  }

  // Stable per-iso Date instances: a fresh `new Date(iso)` on every change
  // detection cycle re-triggers the pickers' [value] setter (new object
  // identity) and loops CD (NG0103) — same trap family as the valueChange echo.
  private readonly dateCache = new Map<string, Date>();

  asDate(iso: string): Date {
    let d = this.dateCache.get(iso);
    if (!d) {
      d = new Date(iso);
      this.dateCache.set(iso, d);
    }
    return d;
  }

  private isoFrom(datePart: Date, timePart: Date): string {
    const p = (n: number) => String(n).padStart(2, '0');
    return `${datePart.getFullYear()}-${p(datePart.getMonth() + 1)}-${p(datePart.getDate())}T${p(timePart.getHours())}:${p(timePart.getMinutes())}:00`;
  }

  // Equality guards required: mat-timepicker emits valueChange on programmatic
  // [value] writes too — patching unconditionally loops change detection (see
  // angular-frontend skill, Material widget traps).
  private applyTimes(
    id: string,
    times: { start: string; end: string },
    label: string,
    coalesceKey: string,
  ): void {
    this.mutateDraft(
      (d) => {
        const a = d.appointments.find((x) => x.id === id);
        if (a) {
          a.start = times.start;
          a.end = times.end;
        }
      },
      label,
      coalesceKey,
    );
  }

  setStartDate(a: DraftAppointment, v: Date | null): void {
    if (!v) return;
    const next = this.isoFrom(v, new Date(a.start));
    if (next !== a.start) this.applyTimes(a.id, withStart(a, next), 'Beginn', `appt:${a.id}:start`);
  }

  setStartTime(a: DraftAppointment, v: Date | null): void {
    if (!v) return;
    const next = this.isoFrom(new Date(a.start), v);
    if (next !== a.start) this.applyTimes(a.id, withStart(a, next), 'Beginn', `appt:${a.id}:start`);
  }

  setEndDate(a: DraftAppointment, v: Date | null): void {
    if (!v) return;
    const next = this.isoFrom(v, new Date(a.end));
    if (next !== a.end) this.applyEnd(a, withEnd(a, next));
  }

  setEndTime(a: DraftAppointment, v: Date | null): void {
    if (!v) return;
    const next = this.isoFrom(new Date(a.end), v);
    if (next !== a.end) this.applyEnd(a, withEnd(a, next));
  }

  // When the clamp rejects the user's pick WITHOUT changing the model, the
  // cached Date reference makes the [value] write a no-op and the widget
  // keeps displaying the rejected pick. Dropping the cache entries hands the
  // binding ONE fresh instance so the input reformats (on blur) — a one-shot
  // bump, not a per-cycle instance, so it cannot loop CD.
  private applyEnd(a: DraftAppointment, times: { start: string; end: string }): void {
    if (times.end === a.end) {
      this.dateCache.delete(a.start);
      this.dateCache.delete(a.end);
      const d = this.draft();
      if (d) this.draft.set({ ...d });
      return;
    }
    this.applyTimes(a.id, times, 'Ende', `appt:${a.id}:end`);
  }

  addAppointment(): void {
    this.mutateDraft((d) => {
      const last = d.appointments[d.appointments.length - 1];
      const shift = (iso: string): string => {
        const dt = new Date(iso);
        dt.setDate(dt.getDate() + 1);
        const p = (n: number) => String(n).padStart(2, '0');
        return `${dt.getFullYear()}-${p(dt.getMonth() + 1)}-${p(dt.getDate())}T${p(dt.getHours())}:${p(dt.getMinutes())}:00`;
      };
      d.appointments.push({
        id: generateAppointmentId(),
        start: last ? shift(last.start) : new Date().toISOString().slice(0, 17) + '00',
        end: last ? shift(last.end) : new Date().toISOString().slice(0, 17) + '00',
        allDay: false,
        repeating: null,
      });
    }, '+ Termin');
  }

  removeAppointment(id: string): void {
    this.mutateDraft((d) => {
      if (d.appointments.length < 2) return;
      d.appointments = d.appointments.filter((a) => a.id !== id);
      for (const al of d.allocations) {
        if (al.appointmentIds) {
          al.appointmentIds = al.appointmentIds.filter((x) => x !== id);
        }
      }
    }, 'Termin gelöscht');
    if (this.repeatingOpen() === id) this.repeatingOpen.set(null);
  }

  // ------------------------------------------------------------ recurrence (Phase 4.3)

  private openRepeatingAppointment(): DraftAppointment | null {
    const id = this.repeatingOpen();
    if (!id) return null;
    return this.draft()?.appointments.find((a) => a.id === id) ?? null;
  }

  toggleRepeatingPanel(a: DraftAppointment): void {
    if (!this.canModify()) return;
    this.repeatingOpen.update((cur) => (cur === a.id ? null : a.id));
    this.previewRows.set([]);
    this.preview$.next();
  }

  /** The rule-edit funnel: every transform runs on the draft's own appointment. */
  private mutateRule(
    id: string,
    fn: (a: DraftAppointment) => void,
    label: string,
    coalesceKey: string | null = null,
  ): void {
    this.mutateDraft(
      (d) => {
        const a = d.appointments.find((x) => x.id === id);
        if (a) fn(a);
      },
      label,
      coalesceKey,
    );
  }

  setRepeatingType(a: DraftAppointment, type: RepeatType | 'NONE'): void {
    this.mutateRule(
      a.id,
      (ap) => (ap.repeating = type === 'NONE' ? null : defaultRule(type, ap.start)),
      'Wiederholung',
    );
  }

  setRepInterval(a: DraftAppointment, value: string): void {
    this.mutateRule(
      a.id,
      (ap) => (ap.repeating = withInterval(ap.repeating!, Number(value))),
      'Intervall',
      `appt:${a.id}:rep:interval`,
    );
  }

  toggleRepWeekday(a: DraftAppointment, weekday: number): void {
    this.mutateRule(
      a.id,
      (ap) => (ap.repeating = toggleWeekday(ap.repeating!, weekday)),
      'Wochentage',
    );
  }

  setRepEndMode(a: DraftAppointment, mode: EndMode): void {
    this.mutateRule(
      a.id,
      (ap) => (ap.repeating = withEndMode(ap.repeating!, mode, ap.start)),
      'Serienende',
    );
  }

  setRepUntil(a: DraftAppointment, v: Date | null): void {
    if (!v) return;
    const p = (n: number) => String(n).padStart(2, '0');
    const day = `${v.getFullYear()}-${p(v.getMonth() + 1)}-${p(v.getDate())}`;
    this.mutateRule(
      a.id,
      (ap) => (ap.repeating = withUntil(ap.repeating!, day)),
      'Serienende',
      `appt:${a.id}:rep:until`,
    );
  }

  setRepCount(a: DraftAppointment, value: string): void {
    this.mutateRule(
      a.id,
      (ap) => (ap.repeating = withCount(ap.repeating!, Number(value))),
      'Serienende',
      `appt:${a.id}:rep:count`,
    );
  }

  /** The preview's click-to-skip gesture (UC-E4): toggle the occurrence's day. */
  toggleRepOccurrence(a: DraftAppointment, row: OccurrenceRow): void {
    this.mutateRule(
      a.id,
      (ap) => (ap.repeating = toggleException(ap.repeating!, row.start.slice(0, 10))),
      'Ausnahme',
    );
  }

  repSummary(a: DraftAppointment): string {
    return a.repeating ? ruleSummary(a.repeating, a.start) : '';
  }

  repEndMode(a: DraftAppointment): EndMode {
    return a.repeating ? endModeOf(a.repeating) : 'FOREVER';
  }

  untilDate(a: DraftAppointment): Date | null {
    return a.repeating?.end ? this.asDate(a.repeating.end + 'T00:00:00') : null;
  }

  isSkipped(a: DraftAppointment, row: OccurrenceRow): boolean {
    return row.exception || (a.repeating?.exceptions.includes(row.start.slice(0, 10)) ?? false);
  }

  openAddMode(): void {
    if (!this.canModify()) return;
    this.addMode.set(true);
    this.active.set('ressourcen');
    this.refresh$.next();
  }

  closeAddMode(): void {
    this.addMode.set(false);
    this.searchText.set('');
  }

  onSearch(value: string): void {
    this.searchText.set(value);
    this.refresh$.next();
  }

  togglePin(id: string): void {
    this.pins.update((p) => (p.includes(id) ? p.filter((x) => x !== id) : [...p, id]));
    this.refresh$.next();
  }

  assign(row: AvailabilityRow): void {
    this.mutateDraft((d) => {
      if (d.allocations.some((a) => a.allocatableId === row.id)) return;
      d.allocations.push({
        allocatableId: row.id,
        allocatableName: row.name,
        appointmentIds: null,
      });
    }, 'Ressource zugeordnet');
    this.pins.update((p) => p.filter((x) => x !== row.id));
  }

  unassign(id: string): void {
    this.mutateDraft((d) => {
      d.allocations = d.allocations.filter((a) => a.allocatableId !== id);
    }, 'Ressource entfernt');
    if (this.giltOpen() === id) this.giltOpen.set(null);
  }

  toggleGilt(id: string): void {
    this.giltOpen.update((cur) => (cur === id ? null : id));
  }

  setGiltMode(al: DraftAllocation, mode: 'all' | 'some'): void {
    this.mutateDraft(
      (d) => {
        const target = d.allocations.find((a) => a.allocatableId === al.allocatableId);
        if (!target) return;
        target.appointmentIds = mode === 'all' ? null : d.appointments.map((a) => a.id);
      },
      'gilt für',
      `gilt:${al.allocatableId}`,
    );
  }

  toggleGiltAppointment(al: DraftAllocation, appointmentId: string, checked: boolean): void {
    this.mutateDraft(
      (d) => {
        const target = d.allocations.find((a) => a.allocatableId === al.allocatableId);
        if (!target || !target.appointmentIds) return;
        target.appointmentIds = checked
          ? [...new Set([...target.appointmentIds, appointmentId])]
          : target.appointmentIds.filter((x) => x !== appointmentId);
      },
      'gilt für',
      `gilt:${al.allocatableId}`,
    );
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.addMode()) this.closeAddMode();
  }

  /**
   * D5 shortcuts — scoped to the sheet HOST (the sheet also runs as a dialog
   * over the main view and must not grab Ctrl+Z from underneath). Text inputs
   * keep the native browser undo; their edits re-enter the history via the
   * change handlers anyway.
   */
  @HostListener('keydown', ['$event'])
  onHistoryShortcut(e: KeyboardEvent): void {
    if (!this.canModify()) return;
    if (!(e.ctrlKey || e.metaKey)) return;
    const key = e.key.toLowerCase();
    if (key !== 'z' && key !== 'y') return;
    const target = e.target as HTMLElement;
    if (target instanceof HTMLTextAreaElement) return;
    if (
      target instanceof HTMLInputElement &&
      !['checkbox', 'radio', 'button'].includes(target.type)
    ) {
      return;
    }
    e.preventDefault();
    e.stopPropagation();
    if (key === 'y' || (key === 'z' && e.shiftKey)) this.redo();
    else this.undo();
  }

  save(overwrite = false): void {
    const d = this.draft();
    if (!d || this.saving()) return;
    if (overwrite) d.lastChanged = null;
    this.saving.set(true);
    this.issues.set([]);
    this.concurrent.set(false);
    this.data
      .save(d)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((result) => {
        this.saving.set(false);
        switch (result.kind) {
          case 'ok': {
            this.closeAddMode();
            this.resetHistory(); // D5 hard boundary — undo never crosses a save
            if (this.dialogRef) {
              this.dialogRef.close('saved');
              break;
            }
            this.notice.set('Gespeichert.');
            // Reload — fresh lastChanged + server-normalized state.
            this.data
              .load(d.id)
              .pipe(takeUntilDestroyed(this.destroyRef))
              .subscribe((loaded) => {
                if (loaded) {
                  this.draft.set(loaded.draft);
                  this.canModify.set(loaded.canModify);
                  this.baseline = snapshot(loaded.draft);
                }
              });
            break;
          }
          case 'concurrent':
            this.concurrent.set(true);
            break;
          case 'invalid': {
            // PRD 056 §9 retry contract: ID_COLLISION on the draft's own id
            // after a create means "already applied".
            if (!d.persisted && result.issues.some((i) => i.code === 'ID_COLLISION')) {
              this.notice.set('Bereits gespeichert (Wiederholung erkannt).');
              this.reloadAsPersisted(d.id);
            } else {
              this.issues.set(result.issues);
            }
            break;
          }
          case 'denied':
            this.issues.set(result.issues);
            break;
          case 'transport':
            this.issues.set([{ code: 'TRANSPORT', path: '', message: result.message }]);
            break;
        }
      });
  }

  private reloadAsPersisted(id: string): void {
    this.data
      .load(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((loaded) => {
        if (loaded) {
          this.draft.set(loaded.draft);
          this.baseline = snapshot(loaded.draft);
          this.resetHistory();
        }
      });
  }

  reloadDiscarding(): void {
    this.concurrent.set(false);
    this.loading.set(true);
    this.resetHistory();
    this.data
      .load(this.eventId())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((loaded) => {
        this.loading.set(false);
        if (loaded) {
          this.draft.set(loaded.draft);
          this.canModify.set(loaded.canModify);
          this.baseline = snapshot(loaded.draft);
          this.refresh$.next();
        }
      });
  }

  cancel(): void {
    if (this.dialogRef) {
      this.dialogRef.close();
      return;
    }
    this.location.back();
  }
}
