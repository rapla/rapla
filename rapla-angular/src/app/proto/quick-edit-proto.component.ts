import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  TemplateRef,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { DragDropModule } from '@angular/cdk/drag-drop';
import { provideNativeDateAdapter } from '@angular/material/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTimepickerModule } from '@angular/material/timepicker';

/**
 * THROWAWAY PROTOTYPE — PRD 091 quick-edit window exploration.
 * Not wired to any service; static demo data; delete the proto/ folder
 * (and its route) once the design is locked. Purpose: validate the REAL
 * Material widgets (mat-datepicker, mat-timepicker, mat-select, MatDialog
 * + cdkDrag) with de-DE locale + the Swing-style four-field coupling.
 *
 * Both windows are MatDialogs: the quick card backdrop-less + draggable
 * (cdkDragRootElement grabs the overlay pane), the sheet fullscreen.
 * Fixed-position divs inside the routed component sit in the sidenav
 * content's stacking context and paint UNDER the drawer — dialogs render
 * in the global overlay container and don't.
 */

interface ProtoEvent {
  id: number;
  title: string;
  type: string;
  start: Date;
  end: Date;
  res: string[];
}

interface QuickDraft {
  id: number | null;
  title: string;
  type: string;
  start: Date;
  end: Date;
  res: string[];
}

const HOUR_PX = 48;
const DAY_FROM = 8;
const DAY_TO = 18;

@Component({
  selector: 'app-quick-edit-proto',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [provideNativeDateAdapter()],
  imports: [
    DatePipe,
    DragDropModule,
    MatButtonModule,
    MatDatepickerModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatTimepickerModule,
  ],
  styles: `
    :host { display: block; padding: 12px; }
    .topbar { display: flex; align-items: center; gap: 12px; margin-bottom: 10px; }
    .topbar .brand { font-weight: 700; }
    .spacer { flex: 1; }
    .hint { color: #5c6774; font-size: 12px; margin: 0 0 8px; }

    .cal { display: flex; border: 1px solid #d9dee6; border-radius: 10px; overflow: hidden; user-select: none; }
    .timecol { width: 46px; flex: 0 0 46px; border-right: 1px solid #d9dee6; }
    .timecol .h { height: 48px; font-size: 11px; color: #5c6774; text-align: right; padding: 2px 6px 0 0; }
    .daycol { flex: 1; border-right: 1px solid #d9dee6; min-width: 0; }
    .daycol:last-child { border-right: none; }
    .dayhead { text-align: center; font-size: 12px; padding: 5px 2px; color: #5c6774; border-bottom: 1px solid #d9dee6; background: #eef1f5; }
    .slots { position: relative; cursor: cell; }
    .slot { height: 48px; border-bottom: 1px solid #edf0f4; }
    .chip { position: absolute; left: 3px; right: 3px; border-radius: 6px; padding: 3px 6px; background: #e3edfc; border-left: 3px solid #1a73e8; font-size: 12px; overflow: hidden; cursor: pointer; z-index: 2; }
    .chip.loan { border-left-color: #b06000; background: #fef3e0; }
    .chip .t { color: #5c6774; font-size: 11px; }

    .quick-card { width: 560px; }
    .grip { display: flex; align-items: center; padding: 6px 12px; cursor: grab; color: #5c6774; background: #eef1f5; }
    .grip:active { cursor: grabbing; }
    .body { padding: 14px 16px 4px; }
    .row { display: flex; align-items: baseline; gap: 8px; }
    .row .grow { flex: 1; }
    .dash { color: #5c6774; align-self: center; }
    .foot { display: flex; align-items: center; padding: 0 16px 12px; }
    mat-form-field.type { width: 170px; }
    mat-form-field.date { width: 150px; }
    mat-form-field.time { width: 148px; }

    .sheet-page { height: 100%; overflow: auto; background: #f7f8fa; }
    .ed-top { position: sticky; top: 0; display: flex; align-items: center; gap: 10px; padding: 8px 16px; background: #fff; border-bottom: 1px solid #d9dee6; z-index: 2; }
    .crumb { font-weight: 600; }
    .crumb .muted, .muted { font-weight: 400; color: #5c6774; }
    .sheet { max-width: 820px; margin: 14px auto 60px; padding: 0 14px; display: flex; flex-direction: column; gap: 12px; }
    .sec { background: #fff; border: 1px solid #d9dee6; border-radius: 10px; padding: 12px 14px 2px; }
    .sec-title { font-weight: 600; margin-bottom: 8px; }
    .pill { border-radius: 99px; padding: 1px 8px; font-size: 11px; }
    .pill.frei { background: #e6f4ea; color: #1e8e3e; }
    .pill.belegt { background: #fce8e6; color: #d93025; }
    .rrow { display: flex; align-items: center; gap: 8px; padding: 6px 0 14px; border-bottom: 1px dashed #d9dee6; justify-content: space-between; }
    .rrow:last-of-type { border-bottom: none; }
  `,
  template: `
    <div class="topbar">
      <span class="brand">rapla· Kalender <small>(Material-Prototyp)</small></span>
      <span class="spacer"></span>
      <button mat-stroked-button (click)="mobile.set(!mobile())">
        {{ mobile() ? '🖥 Desktop-Modus' : '📱 Mobil-Modus' }}
      </button>
      <button mat-flat-button (click)="openNew($event)">Neu</button>
    </div>
    <p class="hint">
      Klick in eine freie Zelle → Quick-Edit-Fenster (echte Material-Widgets, verschiebbar am Griff).
      Start verschieben nimmt das Ende mit (Dauer bleibt); Ende vor Start wird geklemmt.
      {{ mobile() ? 'Mobil: Klick öffnet direkt das Sheet.' : '' }}
    </p>

    <div class="cal" [style.max-width.px]="mobile() ? 420 : null">
      <div class="timecol">
        <div class="dayhead">&nbsp;</div>
        @for (h of hours; track h) {
          <div class="h">{{ h }}:00</div>
        }
      </div>
      @for (d of visibleDays(); track d) {
        <div class="daycol">
          <div class="dayhead">{{ dayDate(d) | date: 'EE dd.MM.' }}</div>
          <div
            class="slots"
            tabindex="0"
            role="button"
            (click)="slotClick(d, $event)"
            (keydown.enter)="slotClick(d, $any($event))"
          >
            @for (h of hours; track h) {
              <div class="slot"></div>
            }
            @for (ev of chipsFor(d); track ev.id) {
              <div
                class="chip"
                [class.loan]="ev.type === 'loan'"
                [style.top.px]="chipTop(ev)"
                [style.height.px]="chipHeight(ev)"
                tabindex="0"
                role="button"
                (click)="openEvent(ev, $event)"
                (keydown.enter)="openEvent(ev, $any($event))"
              >
                <div>{{ ev.title }}</div>
                <div class="t">{{ ev.start | date: 'HH:mm' }}–{{ ev.end | date: 'HH:mm' }} · {{ ev.res.join(', ') }}</div>
              </div>
            }
          </div>
        </div>
      }
    </div>

    <!-- ------------------------------------------- quick edit dialog -->
    <ng-template #quickTpl>
      @if (quick(); as q) {
        <div class="quick-card" cdkDrag cdkDragRootElement=".cdk-overlay-pane">
          <div class="grip" cdkDragHandle>
            <span>⠿ &nbsp;Schnell bearbeiten</span>
            <span class="spacer"></span>
            <button mat-icon-button aria-label="Schließen" (click)="closeAll()">✕</button>
          </div>
          <div class="body">
            <div class="row">
              <mat-form-field class="type" appearance="outline">
                <mat-label>Veranstaltungstyp</mat-label>
                <mat-select [value]="q.type" (valueChange)="patch({ type: $event })">
                  @for (t of types; track t.key) {
                    <mat-option [value]="t.key">{{ t.name }}</mat-option>
                  }
                </mat-select>
              </mat-form-field>
              <mat-form-field class="grow" appearance="outline">
                <mat-label>Name</mat-label>
                <input matInput [value]="q.title" (input)="patch({ title: $any($event.target).value })" />
              </mat-form-field>
            </div>
            <div class="row">
              <mat-form-field class="date" appearance="outline">
                <mat-label>Beginn</mat-label>
                <input matInput [matDatepicker]="sd" [value]="q.start" (dateChange)="setStartDate($event.value)" />
                <mat-datepicker-toggle matSuffix [for]="sd" />
                <mat-datepicker #sd />
              </mat-form-field>
              <mat-form-field class="time" appearance="outline">
                <input matInput [matTimepicker]="st" [value]="q.start" (valueChange)="setStartTime($event)" />
                <mat-timepicker-toggle matSuffix [for]="st" />
                <mat-timepicker #st interval="15m" />
              </mat-form-field>
              <span class="dash">–</span>
              <mat-form-field class="date" appearance="outline">
                <mat-label>Ende</mat-label>
                <input matInput [matDatepicker]="ed" [value]="q.end" (dateChange)="setEndDate($event.value)" />
                <mat-datepicker-toggle matSuffix [for]="ed" />
                <mat-datepicker #ed />
              </mat-form-field>
              <mat-form-field class="time" appearance="outline">
                <input matInput [matTimepicker]="et" [value]="q.end" (valueChange)="setEndTime($event)" />
                <mat-timepicker-toggle matSuffix [for]="et" />
                <mat-timepicker #et interval="15m" />
              </mat-form-field>
            </div>
            <p class="hint">
              {{ q.res.length ? q.res.join(', ') + ' · Zuordnung im Editor' : 'Keine Ressourcen — im Editor zuordnen' }}
            </p>
          </div>
          <div class="foot">
            <button mat-button (click)="openSheet()">Mehr Optionen</button>
            <span class="spacer"></span>
            <button mat-flat-button (click)="saveQuick()">Speichern</button>
          </div>
        </div>
      }
    </ng-template>

    <!-- ------------------------------------------- fullscreen sheet dialog -->
    <ng-template #sheetTpl>
      @if (quick(); as q) {
        <div class="sheet-page">
          <div class="ed-top">
            <button mat-icon-button aria-label="Schließen" (click)="closeAll()">✕</button>
            <span class="crumb">
              <span class="muted">{{ typeName(q.type) }} · </span>{{ q.title || '(ohne Namen)' }}
            </span>
            <span class="spacer"></span>
            <button mat-flat-button (click)="saveQuick()">Speichern</button>
          </div>
          <div class="sheet">
            <div class="sec">
              <div class="sec-title">Veranstaltungstyp &amp; Attribute</div>
              <div class="row">
                <mat-form-field class="type" appearance="outline">
                  <mat-label>Veranstaltungstyp</mat-label>
                  <mat-select [value]="q.type" (valueChange)="patch({ type: $event })">
                    @for (t of types; track t.key) {
                      <mat-option [value]="t.key">{{ t.name }}</mat-option>
                    }
                  </mat-select>
                </mat-form-field>
                <mat-form-field class="grow" appearance="outline">
                  <mat-label>Name</mat-label>
                  <input matInput [value]="q.title" (input)="patch({ title: $any($event.target).value })" />
                </mat-form-field>
              </div>
            </div>
            <div class="sec">
              <div class="sec-title">Termine</div>
              <div class="row">
                <span>①</span>
                <mat-form-field class="date" appearance="outline">
                  <mat-label>Beginn</mat-label>
                  <input matInput [matDatepicker]="sd2" [value]="q.start" (dateChange)="setStartDate($event.value)" />
                  <mat-datepicker-toggle matSuffix [for]="sd2" />
                  <mat-datepicker #sd2 />
                </mat-form-field>
                <mat-form-field class="time" appearance="outline">
                  <input matInput [matTimepicker]="st2" [value]="q.start" (valueChange)="setStartTime($event)" />
                  <mat-timepicker-toggle matSuffix [for]="st2" />
                  <mat-timepicker #st2 interval="15m" />
                </mat-form-field>
                <span class="dash">–</span>
                <mat-form-field class="date" appearance="outline">
                  <mat-label>Ende</mat-label>
                  <input matInput [matDatepicker]="ed2" [value]="q.end" (dateChange)="setEndDate($event.value)" />
                  <mat-datepicker-toggle matSuffix [for]="ed2" />
                  <mat-datepicker #ed2 />
                </mat-form-field>
                <mat-form-field class="time" appearance="outline">
                  <input matInput [matTimepicker]="et2" [value]="q.end" (valueChange)="setEndTime($event)" />
                  <mat-timepicker-toggle matSuffix [for]="et2" />
                  <mat-timepicker #et2 interval="15m" />
                </mat-form-field>
              </div>
            </div>
            <div class="sec">
              <div class="sec-title">Ressourcen <small class="muted">(statisch — wie Sheet-Prototyp)</small></div>
              <div class="rrow"><span>Raum A66</span><span class="pill belegt">belegt an ①</span></div>
              <div class="rrow"><span>Kamera 2</span><span class="pill frei">frei</span></div>
            </div>
          </div>
        </div>
      }
    </ng-template>
  `,
})
export class QuickEditProtoComponent {
  private readonly dialog = inject(MatDialog);
  private readonly quickTpl = viewChild.required<TemplateRef<unknown>>('quickTpl');
  private readonly sheetTpl = viewChild.required<TemplateRef<unknown>>('sheetTpl');
  private quickRef: MatDialogRef<unknown> | null = null;
  private sheetRef: MatDialogRef<unknown> | null = null;

  readonly hours = Array.from({ length: DAY_TO - DAY_FROM }, (_, i) => DAY_FROM + i);
  readonly types = [
    { key: 'event', name: 'Veranstaltung' },
    { key: 'loan', name: 'Ausleihe' },
    { key: 'meeting', name: 'Besprechung' },
  ];

  private readonly monday = new Date(2026, 6, 6);
  private seq = 4;

  readonly mobile = signal(false);
  readonly events = signal<ProtoEvent[]>([
    { id: 1, title: 'Mathe I', type: 'event', start: this.at(0, 10), end: this.at(0, 12), res: ['Raum A66'] },
    { id: 2, title: 'Kamera-Ausleihe Erwin', type: 'loan', start: this.at(1, 9), end: this.at(1, 17), res: ['Kamera 2'] },
    { id: 3, title: 'Fakultätsrat', type: 'meeting', start: this.at(3, 14), end: this.at(3, 15.5), res: ['Raum B12'] },
  ]);
  readonly quick = signal<QuickDraft | null>(null);
  readonly visibleDays = computed(() => (this.mobile() ? [0, 1] : [0, 1, 2, 3, 4]));

  // ------------------------------------------------------------ calendar

  dayDate(d: number): Date {
    const dt = new Date(this.monday);
    dt.setDate(dt.getDate() + d);
    return dt;
  }

  private at(day: number, hour: number): Date {
    const dt = this.dayDate(day);
    dt.setHours(Math.floor(hour), Math.round((hour % 1) * 60), 0, 0);
    return dt;
  }

  private dayIndex(date: Date): number {
    return Math.floor((date.getTime() - this.monday.getTime()) / 86400e3);
  }

  chipsFor(d: number): ProtoEvent[] {
    return this.events().filter((e) => this.dayIndex(e.start) === d);
  }

  chipTop(ev: ProtoEvent): number {
    return (ev.start.getHours() + ev.start.getMinutes() / 60 - DAY_FROM) * HOUR_PX + 1;
  }

  chipHeight(ev: ProtoEvent): number {
    const sameDay = this.dayIndex(ev.start) === this.dayIndex(ev.end);
    const endH = sameDay ? ev.end.getHours() + ev.end.getMinutes() / 60 : DAY_TO;
    return Math.max(20, (endH - ev.start.getHours() - ev.start.getMinutes() / 60) * HOUR_PX - 3);
  }

  slotClick(day: number, e: MouseEvent): void {
    const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
    const hour = Math.floor((e.clientY - rect.top) / HOUR_PX) + DAY_FROM;
    this.open({ id: null, title: '', type: 'event', start: this.at(day, hour), end: this.at(day, hour + 1), res: [] }, e);
  }

  openEvent(ev: ProtoEvent, e: MouseEvent): void {
    e.stopPropagation();
    this.open({ id: ev.id, title: ev.title, type: ev.type, start: new Date(ev.start), end: new Date(ev.end), res: [...ev.res] }, e);
  }

  openNew(e: MouseEvent): void {
    this.open({ id: null, title: '', type: 'event', start: this.at(0, 10), end: this.at(0, 11), res: [] }, e);
  }

  // ------------------------------------------------------------ dialogs

  private open(draft: QuickDraft, e: MouseEvent): void {
    this.quick.set(draft);
    if (this.mobile()) {
      this.openSheetDialog();
      return;
    }
    this.quickRef?.close('replaced');
    const x = Math.max(8, Math.min(e.clientX + 16, window.innerWidth - 580));
    const y = Math.max(8, Math.min(e.clientY - 40, window.innerHeight - 380));
    const ref = this.dialog.open(this.quickTpl(), {
      hasBackdrop: false,
      position: { left: `${x}px`, top: `${y}px` },
      restoreFocus: false,
      autoFocus: 'input',
    });
    this.quickRef = ref;
    ref.afterClosed().subscribe((result) => {
      if (this.quickRef === ref) this.quickRef = null;
      if (result === undefined) this.quick.set(null); // Esc / ✕
    });
  }

  openSheet(): void {
    this.quickRef?.close('toSheet');
    this.openSheetDialog();
  }

  private openSheetDialog(): void {
    const ref = this.dialog.open(this.sheetTpl(), {
      width: '100vw',
      height: '100vh',
      maxWidth: '100vw',
      restoreFocus: false,
      autoFocus: 'dialog',
    });
    this.sheetRef = ref;
    ref.afterClosed().subscribe((result) => {
      if (this.sheetRef === ref) this.sheetRef = null;
      if (result === undefined) this.quick.set(null);
    });
  }

  closeAll(): void {
    this.quickRef?.close('done');
    this.sheetRef?.close('done');
    this.quick.set(null);
  }

  // ------------------------------------------------------------ draft edits

  patch(p: Partial<QuickDraft>): void {
    this.quick.update((q) => (q ? { ...q, ...p } : q));
  }

  typeName(key: string): string {
    return this.types.find((t) => t.key === key)?.name ?? key;
  }

  /** Swing coupling: any start edit SHIFTS the end so the duration stays. */
  private shiftEnd(newStart: Date): void {
    const q = this.quick();
    if (!q) return;
    const duration = q.end.getTime() - q.start.getTime();
    this.patch({ start: newStart, end: new Date(newStart.getTime() + duration) });
  }

  /** End edits change the duration but are clamped to stay after the start. */
  private clampEnd(newEnd: Date): void {
    const q = this.quick();
    if (!q) return;
    const end = newEnd.getTime() <= q.start.getTime() ? new Date(q.start.getTime() + 15 * 60e3) : newEnd;
    if (end.getTime() === q.end.getTime()) return;
    this.patch({ end });
  }

  private combine(datePart: Date, timePart: Date): Date {
    const d = new Date(datePart);
    d.setHours(timePart.getHours(), timePart.getMinutes(), 0, 0);
    return d;
  }

  // The equality guards matter: mat-timepicker emits valueChange on
  // programmatic [value] writes too — patching a fresh Date instance on every
  // echo loops change detection forever (NG0103).
  setStartDate(v: Date | null): void {
    const q = this.quick();
    if (!q || !v) return;
    const ns = this.combine(v, q.start);
    if (ns.getTime() !== q.start.getTime()) this.shiftEnd(ns);
  }

  setStartTime(v: Date | null): void {
    const q = this.quick();
    if (!q || !v) return;
    const ns = this.combine(q.start, v);
    if (ns.getTime() !== q.start.getTime()) this.shiftEnd(ns);
  }

  setEndDate(v: Date | null): void {
    const q = this.quick();
    if (q && v) this.clampEnd(this.combine(v, q.end));
  }

  setEndTime(v: Date | null): void {
    const q = this.quick();
    if (q && v) this.clampEnd(this.combine(q.end, v));
  }

  // ------------------------------------------------------------ save

  saveQuick(): void {
    const q = this.quick();
    if (!q) return;
    const title = q.title.trim() || `${this.typeName(q.type)} (ohne Titel)`;
    this.events.update((list) => {
      if (q.id === null) {
        return [...list, { id: this.seq++, title, type: q.type, start: q.start, end: q.end, res: q.res }];
      }
      return list.map((e) => (e.id === q.id ? { ...e, title, type: q.type, start: q.start, end: q.end } : e));
    });
    this.closeAll();
  }
}
