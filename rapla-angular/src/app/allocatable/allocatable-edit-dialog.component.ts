import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

import { FormsModule } from '@angular/forms';

import {
  ClassificationEditComponent,
  type ClassificationPatch,
} from '../classification/classification-edit.component';
import { EntityIdChipComponent } from '../common/entity-id-chip.component';
import { remapValues } from '../classification/classification-schema';
import { ClassificationSchemaService } from '../classification/classification-schema.service';
import type { MutationIssue } from '../graphql/mutation-result';
import { AllocatableDataService, type AllocatableDraft } from './allocatable-data.service';

export interface AllocatableEditDialogData {
  id: string;
  /** "Anzeigen" — force read-only regardless of canModify. */
  readOnly?: boolean;
}

/**
 * PRD 096 Phase 4 — the allocatable editor: a thin MatDialog around the
 * reusable classification component. Deliberately simpler than the event
 * sheet: no appointments/allocations, no type change (the server still
 * rejects it for allocatables — PRD 063), no memento history v1 (a handful
 * of flat fields; Abbrechen is the undo). `name` is NOT special-cased —
 * for resources it is an ordinary attribute rendered by the component.
 */
@Component({
  selector: 'app-allocatable-edit-dialog',
  standalone: true,
  imports: [ClassificationEditComponent, EntityIdChipComponent, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="dlg">
      <app-entity-id-chip class="idchip" [id]="entityId" />
      @if (loading()) {
        <p class="hint">Lade Ressource…</p>
      } @else if (notFound()) {
        <h2>Nicht gefunden</h2>
        <p class="hint">Die Ressource existiert nicht oder ist für dich nicht sichtbar.</p>
        <div class="bar">
          <button type="button" (click)="cancel()">Zurück</button>
        </div>
      } @else if (draft(); as d) {
        <div class="head">
          <h2>{{ displayName() }}</h2>
          @if (editable() && typeOptions().length > 1) {
            <label class="typesel">
              <span class="hint">Typ</span>
              <select [ngModel]="d.typeKey" (ngModelChange)="setTypeKey($event)">
                @for (t of typeOptions(); track t.key) {
                  <option [value]="t.key">{{ t.name }}</option>
                }
              </select>
            </label>
          } @else {
            <span class="hint">{{ typeName() }}</span>
          }
          @if (!editable()) {
            <span class="pill">nur ansehen</span>
          }
        </div>
        <div class="body">
          <app-classification-edit
            [typeKey]="d.typeKey"
            [values]="d.values"
            [disabled]="!editable()"
            (patch)="applyPatch($event)"
          />
          @for (issue of issues(); track issue.path + issue.code) {
            <p class="err">{{ issue.code }} · {{ issue.path }} — {{ issue.message }}</p>
          }
          @if (notice()) {
            <p class="hint">{{ notice() }}</p>
          }
        </div>
        <div class="bar">
          <span class="hint grow">
            @if (dirty()) {
              ungespeicherte Änderungen
            }
          </span>
          <button type="button" (click)="cancel()">Abbrechen</button>
          @if (editable()) {
            <button
              type="button"
              class="primary"
              [disabled]="saving() || !dirty()"
              (click)="save()"
            >
              {{ saving() ? 'Speichere…' : 'Speichern' }}
            </button>
          }
        </div>
      }
    </div>
  `,
  styles: `
    .dlg {
      position: relative;
      display: flex;
      flex-direction: column;
      min-width: min(520px, 90vw);
      max-height: min(85vh, 900px);
      box-sizing: border-box;
    }
    .idchip {
      position: absolute;
      top: 2px;
      right: 6px;
      z-index: 2;
    }
    .body {
      display: flex;
      flex-direction: column;
      gap: 14px;
      padding: 14px 20px;
      overflow-y: auto;
      flex: 1 1 auto;
      min-height: 0;
    }
    .head {
      display: flex;
      align-items: baseline;
      gap: 10px;
      padding: 20px 20px 0;
      flex: 0 0 auto;
    }
    .head h2 {
      margin: 0;
      font-size: 18px;
    }
    .typesel {
      display: flex;
      align-items: baseline;
      gap: 6px;
    }
    .typesel select {
      font: inherit;
      padding: 4px 6px;
      border: 1px solid color-mix(in srgb, currentColor 28%, transparent);
      border-radius: 6px;
      background: transparent;
      color: inherit;
    }
    .hint {
      opacity: 0.65;
      font-size: 13px;
    }
    .grow {
      flex: 1;
    }
    .pill {
      font-size: 11px;
      padding: 2px 8px;
      border-radius: 10px;
      background: color-mix(in srgb, currentColor 12%, transparent);
    }
    .err {
      margin: 0;
      color: #b3261e;
      font-size: 13px;
    }
    .dlg > .hint,
    .dlg > h2 {
      padding: 20px 20px 0;
    }
    .bar {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 14px 20px;
      flex: 0 0 auto;
      border-top: 1px solid color-mix(in srgb, currentColor 14%, transparent);
    }
    .bar button {
      font: inherit;
      padding: 6px 14px;
      border: 1px solid color-mix(in srgb, currentColor 28%, transparent);
      border-radius: 6px;
      background: transparent;
      color: inherit;
      cursor: pointer;
    }
    .bar button.primary {
      background: #1976d2;
      border-color: #1976d2;
      color: #fff;
    }
    .bar button:disabled {
      opacity: 0.5;
      cursor: default;
    }
  `,
})
export class AllocatableEditDialogComponent {
  private readonly data = inject(AllocatableDataService);
  private readonly schema = inject(ClassificationSchemaService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly dialogData = inject<AllocatableEditDialogData>(MAT_DIALOG_DATA);
  readonly entityId = this.dialogData.id;
  private readonly dialogRef = inject<MatDialogRef<AllocatableEditDialogComponent>>(MatDialogRef);

  readonly loading = signal(true);
  readonly notFound = signal(false);
  readonly draft = signal<AllocatableDraft | null>(null);
  readonly displayName = signal('');
  readonly typeName = signal('');
  readonly canModify = signal(false);
  readonly typeOptions = signal<{ key: string; name: string }[]>([]);
  readonly saving = signal(false);
  readonly issues = signal<MutationIssue[]>([]);
  readonly notice = signal('');

  readonly editable = computed(() => this.canModify() && this.dialogData.readOnly !== true);

  private baseline = '';
  readonly dirty = computed(() => {
    const d = this.draft();
    return d !== null && JSON.stringify({ t: d.typeKey, v: d.values }) !== this.baseline;
  });

  // eslint-disable-next-line @angular-eslint/use-lifecycle-interface
  ngOnInit(): void {
    this.data
      .load(this.dialogData.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((loaded) => {
        this.loading.set(false);
        if (!loaded) {
          this.notFound.set(true);
          return;
        }
        this.draft.set(loaded.draft);
        this.displayName.set(loaded.displayName);
        this.typeName.set(loaded.typeName);
        this.canModify.set(loaded.canModify);
        this.baseline = JSON.stringify({ t: loaded.draft.typeKey, v: loaded.draft.values });
        if (loaded.canModify && this.dialogData.readOnly !== true) {
          this.data
            .typeOptions(loaded.classificationType)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe((opts) => this.typeOptions.set(opts));
        }
      });
  }

  /**
   * PRD 096 — type change with client-side remapping (same rule as the event
   * sheet): same-key same-type values survive; the server stores the supplied
   * classification as-is and only knows the target variant's fields.
   */
  setTypeKey(key: string): void {
    const d = this.draft();
    if (!d || key === d.typeKey || !this.editable()) return;
    const map = this.schema.typeMap();
    const from = map?.get(d.typeKey)?.attributes ?? [];
    const to = map?.get(key)?.attributes;
    const values = to
      ? remapValues(d.values, from, to)
      : typeof d.values['name'] === 'string'
        ? { name: d.values['name'] }
        : {};
    this.draft.set({ ...d, typeKey: key, values });
  }

  applyPatch(p: ClassificationPatch): void {
    const d = this.draft();
    if (!d || !this.editable()) return;
    this.draft.set({ ...d, values: { ...d.values, [p.key]: p.value } });
  }

  save(): void {
    const d = this.draft();
    if (!d || this.saving()) return;
    this.saving.set(true);
    this.issues.set([]);
    this.notice.set('');
    this.data
      .save(d)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((result) => {
        this.saving.set(false);
        switch (result.kind) {
          case 'ok':
            this.dialogRef.close('saved');
            break;
          case 'concurrent':
            this.notice.set(
              'Zwischenzeitlich geändert — bitte Dialog schließen und neu öffnen (deine Eingaben gehen verloren).',
            );
            break;
          case 'transport':
            this.notice.set(result.message);
            break;
          default:
            this.issues.set(result.issues);
        }
      });
  }

  cancel(): void {
    this.dialogRef.close();
  }
}
