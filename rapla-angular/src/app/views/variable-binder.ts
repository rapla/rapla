import type { ViewVariable } from '../graphql/graphql.service';

/**
 * Type-driven variable binder. The GUI owns the LOGIC of how each rapla input
 * type is filled from ambient state; the view's variable signature ({@code name}
 * + {@code type}) says only WHICH variables exist. Each variable is filled by a
 * filler keyed on its TYPE (not its name) — so {@code $allocatableFilter} and
 * {@code $rooms} bind identically, and a view that declares two AllocatableFilter
 * sinks gets the selection in BOTH (no special-casing, no required-var failures).
 *
 * Unknown types are left unset → the server's stored defaults apply (graceful).
 * New types (conflict ids, user, …) are added here as new fillers; views need no
 * change. This is the implicit contract: the meaning of the rapla input types.
 */

/** The ambient state the GUI binds from (grows: groups, conflicts, user, …). */
export interface SelectionContext {
  window: { from: string; to: string } | null;
  /** Ids of the currently-selected resources (chips). */
  resourceIds: string[];
}

/** Strip GraphQL type wrappers ({@code !}, {@code [ ]}) to the base type name. */
function baseType(type: string): string {
  return type.replace(/[![\]]/g, '');
}

function fillByType(type: string, ctx: SelectionContext): unknown | undefined {
  switch (baseType(type)) {
    case 'ReservationFilter': {
      if (!ctx.window) return undefined; // first load → server merges its default
      const filter: Record<string, unknown> = { from: ctx.window.from, to: ctx.window.to };
      // Selection narrows the reservation search via allocatableMatching (an
      // AllocatableFilter — future-proof for groups, not just ids).
      if (ctx.resourceIds.length) filter['allocatableMatching'] = { idIn: ctx.resourceIds };
      return filter;
    }
    case 'AllocatableFilter':
      return ctx.resourceIds.length ? { idIn: ctx.resourceIds } : undefined;
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
    const value = fillByType(variable.type, ctx);
    if (value !== undefined) out[variable.name] = value;
  }
  return out;
}
