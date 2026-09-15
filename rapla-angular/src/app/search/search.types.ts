/** A search hit's domain category — drives icon, grouping, and default actions. */
export type SearchResultKind = 'resource' | 'event' | 'user' | 'occurrence' | 'group' | 'savedView';

/** One hit in the omnibox. {@link count} is set for group results, e.g. "Räume C-Bau (12)". */
export interface SearchResult {
  id: string;
  kind: SearchResultKind;
  label: string;
  sublabel?: string;
  color?: string;
  /** EVENT hits: LocalDateTime of the first occurrence — the week the dropdown jumps to (PRD 119 D4). */
  start?: string;
  count?: number;
}

/** A heading + its hits — the omnibox renders one block per group. */
export interface SearchResultGroup {
  kind: SearchResultKind;
  heading: string;
  results: SearchResult[];
}
