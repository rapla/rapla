import { DatePipe } from '@angular/common';
import { Component, input } from '@angular/core';

import { TPipe } from '../i18n/i18n.service';

/** One compared field: what was last applied vs. what the source says now. */
export interface ChangedField {
  field: string;
  applied: string;
  now: string;
}

/**
 * dhbwrapla PRD 004 § H5a "Geänderte Felder" — ONE component for the ⓘ details of a changed
 * item and the Übernehmen confirmation. This round has no field data (the Halde keeps only a
 * fingerprint), so it renders "geändert seit dd.mm. · Feldvergleich folgt"; a later WP only
 * plugs in `rows`.
 */
@Component({
  selector: 'app-changed-fields',
  imports: [DatePipe, TPipe],
  template: `
    <div class="changed-fields">
      <div class="title">{{ 'import_changed_fields' | t }}</div>
      @if (rows().length) {
        <table>
          <tr>
            <th>{{ 'import_changed_field' | t }}</th>
            <th>{{ 'import_changed_applied' | t }}</th>
            <th>{{ 'import_changed_now' | t }}</th>
          </tr>
          @for (r of rows(); track r.field) {
            <tr>
              <td>{{ r.field }}</td>
              <td>{{ r.applied }}</td>
              <td>{{ r.now }}</td>
            </tr>
          }
        </table>
      } @else {
        <div class="pending">
          {{ 'import_changed_pending' | t: (changedSince() | date: 'dd.MM.') }}
        </div>
      }
    </div>
  `,
  styles: `
    .title {
      font-weight: 600;
      margin-bottom: 4px;
    }
    .pending {
      font-size: 12px;
      color: var(--mat-sys-on-surface-variant);
    }
    table {
      font-size: 12px;
      border-collapse: collapse;
    }
    td,
    th {
      padding: 2px 8px 2px 0;
      text-align: left;
    }
  `,
})
export class ChangedFieldsComponent {
  readonly changedSince = input<string | null>(null);
  readonly rows = input<ChangedField[]>([]);
}
