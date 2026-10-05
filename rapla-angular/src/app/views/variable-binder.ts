import type { ViewVariable } from '../graphql/graphql.service';
import { scopedResourceId, type FilterEntry } from '../state/filter-store';

/**
 * Type-driven variable binder. The GUI owns the LOGIC of how each rapla input
 * type is filled from ambient state; the view's variable signature ({@code name}
 * + {@code type}) says only WHICH variables exist. Each variable is filled by a
 * filler keyed on its TYPE (not its name) — so {@code $resourceFilter} and
 * {@code $rooms} bind identically, and a view that declares two ResourceFilter
 * sinks gets the selection in BOTH (no special-casing, no required-var failures).
 *
 * Unknown types are left unset → the server falls back to the query text's own
 * variable defaults ({@code $limit: Int = 10}) and the {@code @window} fill (a
 * view's stored defaultVariables are authoring example data, never merged at
 * runtime — PRD 097 D8). New types (conflict ids, user, …) are added here as new
 * fillers; views need no change. This is the implicit contract: the meaning of
 * the rapla input types.
 */

/** The ambient state the GUI binds from (grows: groups, conflicts, …). */
export interface SelectionContext {
  window: { from: string; to: string } | null;
  /** Ids of the currently-selected resources (chips). */
  resourceIds: string[];
  /** Every `user` chip → ReservationFilter.ownerIn, unioned server-side with the resources (PRD 123 D10). */
  ownerIds?: string[];
  /** {@code extensions.view.page.endCursor} of the rows on screen → {@code $after: String} (load more). */
  after?: string;
  /** Header click on a server-sortable column → {@code [BlockSort!]} (PRD 074 § Sort × cursor). */
  sort?: { field: string; dir: string }[];
}

/**
 * The scope the chips carry: resource ids and owner (user) ids; event chips are navigation, not scope. A conflict or
 * request chip scopes its resource (PRD 128 D1).
 */
export function scopeOf(chips: readonly FilterEntry[]): {
  resourceIds: string[];
  ownerIds: string[];
} {
  return {
    resourceIds: [...new Set(chips.map(scopedResourceId).filter((id) => id !== null))],
    ownerIds: chips.filter((c) => c.kind === 'user').map((c) => c.id),
  };
}

/** Strip GraphQL type wrappers ({@code !}, {@code [ ]}) to the base type name. */
function baseType(type: string): string {
  return type.replace(/[![\]]/g, '');
}

function fillByType(name: string, type: string, ctx: SelectionContext): unknown | undefined {
  switch (baseType(type)) {
    case 'String':
      return name === 'after' ? ctx.after : undefined;
    case 'BlockSort':
      return ctx.sort;
    case 'ReservationFilter': {
      if (!ctx.window) return undefined; // first load → server merges its default
      const filter: Record<string, unknown> = { from: ctx.window.from, to: ctx.window.to };
      // Selection narrows the reservation search via resourceMatching (an
      // ResourceFilter — future-proof for groups, not just ids).
      if (ctx.resourceIds.length) filter['resourceMatching'] = { idIn: ctx.resourceIds };
      // User scopes → events owned by those users, OR-ed with the resources server-side.
      if (ctx.ownerIds?.length) filter['ownerIn'] = ctx.ownerIds;
      return filter;
    }
    case 'ResourceFilter':
      return ctx.resourceIds.length ? { idIn: ctx.resourceIds } : undefined;
    // PRD 128 D7 — the Prüfen views: conflicts / requests on the resources the chips scope.
    case 'ConflictFilter':
      if (!ctx.resourceIds.length) return undefined;
      return ctx.window
        ? { resourceIdsIn: ctx.resourceIds, from: ctx.window.from, to: ctx.window.to }
        : { resourceIdsIn: ctx.resourceIds };
    case 'ResourceRequestFilter':
      return ctx.resourceIds.length ? { resourceIdsIn: ctx.resourceIds } : undefined;
    default:
      return undefined; // no filler → leave unset (server default)
  }
}

export function buildVariablesByType(
  variables: ViewVariable[],
  ctx: SelectionContext,
): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const variable of variables) {
    const value = fillByType(variable.name, variable.type, ctx);
    if (value !== undefined) out[variable.name] = value;
  }
  return out;
}
