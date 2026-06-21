/** A search hit's domain category — drives icon, grouping, and default actions. */
export type SearchResultKind = 'resource' | 'event' | 'occurrence' | 'group' | 'savedView';

/** What a result row's button does when clicked. */
export type SearchAction = 'filter-replace' | 'filter-add' | 'navigate' | 'edit' | 'load-group';

/** One hit in the omnibox. {@link count} is set for group results, e.g. "Räume C-Bau (12)". */
export interface SearchResult {
  id: string;
  kind: SearchResultKind;
  label: string;
  sublabel?: string;
  color?: string;
  actions: SearchAction[];
  count?: number;
}

/** A heading + its hits — the omnibox renders one block per group. */
export interface SearchResultGroup {
  kind: SearchResultKind;
  heading: string;
  results: SearchResult[];
}
