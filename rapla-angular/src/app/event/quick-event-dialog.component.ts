import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DragDropModule } from '@angular/cdk/drag-drop';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTimepickerModule } from '@angular/material/timepicker';

import { GraphqlService } from '../graphql/graphql.service';
import type { MutationIssue } from '../graphql/mutation-result';
import { EventDataService } from './event-data.service';
import { EventSheetComponent, type EventSheetDialogData } from './event-sheet.component';
import { newDraft, withEnd, withStart, type EventDraft } from './event-draft';

/**
 * PRD 091 — quick-create window (gcal-style, locked in the 2026-07-07
 * Material prototype round). Backdrop-less draggable MatDialog behind the
 * toolbar "Neu" button: type, name, ONE appointment as four coupled
 * date/time fields. "Mehr Optionen" carries the draft into the full event
 * sheet via router state; saving stays on the current page.
 */
@Component({
  selector: 'app-quick-event-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DragDropModule,
    MatButtonModule,
    MatDatepickerModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatTimepickerModule,
  ],
  styles: `
    .card {
      width: 560px;
      max-width: 90vw;
    }
    .grip {
      display: flex;
      align-items: center;
      padding: 6px 12px;
      cursor: grab;
      color: var(--mat-sys-on-surface-variant, #5c6774);
      background: var(--mat-sys-surface-container, #eef1f5);
    }
    .grip:active {
      cursor: grabbing;
    }
    .body {
      padding: 14px 16px 0;
    }
    .row {
      display: flex;
      align-items: baseline;
      gap: 8px;
      flex-wrap: wrap;
      row-gap: 4px;
    }
    .grow {
      flex: 1;
    }
    .dash {
      align-self: center;
      color: var(--mat-sys-on-surface-variant, #5c6774);
    }
    .foot {
      display: flex;
      align-items: center;
      padding: 4px 16px 12px;
    }
    .spacer {
      flex: 1;
    }
    mat-form-field.f-type {
      width: 180px;
    }
    mat-form-field.f-date {
      width: 150px;
    }
    mat-form-field.f-time {
      width: 148px;
    }
    .err {
      color: var(--mat-sys-error, #d93025);
      font-size: 12px;
      margin: 4px 0 0;
    }
  `,
  template: `
    <div class="card" cdkDrag cdkDragRootElement=".cdk-overlay-pane">
      @if (draft(); as d) {
        <div class="grip" cdkDragHandle>
          <span>⠿ &nbsp;Neue Veranstaltung</span>
          <span class="spacer"></span>
          <button matIconButton aria-label="Schließen" (click)="dialogRef.close()">✕</button>
        </div>
        <div class="body">
          <div class="row">
            <mat-form-field class="f-type" appearance="outline" subscriptSizing="dynamic">
              <mat-label>Veranstaltungstyp</mat-label>
              <mat-select [value]="d.typeKey" (valueChange)="setTypeKey($event)">
                @for (t of typeOptions(); track t.key) {
                  <mat-option [value]="t.key">{{ t.name }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
            <mat-form-field class="grow" appearance="outline" subscriptSizing="dynamic">
              <mat-label>Name</mat-label>
              <input matInput [value]="name()" (input)="setName($any($event.target).value)" />
            </mat-form-field>
          </div>
          <div class="row">
            <mat-form-field class="f-date" appearance="outline" subscriptSizing="dynamic">
              <mat-label>Beginn</mat-label>
              <input
                matInput
                [matDatepicker]="sd"
                [value]="asDate(d.appointments[0].start)"
                (dateChange)="setStartDate($event.value)"
              />
              <mat-datepicker-toggle matSuffix [for]="sd" />
              <mat-datepicker #sd />
            </mat-form-field>
            <mat-form-field class="f-time" appearance="outline" subscriptSizing="dynamic">
              <input
                matInput
                [matTimepicker]="st"
                [value]="asDate(d.appointments[0].start)"
                (valueChange)="setStartTime($event)"
              />
              <mat-timepicker-toggle matSuffix [for]="st" />
              <mat-timepicker #st interval="15m" />
            </mat-form-field>
            <span class="dash">–</span>
            <mat-form-field class="f-date" appearance="outline" subscriptSizing="dynamic">
              <mat-label>Ende</mat-label>
              <input
                matInput
                [matDatepicker]="ed"
                [value]="asDate(d.appointments[0].end)"
                (dateChange)="setEndDate($event.value)"
              />
              <mat-datepicker-toggle matSuffix [for]="ed" />
              <mat-datepicker #ed />
            </mat-form-field>
            <mat-form-field class="f-time" appearance="outline" subscriptSizing="dynamic">
              <input
                matInput
                [matTimepicker]="et"
                [value]="asDate(d.appointments[0].end)"
                (valueChange)="setEndTime($event)"
              />
              <mat-timepicker-toggle matSuffix [for]="et" />
              <mat-timepicker #et interval="15m" />
            </mat-form-field>
          </div>
          @for (issue of issues(); track issue.path + issue.code) {
            <p class="err">{{ issue.code }} · {{ issue.path }} — {{ issue.message }}</p>
          }
        </div>
        <div class="foot">
          <button matButton (click)="moreOptions()">Mehr Optionen</button>
          <span class="spacer"></span>
          <button matButton="filled" [disabled]="saving()" (click)="save()">
            {{ saving() ? 'Speichere…' : 'Speichern' }}
          </button>
        </div>
      }
    </div>
  `,
})
export class QuickEventDialogComponent {
  readonly dialogRef = inject(MatDialogRef<QuickEventDialogComponent>);
  private readonly data = inject(EventDataService);
  private readonly gql = inject(GraphqlService);
  private readonly dialog = inject(MatDialog);
  private readonly destroyRef = inject(DestroyRef);

  readonly draft = signal<EventDraft>(newDraft('event', new Date()));
  readonly typeOptions = signal<{ key: string; name: string }[]>([]);
  readonly saving = signal(false);
  readonly issues = signal<MutationIssue[]>([]);

  constructor() {
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

  name(): string {
    const name = this.draft().values['name'];
    return typeof name === 'string' ? name : '';
  }

  // Stable per-iso Date instances — a fresh `new Date(iso)` per CD cycle
  // re-triggers the pickers' [value] setter and loops CD (NG0103).
  private readonly dateCache = new Map<string, Date>();

  asDate(iso: string): Date {
    let d = this.dateCache.get(iso);
    if (!d) {
      d = new Date(iso);
      this.dateCache.set(iso, d);
    }
    return d;
  }

  private mutate(fn: (d: EventDraft) => void): void {
    const d = this.draft();
    fn(d);
    this.draft.set({ ...d });
  }

  setTypeKey(key: string): void {
    this.mutate((d) => (d.typeKey = key));
  }

  setName(value: string): void {
    this.mutate((d) => (d.values['name'] = value));
  }

  private isoFrom(datePart: Date, timePart: Date): string {
    const p = (n: number) => String(n).padStart(2, '0');
    return `${datePart.getFullYear()}-${p(datePart.getMonth() + 1)}-${p(datePart.getDate())}T${p(timePart.getHours())}:${p(timePart.getMinutes())}:00`;
  }

  // Equality guards — mat-timepicker echoes programmatic [value] writes (see
  // angular-frontend skill, Material widget traps).
  private applyTimes(times: { start: string; end: string }): void {
    this.mutate((d) => {
      d.appointments[0].start = times.start;
      d.appointments[0].end = times.end;
    });
  }

  setStartDate(v: Date | null): void {
    const a = this.draft().appointments[0];
    if (!v) return;
    const next = this.isoFrom(v, new Date(a.start));
    if (next !== a.start) this.applyTimes(withStart(a, next));
  }

  setStartTime(v: Date | null): void {
    const a = this.draft().appointments[0];
    if (!v) return;
    const next = this.isoFrom(new Date(a.start), v);
    if (next !== a.start) this.applyTimes(withStart(a, next));
  }

  setEndDate(v: Date | null): void {
    const a = this.draft().appointments[0];
    if (!v) return;
    const next = this.isoFrom(v, new Date(a.end));
    if (next !== a.end) this.applyEnd(a, withEnd(a, next));
  }

  setEndTime(v: Date | null): void {
    const a = this.draft().appointments[0];
    if (!v) return;
    const next = this.isoFrom(new Date(a.end), v);
    if (next !== a.end) this.applyEnd(a, withEnd(a, next));
  }

  // Clamp rejected the pick without a model change → bust the cache once so
  // the [value] binding delivers a fresh instance and the widget reformats
  // (see event-sheet.component.ts, same pattern).
  private applyEnd(a: { start: string; end: string }, times: { start: string; end: string }): void {
    if (times.end === a.end) {
      this.dateCache.delete(a.start);
      this.dateCache.delete(a.end);
      this.draft.set({ ...this.draft() });
      return;
    }
    this.applyTimes(times);
  }

  moreOptions(): void {
    const d = this.draft();
    this.dialogRef.close();
    this.dialog.open(EventSheetComponent, {
      data: { id: d.id, isNew: true, draft: d } satisfies EventSheetDialogData,
      width: '960px',
      maxWidth: '95vw',
      height: '90vh',
      restoreFocus: false,
    });
  }

  save(): void {
    const d = this.draft();
    if (this.saving()) return;
    this.saving.set(true);
    this.issues.set([]);
    this.data
      .save(d)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((result) => {
        this.saving.set(false);
        switch (result.kind) {
          case 'ok':
            this.dialogRef.close('saved');
            break;
          case 'invalid':
          case 'denied':
            this.issues.set(result.issues);
            break;
          case 'concurrent':
            this.issues.set([
              { code: 'CONCURRENT', path: '', message: 'Zwischenzeitlich geändert.' },
            ]);
            break;
          case 'transport':
            this.issues.set([{ code: 'TRANSPORT', path: '', message: result.message }]);
            break;
        }
      });
  }
}
