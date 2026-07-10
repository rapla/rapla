import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { ClassificationSchemaService } from './classification-schema.service';
import type { AttributeDescriptor } from './classification-schema';

/**
 * A single attribute edit, routed through the HOST's mutation funnel —
 * the event sheet passes label/coalesceKey into mutateDraft (PRD 091 D5),
 * a future allocatable editor does the same with its own draft.
 */
export interface ClassificationPatch {
  key: string;
  value: unknown;
  label: string;
  coalesceKey: string | null;
}

type Widget = 'text' | 'number' | 'checkbox' | 'select' | 'date' | 'readonly';

/**
 * PRD 096 Phase 2 — reusable, CONTROLLED classification form. Renders the
 * DynamicType-driven attribute fields for whatever `typeKey` the host puts
 * in; never holds its own copy of the values. Widget mapping per PRD 035 §5
 * (v1 subset — tree-category / allocatable / list attributes render
 * read-only and ride along untouched).
 */
@Component({
  selector: 'app-classification-edit',
  standalone: true,
  imports: [FormsModule, NgTemplateOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="cgrid">
      @for (d of mainDescriptors(); track d.key) {
        <ng-container [ngTemplateOutlet]="fieldTpl" [ngTemplateOutletContext]="{ $implicit: d }" />
      }
    </div>

    @if (additionalDescriptors().length > 0) {
      <div class="expander" [class.open]="showExtended()">
        <button
          type="button"
          class="exp-toggle"
          [attr.aria-expanded]="showExtended()"
          (click)="showExtended.set(!showExtended())"
        >
          <span class="chev" aria-hidden="true">▸</span>
          Weitere Felder
          <span class="count">({{ additionalDescriptors().length }})</span>
        </button>
        @if (showExtended()) {
          <div class="cgrid exp-body">
            @for (d of additionalDescriptors(); track d.key) {
              <ng-container
                [ngTemplateOutlet]="fieldTpl"
                [ngTemplateOutletContext]="{ $implicit: d }"
              />
            }
          </div>
        }
      </div>
    }

    <ng-template #fieldTpl let-d>
      @if (widgetOf(d) === 'readonly') {
        <div class="field readonly span-full">
          <span class="lab">{{ d.label }}</span>
          <span class="ro" title="Bearbeitung folgt (PRD 096)">{{
            displayValue(values()[d.key])
          }}</span>
        </div>
      } @else if (widgetOf(d) === 'checkbox') {
        <div class="field check">
          <label class="chk" [for]="'cls-' + d.key">
            <input
              [id]="'cls-' + d.key"
              type="checkbox"
              [ngModel]="values()[d.key] === true"
              (ngModelChange)="emitPatch(d, $event, null)"
              [disabled]="disabled()"
            />
            <span class="lab"
              >{{ d.label }}
              @if (d.required) {
                <span class="req"> *</span>
              }
            </span>
          </label>
        </div>
      } @else {
        <div class="field">
          <div class="ctrl" [class.select]="widgetOf(d) === 'select'">
            @switch (widgetOf(d)) {
              @case ('text') {
                <input
                  [id]="'cls-' + d.key"
                  type="text"
                  [ngModel]="asString(values()[d.key])"
                  (ngModelChange)="emitPatch(d, $event === '' ? null : $event, 'values:' + d.key)"
                  [disabled]="disabled()"
                />
              }
              @case ('number') {
                <input
                  [id]="'cls-' + d.key"
                  type="number"
                  [ngModel]="values()[d.key]"
                  (ngModelChange)="
                    emitPatch(
                      d,
                      $event === null || $event === '' ? null : +$event,
                      'values:' + d.key
                    )
                  "
                  [disabled]="disabled()"
                />
              }
              @case ('date') {
                <input
                  [id]="'cls-' + d.key"
                  type="date"
                  [ngModel]="datePart(values()[d.key])"
                  (ngModelChange)="emitPatch(d, $event ? $event + 'T00:00:00' : null, null)"
                  [disabled]="disabled()"
                />
              }
              @case ('select') {
                <select
                  [id]="'cls-' + d.key"
                  [ngModel]="values()[d.key] ?? ''"
                  (ngModelChange)="emitPatch(d, $event === '' ? null : $event, null)"
                  [disabled]="disabled()"
                >
                  <!-- unset shows blank but "nothing" is NOT pickable (user decision
                     2026-07-07 — deliberate deviation from Swing's nothing_selected) -->
                  <option value="" disabled hidden></option>
                  @for (e of d.enumValues; track e.key) {
                    <option [value]="e.key">{{ e.label }}</option>
                  }
                </select>
              }
            }
          </div>
          <label class="lab" [for]="'cls-' + d.key"
            >{{ d.label }}
            @if (d.required) {
              <span class="req"> *</span>
            }
          </label>
        </div>
      }
    </ng-template>
  `,
  styles: `
    :host {
      display: block;
    }
    .cgrid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(15rem, 1fr));
      gap: 1.15rem 0.9rem;
    }
    .field {
      position: relative;
      min-width: 0;
    }
    .field.span-full {
      grid-column: 1 / -1;
    }
    .lab {
      font-size: 0.72rem;
      color: color-mix(in srgb, currentColor 62%, transparent);
    }
    .req {
      color: #b3261e;
    }
    /* dense Material-style outline box with a notched floating label */
    .ctrl {
      position: relative;
      display: flex;
      align-items: center;
      border: 1px solid color-mix(in srgb, currentColor 34%, transparent);
      border-radius: 6px;
      padding: 0.55rem 0.7rem;
      background: var(--mat-sys-surface, transparent);
      transition:
        border-color 0.12s,
        box-shadow 0.12s;
    }
    .ctrl:focus-within {
      border-color: var(--mat-sys-primary, #0061a4);
      box-shadow: inset 0 0 0 1px var(--mat-sys-primary, #0061a4);
    }
    .ctrl.select::after {
      content: '▾';
      margin-left: auto;
      opacity: 0.6;
      pointer-events: none;
    }
    .field:not(.check):not(.readonly) > .lab {
      position: absolute;
      top: -0.55rem;
      left: 0.55rem;
      padding: 0 0.3rem;
      background: var(--mat-sys-surface, #fff);
      white-space: nowrap;
    }
    .ctrl:focus-within ~ .lab {
      color: var(--mat-sys-primary, #0061a4);
    }
    .ctrl input,
    .ctrl select {
      font: inherit;
      border: none;
      outline: none;
      background: transparent;
      color: inherit;
      width: 100%;
      padding: 0;
    }
    .ctrl select {
      appearance: none;
      cursor: pointer;
    }
    .field.check {
      display: flex;
      align-items: center;
    }
    .field.check .chk {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      cursor: pointer;
    }
    .field.check .lab {
      font-size: 0.9rem;
      color: inherit;
    }
    .field.check input[type='checkbox'] {
      width: 18px;
      height: 18px;
    }
    .field.readonly {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }
    .ro {
      padding: 6px 0;
      opacity: 0.75;
    }
    /* extended-fields expander */
    .expander {
      margin-top: 1.1rem;
    }
    .exp-toggle {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      width: 100%;
      font: inherit;
      font-size: 0.85rem;
      font-weight: 500;
      cursor: pointer;
      color: var(--mat-sys-primary, #0061a4);
      background: transparent;
      border: none;
      border-top: 1px solid color-mix(in srgb, currentColor 16%, transparent);
      padding: 0.55rem 0.2rem;
    }
    .exp-toggle .chev {
      transition: transform 0.18s;
    }
    .expander.open .exp-toggle .chev {
      transform: rotate(90deg);
    }
    .exp-toggle .count {
      color: color-mix(in srgb, currentColor 55%, transparent);
      font-weight: 400;
    }
    .exp-body {
      margin-top: 0.5rem;
    }
    @media (prefers-reduced-motion: reduce) {
      .ctrl,
      .chev {
        transition: none;
      }
    }
  `,
})
export class ClassificationEditComponent {
  private readonly schema = inject(ClassificationSchemaService);

  readonly typeKey = input.required<string>();
  readonly values = input.required<Record<string, unknown>>();
  readonly disabled = input(false);
  /** Attributes the host renders itself (e.g. the sheet's title fields). */
  readonly excludeKeys = input<string[]>([]);
  /** Render ONLY these attributes (the sheet's header title strip) — full
   *  widget map applies, so a category title gets its select, not raw text. */
  readonly onlyKeys = input<string[] | null>(null);

  readonly patch = output<ClassificationPatch>();

  /** Extended (@editView additional) fields collapsed by default. */
  readonly showExtended = signal(false);

  readonly descriptors = computed<AttributeDescriptor[]>(() => {
    const map = this.schema.typeMap();
    const type = map?.get(this.typeKey());
    if (!type) return [];
    const only = this.onlyKeys();
    const excluded = new Set(this.excludeKeys());
    // @editView: no-view attributes are admin-hidden in editors (Swing parity);
    // additional renders like main for now (PRD 096 — collapsible details later).
    return type.attributes.filter(
      (a) =>
        (only === null ? !excluded.has(a.key) : only.includes(a.key)) && a.editView !== 'no-view',
    );
  });

  /** Always-visible fields — everything except @editView additional. */
  readonly mainDescriptors = computed<AttributeDescriptor[]>(() =>
    this.descriptors().filter((d) => d.editView !== 'additional'),
  );

  /** Extended fields — @editView additional, shown under the expander. */
  readonly additionalDescriptors = computed<AttributeDescriptor[]>(() =>
    this.descriptors().filter((d) => d.editView === 'additional'),
  );

  constructor() {
    this.schema.load();
  }

  widgetOf(d: AttributeDescriptor): Widget {
    if (d.list) return 'readonly';
    switch (d.valueType) {
      case 'STRING':
        return 'text';
      case 'INT':
        return 'number';
      case 'BOOLEAN':
        return 'checkbox';
      case 'DATE':
        return 'date';
      case 'CATEGORY':
        return d.enumValues ? 'select' : 'readonly';
      default:
        return 'readonly';
    }
  }

  asString(v: unknown): string {
    return typeof v === 'string' ? v : v == null ? '' : String(v);
  }

  /** LocalDateTime wire value → the native date input's yyyy-MM-dd. */
  datePart(v: unknown): string {
    return typeof v === 'string' ? v.slice(0, 10) : '';
  }

  displayValue(v: unknown): string {
    if (v == null || v === '') return '—';
    if (Array.isArray(v)) return v.length === 0 ? '—' : v.map((x) => String(x)).join(', ');
    return String(v);
  }

  emitPatch(d: AttributeDescriptor, value: unknown, coalesceKey: string | null): void {
    this.patch.emit({ key: d.key, value, label: d.label, coalesceKey });
  }
}
