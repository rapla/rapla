import { Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';

import { TPipe, t as tr } from '../i18n/i18n.service';
import { FilterStore } from '../state/filter-store';
import { ResourceSelectionStore } from '../state/resource-selection-store';
import { ViewStateStore } from '../state/view-state-store';
import { currentSemester, openItemsOfGroups, windowSemesterDate } from './import-models';
import {
  ImportSyncDialogComponent,
  type ImportSyncDialogData,
} from './import-sync-dialog.component';
import { effectiveCourseIds, MAX_SYNC_COURSES, syncScope } from './import-sync-scope';
import { ImportWorklistService } from './import-worklist.service';

/** dhbwrapla PRD 004 § H5a — "↻ Dualis-Sync: <Kurs> · <Semester> linked/total" in the filter row,
 *  right after the chips. Greyed without a Kurs chip and from 20 effective Kurse on. */
@Component({
  selector: 'app-import-sync-button',
  imports: [MatButtonModule, MatIconModule, TPipe],
  template: `
    @if (available()) {
      @switch (scope().kind) {
        @case ('none') {
          <button matButton class="import-sync" disabled>
            {{ 'import_sync_pick_course' | t: source() }}
          </button>
        }
        @case ('tooMany') {
          <button
            matButton
            class="import-sync"
            disabled
            [title]="'import_sync_too_many_tooltip' | t: maxCourses"
          >
            {{ 'import_sync_too_many' | t: source() : count() : maxCourses }}
          </button>
        }
        @default {
          <button
            matButton
            class="import-sync"
            (click)="openSync()"
            [title]="'shell_import_sync_tooltip' | t"
          >
            <mat-icon>sync</mat-icon>
            {{ 'import_sync_button' | t: source() : courseLabel() : semester() }}
            <span class="import-sync-badge" [class.done]="done()">{{ badge() }}</span>
          </button>
        }
      }
    }
  `,
  styles: `
    .import-sync {
      white-space: nowrap;
    }
    .import-sync-badge {
      margin-left: 6px;
      padding: 0 6px;
      border-radius: 10px;
      background: var(--mat-sys-surface-container-high);
      font-size: 12px;
    }
    .import-sync-badge.done {
      background: var(--mat-sys-tertiary-container);
    }
  `,
})
export class ImportSyncButtonComponent {
  private readonly worklist = inject(ImportWorklistService);
  private readonly filter = inject(FilterStore);
  private readonly resources = inject(ResourceSelectionStore);
  private readonly viewState = inject(ViewStateStore);
  private readonly dialog = inject(MatDialog);
  protected readonly maxCourses = MAX_SYNC_COURSES;

  protected readonly available = computed(() => this.worklist.sourceName() !== '');
  protected readonly source = computed(
    () => this.worklist.sourceName() || tr('import_default_source'),
  );
  private readonly chips = computed(() =>
    this.filter.entries().filter((c) => c.kind === 'resource'),
  );
  private readonly courseIds = computed(() =>
    effectiveCourseIds(
      this.chips().map((c) => c.id),
      this.resources.resources(),
    ),
  );
  protected readonly scope = computed(() => syncScope(this.courseIds()));
  protected readonly count = computed(() => this.courseIds().length);
  protected readonly semester = computed(() => {
    const w = this.viewState.window();
    return currentSemester(w ? windowSemesterDate(w) : new Date());
  });
  protected readonly courseLabel = computed(() => {
    const ids = this.courseIds();
    if (ids.length !== 1) return tr('import_sync_courses', ids.length);
    const chip = this.chips().find((c) => c.id === ids[0]);
    return chip?.label ?? this.resources.resources().find((r) => r.id === ids[0])?.label ?? ids[0];
  });
  private readonly openCount = computed(
    () =>
      openItemsOfGroups(this.worklist.worklist()?.items ?? [], this.courseIds(), this.semester())
        .length,
  );
  /** linked/total; "!" = load failed — must never look like a real count. */
  protected readonly badge = computed(() => {
    if (this.worklist.loadFailed()) return '!';
    if (this.worklist.worklist() === null) return '…';
    const linked = this.worklist.linked().length;
    return `${linked}/${linked + this.openCount()}`;
  });
  protected readonly done = computed(
    () =>
      !this.worklist.loadFailed() && this.openCount() === 0 && this.worklist.linked().length > 0,
  );

  protected openSync(): void {
    const data: ImportSyncDialogData = {
      groupIds: this.courseIds(),
      groupLabel: this.courseLabel(),
      groupNames: Object.fromEntries(
        this.courseIds().map((id) => [
          id,
          this.chips().find((c) => c.id === id)?.label ??
            this.resources.resources().find((r) => r.id === id)?.label ??
            id,
        ]),
      ),
      semester: this.semester(),
      defaultStart: `${(this.viewState.window()?.from ?? new Date().toISOString()).slice(0, 10)}T08:00:00`,
    };
    this.dialog
      .open(ImportSyncDialogComponent, { data, autoFocus: false, maxWidth: '96vw' })
      .afterClosed()
      .subscribe((outcome) => {
        if (outcome === 'parked') this.viewState.setRenderMode('week');
      });
  }
}
