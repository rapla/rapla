import { Injectable, InjectionToken, inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';

import { forkJoin } from 'rxjs';

import type { EntityRef, RowContext } from './row-context';
import { EventSheetComponent, type EventSheetDialogData } from '../event/event-sheet.component';
import { applyDeleteScope, deleteScopeOptions } from './delete-scope';
import {
  DeleteScopeDialogComponent,
  type DeleteScopeDialogData,
} from './delete-scope-dialog.component';
import { EventDataService, type LoadedEvent } from '../event/event-data.service';
import { UndoToastService } from '../actions/undo-toast.service';
import { buildBulkDeleteCommand, buildDeleteCommand } from '../actions/event-commands';
import { GraphqlService } from '../graphql/graphql.service';

/**
 * PRD 094 D3 — the ObjectMenuFactory analog: providers contribute row menu
 * items and dispatch on the typed row subject (D4). Registered as a plain
 * multi-provider list; no plugin system.
 */
export interface RowMenuItem {
  id: string;
  label: string;
  run: () => void;
}

export interface RowMenuProvider {
  items(ctx: RowContext): RowMenuItem[];
}

export const ROW_MENU_PROVIDERS = new InjectionToken<readonly RowMenuProvider[]>(
  'ROW_MENU_PROVIDERS',
);

/** Phase 1: Bearbeiten/Anzeigen; Phase 2: Löschen with the Swing scope dialog
 *  + undo toast — for reservation-subject rows. */
@Injectable()
export class EventRowMenuProvider implements RowMenuProvider {
  private readonly dialog = inject(MatDialog);
  private readonly data = inject(EventDataService);
  private readonly toast = inject(UndoToastService);
  private readonly gql = inject(GraphqlService);

  items(ctx: RowContext): RowMenuItem[] {
    if (ctx.rows.length > 1) return this.multiItems(ctx);
    const subject = ctx.primary;
    if (subject?.kind !== 'reservation') return [];
    const items: RowMenuItem[] = [];
    if (subject.canModify) {
      items.push({ id: 'edit', label: 'Bearbeiten', run: () => this.open({ id: subject.id }) });
    }
    items.push({
      id: 'view',
      label: 'Anzeigen',
      run: () => this.open({ id: subject.id, readOnly: true }),
    });
    // Swing parity: exception blocks (already-skipped occurrences) offer no delete.
    if (subject.canModify && !ctx.block.isException) {
      items.push({ id: 'delete', label: 'Löschen', run: () => this.deleteFlow(ctx) });
    }
    return items;
  }

  /** PRD 099 Phase 3 — bulk whole-event delete over the selection. OQ1 subset
   *  wins (Swing menu-layer parity): the action targets the deletable subset,
   *  the label names the partial scope ("Löschen (k von N)"). Blocks of the
   *  same event dedupe to one delete. */
  private multiItems(ctx: RowContext): RowMenuItem[] {
    const seen = new Set<string>();
    const events = ctx.subjects.filter(
      (s): s is EntityRef => s.kind === 'reservation' && !seen.has(s.id) && Boolean(seen.add(s.id)),
    );
    const deletable = events.filter((s) => s.canModify);
    if (deletable.length === 0) return [];
    const label =
      deletable.length === events.length
        ? `Löschen (${events.length})`
        : `Löschen (${deletable.length} von ${events.length})`;
    return [
      {
        id: 'delete-selection',
        label,
        run: () => this.bulkDeleteFlow(deletable.map((s) => s.id)),
      },
    ];
  }

  private bulkDeleteFlow(ids: string[]): void {
    forkJoin(ids.map((id) => this.data.load(id))).subscribe((loadedAll) => {
      const drafts = loadedAll.filter((l): l is LoadedEvent => !!l).map((l) => l.draft);
      if (drafts.length === 0) return;
      const noun = drafts.length === 1 ? 'Veranstaltung' : 'Veranstaltungen';
      this.dialog
        .open(DeleteScopeDialogComponent, {
          data: {
            eventName: `${drafts.length} ${noun}`,
            options: [{ scope: 'event', label: 'Ganze Veranstaltungen' }],
          } satisfies DeleteScopeDialogData,
          width: '420px',
          autoFocus: false,
        })
        .afterClosed()
        .subscribe((scope) => {
          if (!scope) return;
          this.toast.run(buildBulkDeleteCommand(this.gql, this.data, drafts));
        });
    });
  }

  private deleteFlow(ctx: RowContext): void {
    const subject = ctx.primary;
    if (!subject) return;
    this.data.load(subject.id).subscribe((loaded) => {
      if (!loaded) return;
      const draft = loaded.draft;
      const options = deleteScopeOptions(draft, ctx.block);
      const eventName = String(draft.values['name'] ?? '') || 'Veranstaltung';
      this.dialog
        .open(DeleteScopeDialogComponent, {
          data: { eventName, options } satisfies DeleteScopeDialogData,
          width: '420px',
          autoFocus: false,
        })
        .afterClosed()
        .subscribe((scope) => {
          if (!scope) return;
          const action = applyDeleteScope(draft, scope, ctx.block);
          this.toast.run(buildDeleteCommand(this.gql, this.data, draft, action));
        });
    });
  }

  private open(data: EventSheetDialogData): void {
    this.dialog.open(EventSheetComponent, {
      data,
      width: '960px',
      maxWidth: '95vw',
      height: '90vh',
      restoreFocus: false,
    });
  }
}
