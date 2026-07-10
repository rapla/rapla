import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, signal } from '@angular/core';
import { provideNativeDateAdapter } from '@angular/material/core';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatRadioModule } from '@angular/material/radio';
import { MatSelectModule } from '@angular/material/select';
import { MatTimepickerModule } from '@angular/material/timepicker';

/**
 * THROWAWAY PROTOTYPE — PRD 091 Phase 4.0 recurrence editor exploration
 * (Swing AppointmentController → SPA migration). Not wired to any service;
 * delete together with the proto/ folder once the design is locked.
 *
 * Purpose: validate the panel UX — type select, interval stepper, weekday
 * chips, end-mode radio, derived summary, occurrence preview with
 * click-to-skip exceptions (the UC-E4 gesture).
 *
 * The occurrence expansion below is PROTOTYPE-ONLY client TS. Production
 * preview comes from the server (PRD 091 Phase 4.1 / OQ6) — the MONTHLY
 * "weekday-in-nth-week" semantic stays server-owned (AGENTS.md §13 scar).
 */

type RepeatType = 'NONE' | 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'YEARLY';
type EndMode = 'FOREVER' | 'UNTIL' | 'COUNT';

interface ProtoRule {
  type: RepeatType;
  interval: number;
  endMode: EndMode;
  until: Date | null;
  count: number;
  weekdays: number[]; // 0=Mo .. 6=So (display convention; wire mapping is 4.2's job)
  exceptions: string[]; // yyyy-MM-dd day keys
}

interface PreviewRow {
  start: Date;
  end: Date;
  skipped: boolean;
}

const DAY_MS = 86400e3;
const WEEKDAY_NAMES = ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So'];
const PREVIEW_CAP = 30;

function dayKey(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/** Monday-based weekday index 0..6. */
function weekdayIndex(d: Date): number {
  return (d.getDay() + 6) % 7;
}

@Component({
  selector: 'app-repeating-proto',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [provideNativeDateAdapter()],
  imports: [
    DatePipe,
    MatButtonModule,
    MatButtonToggleModule,
    MatDatepickerModule,
    MatFormFieldModule,
    MatInputModule,
    MatRadioModule,
    MatSelectModule,
    MatTimepickerModule,
  ],
  styles: `
    :host {
      display: block;
      padding: 16px;
      max-width: 760px;
    }
    .hint {
      color: #5c6774;
      font-size: 12px;
      margin: 0 0 12px;
    }
    .sec {
      background: #fff;
      border: 1px solid #d9dee6;
      border-radius: 10px;
      padding: 14px 16px;
      margin-bottom: 12px;
    }
    .sec-title {
      font-weight: 600;
      margin-bottom: 10px;
    }
    .row {
      display: flex;
      align-items: baseline;
      gap: 8px;
      flex-wrap: wrap;
    }
    .row.center {
      align-items: center;
    }
    .dash {
      color: #5c6774;
      align-self: center;
    }
    mat-form-field.date {
      width: 150px;
    }
    mat-form-field.time {
      width: 130px;
    }
    mat-form-field.rep-type {
      width: 170px;
    }
    mat-form-field.num {
      width: 90px;
    }
    .weekdays {
      display: flex;
      gap: 4px;
      margin: 6px 0 2px;
    }
    .wd {
      border: 1px solid #d9dee6;
      border-radius: 99px;
      background: #fff;
      width: 38px;
      height: 30px;
      cursor: pointer;
      font: inherit;
      font-size: 12px;
    }
    .wd.on {
      background: #1a73e8;
      border-color: #1a73e8;
      color: #fff;
    }
    .wd-error {
      color: #d93025;
      font-size: 12px;
      margin: 4px 0 0;
    }
    .summary {
      background: #eef1f5;
      border-radius: 8px;
      padding: 8px 12px;
      font-size: 13px;
      margin-top: 10px;
    }
    .end-line {
      display: flex;
      align-items: center;
      gap: 8px;
      min-height: 56px;
    }
    .preview {
      display: flex;
      flex-direction: column;
    }
    .occ {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 5px 8px;
      border-bottom: 1px dashed #edf0f4;
      border-radius: 6px;
      background: none;
      border-left: none;
      border-right: none;
      border-top: none;
      font: inherit;
      text-align: left;
      cursor: pointer;
      width: 100%;
    }
    .occ:hover {
      background: #f2f6fd;
    }
    .occ .no {
      color: #5c6774;
      width: 26px;
      font-size: 12px;
    }
    .occ.skipped .when {
      text-decoration: line-through;
      color: #9aa4b0;
    }
    .occ .tag {
      margin-left: auto;
      font-size: 11px;
      border-radius: 99px;
      padding: 1px 8px;
    }
    .occ .tag.skip {
      background: #fce8e6;
      color: #d93025;
    }
    .occ .tag.hover-only {
      visibility: hidden;
      background: #eef1f5;
      color: #5c6774;
    }
    .occ:hover .tag.hover-only {
      visibility: visible;
    }
    .more {
      color: #5c6774;
      font-size: 12px;
      padding: 8px;
    }
    .badge {
      background: #d93025;
      color: #fff;
      border-radius: 99px;
      font-size: 11px;
      padding: 0 7px;
      margin-left: 6px;
    }
  `,
  template: `
    <h2>Wiederholung — Prototyp (PRD 091 Phase 4.0)</h2>
    <p class="hint">
      Swing-Parität: Typ / Intervall / Wochentage / Ende (nie · Datum · N-mal) / Ausnahmen. Klick
      auf einen Vorschau-Termin überspringt ihn (Ausnahme, UC-E4). Monats-/Jahresmuster leiten sich
      aus dem Starttermin ab — wie im Kernmodell.
    </p>

    <div class="sec">
      <div class="sec-title">Termin</div>
      <div class="row">
        <mat-form-field class="date" appearance="outline">
          <mat-label>Beginn</mat-label>
          <input
            matInput
            [matDatepicker]="sd"
            [value]="start()"
            (dateChange)="setStartDate($event.value)"
          />
          <mat-datepicker-toggle matSuffix [for]="sd" />
          <mat-datepicker #sd />
        </mat-form-field>
        <mat-form-field class="time" appearance="outline">
          <input
            matInput
            [matTimepicker]="st"
            [value]="start()"
            (valueChange)="setStartTime($event)"
          />
          <mat-timepicker-toggle matSuffix [for]="st" />
          <mat-timepicker #st interval="15m" />
        </mat-form-field>
        <span class="dash">–</span>
        <mat-form-field class="time" appearance="outline">
          <input matInput [matTimepicker]="et" [value]="end()" (valueChange)="setEndTime($event)" />
          <mat-timepicker-toggle matSuffix [for]="et" />
          <mat-timepicker #et interval="15m" />
        </mat-form-field>
      </div>
    </div>

    <div class="sec">
      <div class="sec-title">
        Wiederholung
        @if (rule().exceptions.length) {
          <span class="badge">{{ rule().exceptions.length }} Ausnahmen</span>
        }
      </div>
      <div class="row center">
        <mat-form-field class="rep-type" appearance="outline">
          <mat-label>Wiederholt sich</mat-label>
          <mat-select [value]="rule().type" (valueChange)="setType($event)">
            <mat-option value="NONE">nie (Einzeltermin)</mat-option>
            <mat-option value="DAILY">täglich</mat-option>
            <mat-option value="WEEKLY">wöchentlich</mat-option>
            <mat-option value="MONTHLY">monatlich</mat-option>
            <mat-option value="YEARLY">jährlich</mat-option>
          </mat-select>
        </mat-form-field>
        @if (rule().type !== 'NONE') {
          <span>alle</span>
          <mat-form-field class="num" appearance="outline">
            <input
              matInput
              type="number"
              min="1"
              [value]="rule().interval"
              (input)="setInterval($any($event.target).value)"
            />
          </mat-form-field>
          <span>{{ intervalUnit() }}</span>
        }
      </div>

      @if (rule().type === 'WEEKLY') {
        <div class="weekdays">
          @for (name of weekdayNames; track name; let i = $index) {
            <button
              type="button"
              class="wd"
              [class.on]="rule().weekdays.includes(i)"
              (click)="toggleWeekday(i)"
            >
              {{ name }}
            </button>
          }
        </div>
        @if (!rule().weekdays.length) {
          <p class="wd-error">Mindestens ein Wochentag — sonst hat die Serie keine Termine.</p>
        }
      }

      @if (rule().type !== 'NONE') {
        <mat-radio-group [value]="rule().endMode" (change)="setEndMode($event.value)">
          <div class="end-line">
            <mat-radio-button value="FOREVER">endet nie</mat-radio-button>
          </div>
          <div class="end-line">
            <mat-radio-button value="UNTIL">endet am</mat-radio-button>
            <mat-form-field class="date" appearance="outline">
              <input
                matInput
                [matDatepicker]="ud"
                [value]="rule().until"
                [disabled]="rule().endMode !== 'UNTIL'"
                (dateChange)="setUntil($event.value)"
              />
              <mat-datepicker-toggle matSuffix [for]="ud" />
              <mat-datepicker #ud />
            </mat-form-field>
          </div>
          <div class="end-line">
            <mat-radio-button value="COUNT">endet nach</mat-radio-button>
            <mat-form-field class="num" appearance="outline">
              <input
                matInput
                type="number"
                min="1"
                [value]="rule().count"
                [disabled]="rule().endMode !== 'COUNT'"
                (input)="setCount($any($event.target).value)"
              />
            </mat-form-field>
            <span>Terminen</span>
          </div>
        </mat-radio-group>

        <div class="summary">{{ summary() }}</div>
      }
    </div>

    @if (rule().type !== 'NONE') {
      <div class="sec">
        <div class="sec-title">Vorschau</div>
        <div class="preview">
          @for (occ of preview(); track occ.start.getTime(); let i = $index) {
            <button
              type="button"
              class="occ"
              [class.skipped]="occ.skipped"
              (click)="toggleException(occ)"
            >
              <span class="no">{{ i + 1 }}.</span>
              <span class="when"
                >{{ occ.start | date: 'EE dd.MM.yyyy' }} · {{ occ.start | date: 'HH:mm' }}–{{
                  occ.end | date: 'HH:mm'
                }}</span
              >
              @if (occ.skipped) {
                <span class="tag skip">übersprungen — Klick stellt wieder her</span>
              } @else {
                <span class="tag hover-only">Klick: überspringen</span>
              }
            </button>
          }
          @if (truncated()) {
            <div class="more">
              … Vorschau auf {{ previewCap }} Termine gekürzt (Serie läuft weiter).
            </div>
          }
          @if (!preview().length) {
            <div class="more">Keine Termine — Regel ergibt keine Vorkommen.</div>
          }
        </div>
        <div class="row" style="margin-top: 8px">
          <button
            mat-stroked-button
            [disabled]="rule().endMode === 'FOREVER'"
            (click)="splitInfo.set(true)"
          >
            In Einzeltermine umwandeln
          </button>
          @if (rule().endMode === 'FOREVER') {
            <span class="hint" style="align-self: center"
              >nur für endliche Serien (Swing-Regel)</span
            >
          }
          @if (splitInfo()) {
            <span class="hint" style="align-self: center">
              → würde {{ activeCount() }} Einzeltermine erzeugen (Mock, Phase 4.6)
            </span>
          }
        </div>
      </div>
    }
  `,
})
export class RepeatingProtoComponent {
  readonly weekdayNames = WEEKDAY_NAMES;
  readonly previewCap = PREVIEW_CAP;

  readonly start = signal(new Date(2026, 6, 7, 10, 0, 0, 0)); // Di 07.07.2026
  readonly end = signal(new Date(2026, 6, 7, 12, 0, 0, 0));
  readonly rule = signal<ProtoRule>({
    type: 'WEEKLY',
    interval: 1,
    endMode: 'COUNT',
    until: null,
    count: 10,
    weekdays: [1, 3], // Di + Do
    exceptions: [],
  });
  readonly splitInfo = signal(false);

  private readonly expanded = computed(() => this.expand());
  readonly preview = computed(() => this.expanded().rows);
  readonly truncated = computed(() => this.expanded().truncated);
  readonly activeCount = computed(() => this.preview().filter((o) => !o.skipped).length);

  // ------------------------------------------------------------ edits

  setStartDate(v: Date | null): void {
    if (!v) return;
    const s = new Date(v);
    s.setHours(this.start().getHours(), this.start().getMinutes(), 0, 0);
    this.shiftTimes(s);
  }

  setStartTime(v: Date | null): void {
    if (!v) return;
    const s = new Date(this.start());
    s.setHours(v.getHours(), v.getMinutes(), 0, 0);
    if (s.getTime() !== this.start().getTime()) this.shiftTimes(s);
  }

  /** Swing coupling (2b.1): start edits shift the end, duration preserved. */
  private shiftTimes(newStart: Date): void {
    const duration = this.end().getTime() - this.start().getTime();
    this.start.set(newStart);
    this.end.set(new Date(newStart.getTime() + duration));
  }

  setEndTime(v: Date | null): void {
    if (!v) return;
    const e = new Date(this.start());
    e.setHours(v.getHours(), v.getMinutes(), 0, 0);
    // end time before start = next day (Swing RepeatingEditor rule)
    if (e.getTime() <= this.start().getTime()) e.setTime(e.getTime() + DAY_MS);
    if (e.getTime() !== this.end().getTime()) this.end.set(e);
  }

  /** Type switch resets type-specific fields (Swing savedRepeatingType analog). */
  setType(type: RepeatType): void {
    this.rule.update((r) => ({
      ...r,
      type,
      interval: 1,
      weekdays: type === 'WEEKLY' ? [weekdayIndex(this.start())] : [],
      exceptions: [],
    }));
    this.splitInfo.set(false);
  }

  setInterval(v: string): void {
    const n = Math.max(1, Math.floor(Number(v) || 1));
    this.rule.update((r) => ({ ...r, interval: n }));
  }

  toggleWeekday(i: number): void {
    this.rule.update((r) => ({
      ...r,
      weekdays: r.weekdays.includes(i)
        ? r.weekdays.filter((w) => w !== i)
        : [...r.weekdays, i].sort(),
    }));
  }

  setEndMode(mode: EndMode): void {
    this.rule.update((r) => ({
      ...r,
      endMode: mode,
      until:
        mode === 'UNTIL' && !r.until ? new Date(this.start().getTime() + 90 * DAY_MS) : r.until,
    }));
  }

  setUntil(v: Date | null): void {
    if (v) this.rule.update((r) => ({ ...r, until: v }));
  }

  setCount(v: string): void {
    const n = Math.max(1, Math.floor(Number(v) || 1));
    this.rule.update((r) => ({ ...r, count: n }));
  }

  toggleException(occ: PreviewRow): void {
    const key = dayKey(occ.start);
    this.rule.update((r) => ({
      ...r,
      exceptions: r.exceptions.includes(key)
        ? r.exceptions.filter((e) => e !== key)
        : [...r.exceptions, key],
    }));
  }

  // ------------------------------------------------------------ derived

  intervalUnit(): string {
    const plural = this.rule().interval !== 1;
    switch (this.rule().type) {
      case 'DAILY':
        return plural ? 'Tage' : 'Tag';
      case 'WEEKLY':
        return plural ? 'Wochen' : 'Woche';
      case 'MONTHLY':
        return plural ? 'Monate' : 'Monat';
      default:
        return plural ? 'Jahre' : 'Jahr';
    }
  }

  summary(): string {
    const r = this.rule();
    const s = this.start();
    let pattern: string;
    switch (r.type) {
      case 'DAILY':
        pattern = r.interval === 1 ? 'Täglich' : `Alle ${r.interval} Tage`;
        break;
      case 'WEEKLY': {
        const days = r.weekdays.map((w) => WEEKDAY_NAMES[w]).join(' + ') || '—';
        pattern = (r.interval === 1 ? 'Wöchentlich' : `Alle ${r.interval} Wochen`) + ` am ${days}`;
        break;
      }
      case 'MONTHLY': {
        const nth = Math.ceil(s.getDate() / 7);
        pattern =
          (r.interval === 1 ? 'Monatlich' : `Alle ${r.interval} Monate`) +
          ` am ${nth}. ${WEEKDAY_NAMES[weekdayIndex(s)]} (aus Starttermin)`;
        break;
      }
      default:
        pattern =
          (r.interval === 1 ? 'Jährlich' : `Alle ${r.interval} Jahre`) +
          ` am ${s.getDate()}.${s.getMonth() + 1}. (aus Starttermin)`;
    }
    const end =
      r.endMode === 'FOREVER'
        ? 'endet nie'
        : r.endMode === 'COUNT'
          ? `${r.count} Termine`
          : `bis ${r.until ? dayKey(r.until) : '—'}`;
    return `${pattern} · ${end}`;
  }

  // ------------------------------------- PROTOTYPE-ONLY expansion (see header)

  private expand(): { rows: PreviewRow[]; truncated: boolean } {
    const r = this.rule();
    if (r.type === 'NONE') return { rows: [], truncated: false };
    const duration = this.end().getTime() - this.start().getTime();
    const starts: Date[] = [];
    const wanted = r.endMode === 'COUNT' ? r.count : PREVIEW_CAP + 1;
    const untilMs =
      r.endMode === 'UNTIL' && r.until ? r.until.getTime() + DAY_MS : Number.MAX_SAFE_INTEGER;
    let cursor = new Date(this.start());
    let guard = 0;
    while (starts.length < wanted && guard++ < 5000) {
      if (cursor.getTime() >= untilMs) break;
      if (this.matches(cursor, r)) starts.push(new Date(cursor));
      cursor = new Date(cursor.getTime() + DAY_MS);
      cursor.setHours(this.start().getHours(), this.start().getMinutes(), 0, 0);
    }
    const rows = starts.map((start) => ({
      start,
      end: new Date(start.getTime() + duration),
      skipped: r.exceptions.includes(dayKey(start)),
    }));
    return { rows: rows.slice(0, PREVIEW_CAP), truncated: rows.length > PREVIEW_CAP };
  }

  private matches(day: Date, r: ProtoRule): boolean {
    const s = this.start();
    const midnight = (d: Date) => new Date(d).setHours(0, 0, 0, 0);
    const daysSince = Math.round((midnight(day) - midnight(s)) / DAY_MS);
    switch (r.type) {
      case 'DAILY':
        return daysSince % r.interval === 0;
      case 'WEEKLY': {
        if (!r.weekdays.includes(weekdayIndex(day))) return false;
        const weekStartOffset = weekdayIndex(s);
        const weeksSince = Math.floor((daysSince + weekStartOffset) / 7);
        return weeksSince % r.interval === 0;
      }
      case 'MONTHLY': {
        if (weekdayIndex(day) !== weekdayIndex(s)) return false;
        if (Math.ceil(day.getDate() / 7) !== Math.ceil(s.getDate() / 7)) return false;
        const monthsSince =
          (day.getFullYear() - s.getFullYear()) * 12 + (day.getMonth() - s.getMonth());
        return monthsSince % r.interval === 0;
      }
      default: {
        if (day.getDate() !== s.getDate() || day.getMonth() !== s.getMonth()) return false;
        return (day.getFullYear() - s.getFullYear()) % r.interval === 0;
      }
    }
  }
}
