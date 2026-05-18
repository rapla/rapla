import { Component, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';

export interface ReservationDialogData {
  start: string;
  end: string;
  reservationName: string;
  allocatables: string[];
}

@Component({
  selector: 'app-reservation-dialog',
  imports: [DatePipe, MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>{{ data.reservationName }}</h2>
    <mat-dialog-content>
      <dl>
        <dt>Start</dt>
        <dd>{{ data.start | date: 'medium' }}</dd>
        <dt>End</dt>
        <dd>{{ data.end | date: 'medium' }}</dd>
        <dt>Allocatables</dt>
        <dd>{{ data.allocatables.join(', ') || '—' }}</dd>
      </dl>
      <p class="muted">Editor lives in PRD 026 phases 2+.</p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button matButton (click)="ref.close()">Close</button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      dl {
        display: grid;
        grid-template-columns: max-content 1fr;
        gap: 0.35rem 1rem;
        margin: 0;
      }
      dt {
        color: rgba(0, 0, 0, 0.6);
        font-size: 0.85rem;
      }
      dd {
        margin: 0;
      }
      .muted {
        color: rgba(0, 0, 0, 0.5);
        font-size: 0.8rem;
        margin-top: 1rem;
      }
    `,
  ],
})
export class ReservationDialogComponent {
  protected readonly ref = inject<MatDialogRef<ReservationDialogComponent>>(MatDialogRef);
  protected readonly data = inject<ReservationDialogData>(MAT_DIALOG_DATA);
}
