import { Injectable, inject } from '@angular/core';
import { Observable, map, of, switchMap } from 'rxjs';

import {
  normalizeClassificationValues,
  valueSelections,
} from '../classification/classification-schema';
import { ClassificationSchemaService } from '../classification/classification-schema.service';
import { GraphqlService } from '../graphql/graphql.service';
import { toLocalDateTime } from '../graphql/local-date-time';
import type { MutationResult } from '../graphql/mutation-result';

/**
 * PRD 096 Phase 4 — load/save for the allocatable edit dialog. Same two-step
 * shape as EventDataService: shell first, then the full-value classification
 * read built from the parsed SDL descriptors. Much simpler than events —
 * no appointments/allocations, classification only (PRD 063's
 * updateAllocatable is classification-only by design).
 */

export interface AllocatableDraft {
  id: string;
  typeKey: string;
  values: Record<string, unknown>;
  /** LocalDateTime for updateAllocatable's expectedLastChanged. */
  lastChanged: string | null;
}

export interface LoadedAllocatable {
  draft: AllocatableDraft;
  displayName: string;
  typeName: string;
  /** RESOURCE | PERSON — the type select only offers same-kind targets. */
  classificationType: string;
  canModify: boolean;
}

interface ShellWire {
  allocatable: {
    id: string;
    displayName: string;
    lastModifiedAt: string | null;
    canModify: boolean;
    classification: { typeKey: string; type: { name: string; classificationType: string } };
  } | null;
}

const SHELL_QUERY = `
  query ($id: ID!) {
    allocatable(id: $id) {
      id
      displayName
      lastModifiedAt
      canModify
      classification { typeKey type { name classificationType } }
    }
  }`;


@Injectable({ providedIn: 'root' })
export class AllocatableDataService {
  private readonly gql = inject(GraphqlService);
  private readonly schema = inject(ClassificationSchemaService);

  load(id: string): Observable<LoadedAllocatable | null> {
    return this.gql.query<ShellWire>(SHELL_QUERY, { id }).pipe(
      switchMap((resp) => {
        const shell = resp.data?.allocatable;
        if (!shell) return of(null);
        return this.classificationValues(id, shell.classification.typeKey).pipe(
          map((values) => ({
            draft: {
              id: shell.id,
              typeKey: shell.classification.typeKey,
              values,
              lastChanged: toLocalDateTime(shell.lastModifiedAt),
            },
            displayName: shell.displayName,
            typeName: shell.classification.type.name,
            classificationType: shell.classification.type.classificationType,
            canModify: shell.canModify,
          })),
        );
      }),
    );
  }

  private classificationValues(id: string, typeKey: string): Observable<Record<string, unknown>> {
    return this.schema.load().pipe(
      switchMap((types) => {
        const type = types.get(typeKey);
        if (!type || type.attributes.length === 0) return of({});
        const fragment = `... on ${typeKey}Classification { ${valueSelections(type.attributes)} }`;
        const doc = `query ($id: ID!) { allocatable(id: $id) { classification { ${fragment} } } }`;
        return this.gql
          .query<{ allocatable: { classification: Record<string, unknown> } | null }>(doc, { id })
          .pipe(
            map((resp) =>
              normalizeClassificationValues(resp.data?.allocatable?.classification ?? {}),
            ),
          );
      }),
    );
  }

  /** Type options for the select — same classificationType as the loaded allocatable. */
  typeOptions(classificationType: string): Observable<{ key: string; name: string }[]> {
    return this.gql
      .query<{ types: { key: string; name: string; classificationType: string }[] }>(
        `query { types { key name classificationType } }`,
      )
      .pipe(
        map((resp) =>
          (resp.data?.types ?? [])
            .filter((t) => t.classificationType === classificationType)
            .map((t) => ({ key: t.key, name: t.name })),
        ),
      );
  }

  save(draft: AllocatableDraft): Observable<MutationResult<{ id: string }>> {
    const input = {
      typeKey: draft.typeKey,
      classification: { [draft.typeKey]: { ...draft.values } },
    };
    return this.gql
      .mutate<{ updateAllocatable: { id: string } }>(
        `mutation ($id: ID!, $input: UpdateAllocatableInput!, $expected: LocalDateTime) {
           updateAllocatable(id: $id, input: $input, expectedLastChanged: $expected) { id }
         }`,
        { id: draft.id, input, expected: draft.lastChanged },
      )
      .pipe(map((r) => (r.kind === 'ok' ? { kind: 'ok', data: r.data.updateAllocatable } : r)));
  }
}
