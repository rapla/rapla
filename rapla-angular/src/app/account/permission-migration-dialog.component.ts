import { Component, OnInit, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';

import {
  PermissionMigrationFinding,
  PermissionMigrationService,
} from './permission-migration.service';

/**
 * PRD 090 — "Permission migration" admin dialog. One row + one checkbox per
 * allocatable whose effective access rose at the additive flip; each row lists
 * the principals who gain access. Checking "Resolved" acknowledges the whole
 * allocatable (server prunes inert DENIED rows + sets the ack flag), removing it
 * from the list. Empty list ⇒ nothing to migrate.
 */
@Component({
  selector: 'app-permission-migration-dialog',
  imports: [
    MatDialogModule,
    MatButtonModule,
    MatCheckboxModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
  ],
  template: `
    <h2 mat-dialog-title>
      <mat-icon class="title-icon" aria-hidden="true">rule</mat-icon>
      Permission migration
    </h2>
    <mat-dialog-content>
      <p class="intro">
        Switching to additive permissions raised access on these resources (a previous “soft deny”
        no longer applies). Review each, then mark it resolved — this prunes the obsolete
        <code>DENIED</code> rows and clears it from the list.
      </p>

      @if (loading()) {
        <div class="centered"><mat-spinner diameter="28"></mat-spinner></div>
      } @else if (findings().length === 0) {
        <p class="done">
          <mat-icon aria-hidden="true">check_circle</mat-icon>
          Nothing to migrate — every resource is additive-clean.
        </p>
      } @else {
        @for (f of findings(); track f.allocatableId) {
          <div class="finding">
            <mat-checkbox
              [disabled]="busyId() === f.allocatableId"
              (change)="resolve(f)"
              matTooltip="Mark resolved — prune obsolete DENIED rows and acknowledge"
            ></mat-checkbox>
            <div class="fmeta">
              <div class="fname">{{ f.allocatableName }}</div>
              <ul class="who">
                @for (e of f.escalations; track e.principalName + e.additiveLevel) {
                  <li>
                    <span class="user">{{ e.principalName }}</span>
                    <span class="levels"
                      >{{ levelLabel(e.currentLevel) }} → {{ e.additiveLevel }}</span
                    >
                    @if (e.form === 'DENIED') {
                      <span class="badge denied">was denied</span>
                    }
                  </li>
                }
              </ul>
            </div>
          </div>
        }
      }

      @if (errorMessage()) {
        <p class="error">{{ errorMessage() }}</p>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button matButton mat-dialog-close>Close</button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      h2 {
        display: flex;
        align-items: center;
        gap: 0.5rem;
      }
      .title-icon {
        color: #1565c0;
      }
      .intro {
        font-size: 0.85rem;
        color: rgba(0, 0, 0, 0.7);
      }
      .centered {
        display: flex;
        justify-content: center;
        padding: 1rem;
      }
      .done {
        display: flex;
        align-items: center;
        gap: 0.4rem;
        color: #33691e;
      }
      .error {
        color: #c62828;
        font-size: 0.9rem;
        margin-top: 0.5rem;
      }
      .finding {
        display: flex;
        align-items: flex-start;
        gap: 0.75rem;
        padding: 0.6rem 0;
        border-bottom: 1px solid rgba(0, 0, 0, 0.08);
      }
      .finding:last-of-type {
        border-bottom: none;
      }
      .fmeta {
        flex: 1;
      }
      .fname {
        font-weight: 500;
      }
      .who {
        list-style: none;
        margin: 0.25rem 0 0;
        padding: 0;
      }
      .who li {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        font-size: 0.8rem;
      }
      .user {
        font-weight: 500;
      }
      .levels {
        color: rgba(0, 0, 0, 0.6);
      }
      .badge.denied {
        font-size: 0.68rem;
        background: #fdecea;
        color: #c62828;
        border-radius: 10px;
        padding: 0.05rem 0.45rem;
      }
    `,
  ],
})
export class PermissionMigrationDialogComponent implements OnInit {
  private readonly api = inject(PermissionMigrationService);

  readonly findings = signal<PermissionMigrationFinding[]>([]);
  readonly loading = signal(true);
  readonly busyId = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);

  ngOnInit(): void {
    this.reload();
  }

  /** DENIED reads as "—" in the "from" column (a deny is "nothing"). */
  levelLabel(level: string): string {
    return level === 'DENIED' ? '—' : level;
  }

  resolve(f: PermissionMigrationFinding): void {
    this.busyId.set(f.allocatableId);
    this.errorMessage.set(null);
    this.api.resolve(f.allocatableId).subscribe({
      next: (remaining) => {
        this.findings.set(remaining);
        this.busyId.set(null);
      },
      error: (err) => {
        console.warn('[permission-migration] resolve failed', err);
        this.busyId.set(null);
        this.errorMessage.set('Could not resolve this resource.');
      },
    });
  }

  private reload(): void {
    this.loading.set(true);
    this.api.findings().subscribe({
      next: (list) => {
        this.findings.set(list);
        this.loading.set(false);
      },
      error: (err) => {
        console.warn('[permission-migration] load failed', err);
        this.loading.set(false);
        this.errorMessage.set('Could not load the migration worklist.');
      },
    });
  }
}
