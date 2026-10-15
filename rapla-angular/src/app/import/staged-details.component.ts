import { Component, effect, inject, input, signal } from '@angular/core';
import { MAT_BOTTOM_SHEET_DATA, MatBottomSheet } from '@angular/material/bottom-sheet';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';

import { copyText } from '../common/copy-text';
import { TPipe, t as tr } from '../i18n/i18n.service';
import { ChangedFieldsComponent } from './changed-fields.component';
import { ImportWorklistService, type StagedEventDetails } from './import-worklist.service';

/** The source record of one staged item (WP23x C4): every field the Halde row carries, plus
 *  the changed-fields block for a changed item. Loads itself; nothing visible → nothing shown. */
@Component({
  selector: 'app-staged-details',
  imports: [TPipe, ChangedFieldsComponent, MatProgressSpinnerModule, MatIconModule],
  template: `
    <div class="details" role="presentation" (click)="$event.stopPropagation()">
      @if (details(); as d) {
        <table>
          @for (f of d.fields; track f.key) {
            <tr>
              <th>{{ f.key }}</th>
              <td>
                @if (f.value) {
                  {{ f.value }}
                  <button
                    type="button"
                    class="copy"
                    [title]="'import_copy' | t"
                    [attr.aria-label]="'import_copy' | t"
                    (click)="copy(f.value)"
                  >
                    <mat-icon inline>content_copy</mat-icon>
                  </button>
                }
              </td>
            </tr>
          }
        </table>
        @if (changedSince()) {
          <app-changed-fields [changedSince]="changedSince()" />
        }
      } @else if (loaded()) {
        <p>{{ 'import_details_unavailable' | t }}</p>
      } @else {
        <div class="loading">
          <mat-progress-spinner mode="indeterminate" diameter="20" />
          <span>{{ 'view_loading' | t }}</span>
        </div>
      }
    </div>
  `,
  styles: `
    /* fixed minimum so the popover doesn't jump when the data arrives (user 2026-10-07) */
    .details {
      padding: 8px 16px;
      font-size: 13px;
      min-width: 320px;
      min-height: 120px;
      box-sizing: border-box;
    }
    .loading {
      display: flex;
      align-items: center;
      gap: 8px;
      min-height: 104px;
      color: var(--mat-sys-on-surface-variant);
    }
    td {
      overflow-wrap: anywhere;
    }
    /* a copy symbol behind every value (user 2026-10-07) */
    .copy {
      border: none;
      background: none;
      padding: 0 0 0 4px;
      cursor: pointer;
      color: var(--mat-sys-on-surface-variant);
      vertical-align: middle;
    }
    .copy:hover {
      color: var(--mat-sys-primary, #3f51b5);
    }
    th {
      text-align: left;
      padding-right: 12px;
      font-weight: 500;
      color: var(--mat-sys-on-surface-variant);
      vertical-align: top;
    }
  `,
})
export class StagedDetailsComponent {
  private readonly service = inject(ImportWorklistService);
  private readonly snackBar = inject(MatSnackBar);
  readonly sourceItemId = input.required<string>();

  copy(text: string): void {
    void copyText(text);
    this.snackBar.open(tr('import_copied', text), undefined, { duration: 1500 });
  }
  readonly changedSince = input<string | null>(null);
  readonly details = signal<StagedEventDetails | null>(null);
  readonly loaded = signal(false);

  private readonly loader = effect(() => {
    const id = this.sourceItemId();
    this.loaded.set(false);
    this.service.stagedEventDetails(id).subscribe((d) => {
      this.details.set(d);
      this.loaded.set(true);
    });
  });
}

/** Bottom-sheet host for small screens. */
@Component({
  selector: 'app-staged-details-sheet',
  imports: [StagedDetailsComponent],
  template: `<app-staged-details
    [sourceItemId]="data.sourceItemId"
    [changedSince]="data.changedSince"
  />`,
})
export class StagedDetailsSheetComponent {
  readonly data = inject<{ sourceItemId: string; changedSince: string | null }>(
    MAT_BOTTOM_SHEET_DATA,
  );
}

/** The Dualis number as a link to the details — a popover on desktop, a bottom sheet below
 *  720 px (CSS decides which button shows). */
@Component({
  selector: 'app-staged-details-button',
  imports: [MatMenuModule, TPipe, StagedDetailsComponent],
  template: `
    <button
      type="button"
      class="info info-desktop"
      [title]="'import_details' | t"
      [matMenuTriggerFor]="menu"
    >
      {{ label() }}
    </button>
    <button
      type="button"
      class="info info-mobile"
      [title]="'import_details' | t"
      (click)="openSheet()"
    >
      {{ label() }}
    </button>
    <mat-menu #menu="matMenu" class="staged-details-menu">
      <ng-template matMenuContent>
        <app-staged-details [sourceItemId]="sourceItemId()" [changedSince]="changedSince()" />
      </ng-template>
    </mat-menu>
  `,
  styles: `
    .info {
      border: none;
      background: none;
      cursor: pointer;
      padding: 0;
      font-family: monospace;
      font-size: 12px;
      color: var(--mat-sys-primary, #3f51b5);
      text-decoration: underline dotted;
      white-space: nowrap;
    }
    .info-mobile {
      display: none;
    }
    @media (max-width: 719px) {
      .info-desktop {
        display: none;
      }
      .info-mobile {
        display: inline;
      }
    }
  `,
})
export class StagedDetailsButtonComponent {
  private readonly sheet = inject(MatBottomSheet);
  /** The link text — the item's Dualis number. */
  readonly label = input.required<string>();
  readonly sourceItemId = input.required<string>();
  readonly changedSince = input<string | null>(null);

  openSheet(): void {
    this.sheet.open(StagedDetailsSheetComponent, {
      data: { sourceItemId: this.sourceItemId(), changedSince: this.changedSince() },
    });
  }
}
