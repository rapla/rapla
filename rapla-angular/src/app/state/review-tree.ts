import { requestChipId, type FilterEntry } from './filter-store';
import type { ResourceItem } from './resource-selection-store';
import type { TreeNode } from './resource-tree';

/** PRD 128 D5 — the `conflicts(filter:)` fields the picker needs; `reservation2` is null when masked (D6). */
export interface ConflictWire {
  id: string;
  startDate: string;
  disabled: boolean;
  description: string;
  resource: { id: string; name: string };
  reservation1: { name: string | null } | null;
  reservation2: { name: string | null } | null;
}

/** PRD 128 D5 — one reservation × requested resource; `reservation` null and no appointments when masked. */
export interface RequestWire {
  resource: { id: string; name: string };
  reservationId: string;
  reservation: { name: string | null } | null;
  appointments: { start: string }[];
}

/** One `conflictStats(groupBy: [RESOURCE, DISABLED])` bucket. */
export interface ConflictCount {
  resourceId: string;
  disabled: boolean;
  count: number;
}

/** `2026-10-05T10:00:00` → `05.10.` */
const dayMonth = (iso: string) => `${iso.slice(8, 10)}.${iso.slice(5, 7)}.`;

/** PRD 128 OQ13 — the conflict chip: scopes its resource, focuses its overlapping appointments (D1). */
export function conflictEntry(c: ConflictWire): FilterEntry {
  return {
    id: c.id,
    kind: 'conflict',
    label: `⚠ ${c.resource.name} · ${dayMonth(c.startDate)}`,
    resourceName: c.resource.name,
  };
}

/** PRD 128 OQ13 — the request chip: scopes its resource; its reservation's blocks stay normal (D1). */
export function requestEntry(r: RequestWire): FilterEntry {
  return {
    id: requestChipId(r.resource.id, r.reservationId),
    kind: 'request',
    label: `? ${r.resource.name} · ${r.reservation?.name ?? '—'}`,
    resourceName: r.resource.name,
  };
}

/** PRD 128 D1 — the date a click on a request jumps to: its first open appointment. */
export function requestFirstDate(r: RequestWire): string | null {
  return r.appointments.map((a) => a.start).sort()[0] ?? null;
}

/**
 * Type folders over resources in lean-list order (PRD 127 D6); each resource is a group node under {@code prefix}
 * whose children {@code leaves} supplies. Resources outside the caller's lean list are skipped.
 */
function folders(
  counts: ReadonlyMap<string, number>,
  resources: readonly ResourceItem[],
  prefix: string,
  leaves: (resourceId: string) => TreeNode[],
): TreeNode[] {
  const types = new Map<string, TreeNode>();
  for (const r of resources) {
    const count = counts.get(r.id);
    if (!count) continue;
    const typeKey = r.typeKey ?? '';
    let type = types.get(typeKey);
    if (!type) {
      type = {
        key: `${prefix}type:${typeKey}`,
        label: r.typeName ?? typeKey,
        kind: 'type',
        children: [],
        count: 0,
      };
      types.set(typeKey, type);
    }
    type.children.push({
      key: `${prefix}res:${r.id}`,
      label: r.label,
      kind: 'group',
      children: leaves(r.id),
      count,
    });
    type.count += count;
  }
  return [...types.values()];
}

const leaf = (key: string, label: string, entry: FilterEntry, date: string | null): TreeNode => ({
  key,
  label,
  kind: 'entry',
  entry,
  date,
  children: [],
  count: 1,
});

/**
 * PRD 128 — the Konflikte section: type → resource → conflict, the disabled ones in their own group like Swing's
 * `createConflictModel`. A resource's conflicts are there once {@code loaded} holds them (D5: loaded per resource).
 */
export function conflictTree(
  counts: readonly ConflictCount[],
  loaded: ReadonlyMap<string, readonly ConflictWire[]>,
  resources: readonly ResourceItem[],
  disabledLabel: string,
): TreeNode[] {
  const part = (disabled: boolean, prefix: string) =>
    folders(
      new Map(counts.filter((c) => c.disabled === disabled).map((c) => [c.resourceId, c.count])),
      resources,
      prefix,
      (id) =>
        (loaded.get(id) ?? [])
          .filter((c) => c.disabled === disabled)
          .map((c) =>
            leaf(
              `k:c:${c.id}`,
              `${dayMonth(c.startDate)} ${c.startDate.slice(11, 16)} ${c.reservation1?.name ?? '—'} ↔ ${
                c.reservation2?.name ?? c.description
              }`,
              conflictEntry(c),
              c.startDate,
            ),
          ),
    );
  const disabled = part(true, 'k:d:');
  const disabledCount = disabled.reduce((n, t) => n + t.count, 0);
  return [
    ...part(false, 'k:'),
    ...(disabledCount
      ? [
          {
            key: 'k:disabled',
            label: disabledLabel,
            kind: 'group' as const,
            children: disabled,
            count: disabledCount,
          },
        ]
      : []),
  ];
}

/** PRD 128 — the Ressourcenanfragen section: type → resource → one entry per requesting reservation. */
export function requestTree(
  requests: readonly RequestWire[],
  resources: readonly ResourceItem[],
): TreeNode[] {
  const byResource = new Map<string, RequestWire[]>();
  for (const r of requests)
    byResource.set(r.resource.id, [...(byResource.get(r.resource.id) ?? []), r]);
  return folders(
    new Map([...byResource].map(([id, rs]) => [id, rs.length])),
    resources,
    'q:',
    (id) =>
      (byResource.get(id) ?? []).map((r) => {
        const date = requestFirstDate(r);
        const entry = requestEntry(r);
        return leaf(
          `q:r:${entry.id}`,
          `${r.reservation?.name ?? '—'}${date ? ` · ${dayMonth(date)}` : ''}`,
          entry,
          date,
        );
      }),
  );
}

/** PRD 128 OQ16 — search in a Prüfen section keeps the resources whose name matches; the folders above recount. */
export function filterReviewTree(nodes: readonly TreeNode[], query: string): TreeNode[] {
  const q = query.trim().toLocaleLowerCase('de');
  return nodes.flatMap((n) => {
    if (n.key.includes('res:')) return n.label.toLocaleLowerCase('de').includes(q) ? [n] : [];
    const children = filterReviewTree(n.children, query);
    return children.length
      ? [{ ...n, children, count: children.reduce((sum, c) => sum + c.count, 0) }]
      : [];
  });
}
