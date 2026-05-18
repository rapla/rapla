import { Component, inject, signal, OnInit, ViewChild, AfterViewInit } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatTableDataSource, MatTableModule } from '@angular/material/table';
import { MatSort, MatSortModule } from '@angular/material/sort';
import { MatPaginator, MatPaginatorModule } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { switchMap, map } from 'rxjs';

import { RemoteStorageControllerService } from '../api/api/remote-storage-controller.service';
import { AppointmentMap } from '../api/model/appointment-map';
import { AllocatableImpl } from '../api/model/allocatable-impl';
import { ClassificationImpl } from '../api/model/classification-impl';
import { AuthService } from '../auth/auth.service';
import { ReservationDialogComponent } from './reservation-dialog.component';

interface AppointmentLite {
  start: string;
  end: string;
  reservationName: string;
  allocatables: string[];
}

/**
 * Local shapes for the REST payloads this view reads. getResources() is typed
 * `any` by the generated client, so the bundle is described here; the
 * reservation/appointment shapes come from the generated AppointmentMap model.
 */
interface ResourceBundle {
  resources?: AllocatableImpl[];
}

interface Classified {
  id?: string;
  classification?: ClassificationImpl;
}

@Component({
  selector: 'app-reservations',
  imports: [
    DatePipe,
    MatTableModule,
    MatSortModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    MatToolbarModule,
    MatButtonModule,
    MatIconModule,
    MatDialogModule,
  ],
  template: `
    <mat-toolbar color="primary">
      <span>Reservations</span>
      <span class="spacer"></span>
      <button matButton (click)="auth.signOut()">
        <mat-icon>logout</mat-icon>
        Sign out
      </button>
    </mat-toolbar>

    <section class="content">
      @if (loading()) {
        <div class="centered"><mat-spinner diameter="32"></mat-spinner></div>
      } @else if (error()) {
        <p class="error">{{ error() }}</p>
      } @else {
        <p class="meta">
          {{ dataSource.data.length }} appointment(s), {{ resourceCount() }} resource(s) hydrated.
        </p>

        <table mat-table [dataSource]="dataSource" matSort class="mat-elevation-z1">
          <ng-container matColumnDef="start">
            <th mat-header-cell *matHeaderCellDef mat-sort-header>Start</th>
            <td mat-cell *matCellDef="let r">{{ r.start | date: 'medium' }}</td>
          </ng-container>

          <ng-container matColumnDef="end">
            <th mat-header-cell *matHeaderCellDef mat-sort-header>End</th>
            <td mat-cell *matCellDef="let r">{{ r.end | date: 'medium' }}</td>
          </ng-container>

          <ng-container matColumnDef="reservationName">
            <th mat-header-cell *matHeaderCellDef mat-sort-header>Event</th>
            <td mat-cell *matCellDef="let r">{{ r.reservationName }}</td>
          </ng-container>

          <ng-container matColumnDef="allocatables">
            <th mat-header-cell *matHeaderCellDef>Allocatables</th>
            <td mat-cell *matCellDef="let r">{{ r.allocatables.join(', ') }}</td>
          </ng-container>

          <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
          <tr
            mat-row
            *matRowDef="let r; columns: displayedColumns"
            class="row-clickable"
            (click)="openDialog(r)"
          ></tr>

          <tr class="mat-row" *matNoDataRow>
            <td class="empty" [attr.colspan]="displayedColumns.length">
              No reservations in the queried window.
            </td>
          </tr>
        </table>

        <mat-paginator
          [pageSizeOptions]="[10, 25, 50, 100]"
          [pageSize]="25"
          showFirstLastButtons
        ></mat-paginator>
      }
    </section>
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .spacer {
        flex: 1 1 auto;
      }
      .content {
        max-width: 1100px;
        margin: 1.5rem auto;
        padding: 0 1rem;
      }
      .meta {
        color: rgba(0, 0, 0, 0.6);
        font-size: 0.85rem;
        margin: 0 0 0.5rem;
      }
      table {
        width: 100%;
      }
      .row-clickable {
        cursor: pointer;
      }
      .row-clickable:hover {
        background: rgba(0, 0, 0, 0.04);
      }
      .empty {
        padding: 1rem;
        color: rgba(0, 0, 0, 0.5);
        text-align: center;
        font-style: italic;
      }
      .centered {
        display: flex;
        justify-content: center;
        padding: 2rem;
      }
      .error {
        color: #c62828;
      }
    `,
  ],
})
export class ReservationsComponent implements OnInit, AfterViewInit {
  private readonly api = inject(RemoteStorageControllerService);
  private readonly dialog = inject(MatDialog);
  protected readonly auth = inject(AuthService);

  readonly displayedColumns = ['start', 'end', 'reservationName', 'allocatables'];
  readonly dataSource = new MatTableDataSource<AppointmentLite>([]);

  loading = signal(true);
  error = signal<string | null>(null);
  resourceCount = signal(0);

  @ViewChild(MatSort) sort!: MatSort;
  @ViewChild(MatPaginator) paginator!: MatPaginator;

  ngOnInit() {
    const now = new Date();
    const start = new Date(now.getFullYear() - 1, 0, 1).toISOString().replace(/\.\d{3}Z$/, '');
    const end = new Date(now.getFullYear() + 2, 11, 31).toISOString().replace(/\.\d{3}Z$/, '');

    this.api
      .getResources()
      .pipe(
        switchMap((bundle: ResourceBundle) => {
          const resources = this.buildResourceIndex(bundle);
          this.resourceCount.set(resources.size);
          const resourceIds = Array.from(resources.keys());
          return this.api
            .queryAppointments({ start, end, resources: resourceIds })
            .pipe(map((appts) => ({ appts, resources })));
        }),
      )
      .subscribe({
        next: ({ appts, resources }) => {
          this.dataSource.data = this.flattenAppointments(appts, resources);
          this.loading.set(false);
        },
        error: (err) => {
          this.error.set(err?.error?.message ?? `Request failed (HTTP ${err?.status ?? '?'})`);
          this.loading.set(false);
        },
      });
  }

  ngAfterViewInit() {
    this.dataSource.sort = this.sort;
    this.dataSource.paginator = this.paginator;
  }

  openDialog(row: AppointmentLite) {
    this.dialog.open(ReservationDialogComponent, {
      data: row,
      width: '480px',
      autoFocus: 'dialog',
    });
  }

  private buildResourceIndex(bundle: ResourceBundle): Map<string, string> {
    const out = new Map<string, string>();
    for (const r of bundle.resources ?? []) {
      if (r.id) out.set(r.id, this.labelFor(r));
    }
    return out;
  }

  private labelFor(resource: Classified): string {
    const data = resource.classification?.data ?? {};
    for (const key of ['name', 'surname', 'firstname', 'title']) {
      const v = data[key];
      if (Array.isArray(v) && v[0]) return String(v[0]);
    }
    const firstKey = Object.keys(data)[0];
    const v = firstKey ? data[firstKey] : null;
    return Array.isArray(v) && v[0] ? String(v[0]) : (resource.id ?? '');
  }

  private flattenAppointments(
    payload: AppointmentMap,
    resources: Map<string, string>,
  ): AppointmentLite[] {
    const result: AppointmentLite[] = [];
    for (const res of payload.reservations ?? []) {
      const name = this.labelFor(res) || '(no name)';
      const allocIds = res.links?.['resources'] ?? res.links?.['allocatable'] ?? [];
      const allocNames = allocIds.map((id) => resources.get(id) ?? id);
      for (const appt of res.appointments ?? []) {
        result.push({
          start: appt.start ?? '',
          end: appt.end ?? '',
          reservationName: name,
          allocatables: allocNames,
        });
      }
    }
    return result.sort((a, b) => (a.start < b.start ? -1 : 1));
  }
}
