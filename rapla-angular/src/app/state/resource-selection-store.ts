import { Injectable, computed, effect, inject, signal, untracked } from '@angular/core';

import { GraphqlService } from '../graphql/graphql.service';
import { RecentsFavoritesService } from './recents-favorites.service';
import {
  byLabel,
  filterRows,
  rankAll,
  typeChips,
  usersMatching,
  type PickerChip,
} from './resource-picker';

/** A steppable item in the selection list. Usually a resource; a `user` item
 *  steps as an ownerEq scope chip instead of a resource filter. Defaults to
 *  `resource` when omitted (back-compat with persisted recents/favorites). */
export interface ResourceItem {
  id: string;
  label: string;
  color?: string;
  kind?: 'resource' | 'user';
  /** rapla type key (e.g. "Raum", "Kurs") — drives the type icon in the list. */
  typeKey?: string;
  /** Localized type name — the label of the type chip (PRD 119). */
  typeName?: string;
  /** Login name of a user row — the search matches it too (PRD 119). */
  username?: string;
  /** Picker group paths, one per categorization value (PRD 119 D11). */
  groupPaths?: string[][];
}

interface PickerWire {
  resources?: {
    id: string;
    name: string | null;
    classification: { typeKey: string; type: { name: string } };
    groupPaths?: string[][];
  }[];
  users?: { id: string; username: string; name: string }[];
}

/** PRD 119 D8 — the lean list: only what the picker needs, never the full resource. */
const PICKER_QUERY = `{
  resources { id kind name classification { typeKey type { name } } groupPaths }
  users { id username name }
}`;

/**
 * The persistent left ResourceSelection — the pool you pick/step resources from.
 * PRD 119: the lean resource list is loaded once; chips pick what the list shows (Alle,
 * Favoriten, Zuletzt, one chip per type); the one search field writes {@link query}.
 * {@link activeId} marks the "▶ gezeigt" item. Distinct from the FilterStore:
 * this is the candidate pool; a click here {@code replace}s the filter.
 *
 * PRD 089: recents + favorites are PER-USER SERVER state, owned by
 * {@link RecentsFavoritesService}; the store re-exposes the service signals.
 */
@Injectable({ providedIn: 'root' })
export class ResourceSelectionStore {
  private readonly lists = inject(RecentsFavoritesService);
  private readonly gql = inject(GraphqlService);

  private readonly _resources = signal<ResourceItem[]>([]);
  private readonly _users = signal<ResourceItem[]>([]);
  private readonly _activeChip = signal<string>('all');
  private readonly _activeId = signal<string | null>(null);
  private readonly _query = signal('');
  private readonly _pickerFocus = signal(0);
  /** PRD 119 P3b (user ruling A) — Alle ranks by this copy of the recents, so a click never moves its row. */
  private readonly _recentsSnapshot = signal<ResourceItem[]>([]);
  private loaded = false;

  readonly resources = this._resources.asReadonly();
  readonly users = this._users.asReadonly();
  readonly recents = this.lists.recents;
  readonly favorites = this.lists.favorites;
  readonly activeChip = this._activeChip.asReadonly();
  readonly activeId = this._activeId.asReadonly();
  /** PRD 119 D3 — the one search field writes it; the picker narrows by it. */
  readonly query = this._query.asReadonly();
  /** Bumped when the search dropdown sends the user to the picker (PRD 119 D4). */
  readonly pickerFocus = this._pickerFocus.asReadonly();

  constructor() {
    // Recents answered after the lean list: refresh the snapshot on every server (re)load, never on a push.
    effect(() => {
      this.lists.reloaded();
      untracked(() => this.snapshotRecents());
    });
  }

  readonly chips = computed<PickerChip[]>(() => [
    { key: 'all', label: 'Alle' },
    { key: 'favorites', label: '★ Favoriten' },
    { key: 'recents', label: 'Zuletzt' },
    ...typeChips(this._resources()),
  ]);

  readonly activeList = computed<ResourceItem[]>(() => {
    const chip = this._activeChip();
    switch (chip) {
      case 'all':
        return rankAll(this._resources(), this.favorites(), this._recentsSnapshot());
      case 'favorites':
        return this.favorites();
      case 'recents':
        return this.recents();
      default: {
        const typeKey = chip.slice('type:'.length);
        return this._resources()
          .filter((it) => it.typeKey === typeKey)
          .sort(byLabel);
      }
    }
  });

  /** Rows the picker shows under Alle for the current query — the dropdown's count row (PRD 119 D4). */
  readonly matchCount = computed(
    () =>
      filterRows(
        rankAll(this._resources(), this.favorites(), this._recentsSnapshot()),
        this._query(),
      ).length + usersMatching(this._users(), this._query()).length,
  );

  /** Loads the lean list once per SPA start. */
  ensureLoaded(): void {
    this.snapshotRecents();
    if (this.loaded) return;
    this.loaded = true;
    this.fetch();
  }

  /** PRD 119 OQ6 default — after the SPA's own resource edits. */
  reload(): void {
    this.loaded = true;
    this.fetch();
  }

  private fetch(): void {
    this.gql.query<PickerWire>(PICKER_QUERY).subscribe({
      next: (resp) => {
        if (!resp.data) {
          this.loaded = false; // failed load must not leave the picker empty for the session
          return;
        }
        this._resources.set(
          (resp.data.resources ?? [])
            .filter((r) => !!r.name)
            .map((r) => ({
              id: r.id,
              label: r.name as string,
              kind: 'resource',
              typeKey: r.classification.typeKey,
              typeName: r.classification.type.name,
              groupPaths: r.groupPaths ?? [],
            })),
        );
        this.snapshotRecents();
        this._users.set(
          (resp.data.users ?? []).map((u) => ({
            id: u.id,
            label: u.name || u.username,
            kind: 'user',
            username: u.username,
          })),
        );
      },
      error: () => {
        this.loaded = false;
      },
    });
  }

  setActiveChip(key: string): void {
    this.snapshotRecents();
    this._activeChip.set(key);
  }

  setQuery(query: string): void {
    if (!query.trim() && this._query().trim()) this.snapshotRecents();
    this._query.set(query);
  }

  /** Refreshed only on load, chip change and a cleared search — never by the click that pushes a recent. */
  private snapshotRecents(): void {
    this._recentsSnapshot.set(this.recents());
  }

  requestPickerFocus(): void {
    this._pickerFocus.update((n) => n + 1);
  }

  /** A newly-found resource lands on top; one already present keeps its spot (no reshuffle). */
  pushRecent(item: ResourceItem): void {
    void this.lists.pushRecent(item);
  }

  clearRecents(): void {
    void this.lists.clearRecents();
  }

  isFavorite(id: string): boolean {
    return this.lists.isFavorite(id);
  }

  toggleFavorite(item: ResourceItem): void {
    void this.lists.toggleFavorite(item);
  }

  setActive(id: string | null): void {
    this._activeId.set(id);
  }
}
