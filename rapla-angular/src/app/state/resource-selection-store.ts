import { Injectable, computed, effect, inject, signal, untracked } from '@angular/core';

import { GraphqlService } from '../graphql/graphql.service';
import { RecentsFavoritesService } from './recents-favorites.service';
import { AuthService } from '../auth/auth.service';
import { ScopedStorage, bindPerUser } from './persist';
import {
  byLabel,
  filterRows,
  rankAll,
  typeChips,
  usersMatching,
  type PickerChip,
} from './resource-picker';
import { t as tr } from '../i18n/i18n.service';

/** A steppable item in the selection list. Usually a resource; a `user` item
 *  steps as an ownerIn scope chip instead of a resource filter. Defaults to
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
  /** RESOURCE | PERSON — groups the type select like the create dialog (PRD 122 D9). */
  classificationType?: string;
  /** Login name of a user row — the search matches it too (PRD 119). */
  username?: string;
  /** Picker group paths, one per categorization value (PRD 119 D11). */
  groupPaths?: string[][];
}

interface PickerState {
  chip: string;
  query: string;
  activeId: string | null;
}

const EMPTY_STATE: PickerState = { chip: 'all', query: '', activeId: null };

interface PickerWire {
  resources?: {
    id: string;
    kind?: string;
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
  private readonly auth = inject(AuthService);

  /** PRD 123 D5 — chip, query and active row survive a reload, per user like the FilterStore. */
  private readonly storage = new ScopedStorage(inject(AuthService), 'rapla.picker');
  private readonly _resources = signal<ResourceItem[]>([]);
  private readonly _users = signal<ResourceItem[]>([]);
  private readonly _activeChip = signal<string>(this.saved().chip);
  private readonly _activeId = signal<string | null>(this.saved().activeId);
  private readonly _query = signal(this.saved().query);
  private readonly _pickerFocus = signal(0);
  private readonly _activateFirst = signal<{ n: number; ctrl: boolean }>({ n: 0, ctrl: false });
  /** PRD 119 P3b (user ruling A) — Alle ranks by this copy of the recents, so a click never moves its row. */
  private readonly _recentsSnapshot = signal<ResourceItem[]>([]);
  private loaded = false;
  /** The lean list has answered — the Benutzer chip waits for it (no "meine" flash). */
  private readonly listLoaded = signal(false);
  /** The identity the lean list was fetched for (R-22). */
  private loadedFor: string | null = null;

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
  /** PRD 123 D4 — Enter in the search field: the rail steps its first shown row (Ctrl = add). */
  readonly activateFirst = this._activateFirst.asReadonly();

  constructor() {
    // Recents answered after the lean list: refresh the snapshot on every server (re)load, never on a push.
    effect(() => {
      this.lists.reloaded();
      untracked(() => this.snapshotRecents());
    });
    bindPerUser(this.auth, () => {
      const saved = this.saved();
      this._activeChip.set(saved.chip);
      this._activeId.set(saved.activeId);
      this._query.set(saved.query);
      // R-22 — the lean list is the caller's read scope: a new identity must not see the old one.
      if (!this.loaded || (this.auth.identity()?.userId ?? null) === this.loadedFor) return;
      this._resources.set([]);
      this._users.set([]);
      this.listLoaded.set(false);
      if (this.auth.identity()) untracked(() => this.fetch());
      else this.loaded = false;
    });
  }

  private saved(): PickerState {
    return { ...EMPTY_STATE, ...this.storage.load<Partial<PickerState>>({}) };
  }

  private persist(): void {
    this.storage.save({
      chip: this._activeChip(),
      query: this._query(),
      activeId: this._activeId(),
    } satisfies PickerState);
  }

  readonly chips = computed<PickerChip[]>(() => [
    { key: 'all', label: tr('state_chip_all') },
    { key: 'favorites', label: tr('state_chip_favorites') },
    { key: 'recents', label: tr('state_chip_recents') },
    ...typeChips(this._resources()),
  ]);

  readonly activeList = computed<ResourceItem[]>(() => this.listFor(this._activeChip()));

  /** PRD 123 D9 — every readable account, the own one first, the rest A–Z. */
  private readonly accounts = computed(() => {
    const me = this.auth.identity()?.userId;
    return [...this._users()].sort(
      (a, b) => Number(b.id === me) - Number(a.id === me) || byLabel(a, b),
    );
  });

  /** PRD 123 D9 — the Benutzer chip; `mine` when the own account is the only readable one. */
  readonly usersChip = computed<{ mine: boolean } | null>(() => {
    const me = this.auth.identity()?.userId;
    if (!this.listLoaded() || (!me && !this._users().length)) return null;
    return { mine: this._users().every((u) => u.id === me) };
  });

  /** PRD 123 D1 — the rows a chip shows; a second picker (the event sheet) asks for its own chip. */
  listFor(chip: string): ResourceItem[] {
    switch (chip) {
      case 'all':
        return rankAll(this._resources(), this.favorites(), this._recentsSnapshot());
      case 'favorites':
        return this.favorites();
      case 'recents':
        return this.recents();
      case 'users':
        return this.accounts();
      default: {
        const typeKey = chip.slice('type:'.length);
        return this._resources()
          .filter((it) => it.typeKey === typeKey)
          .sort(byLabel);
      }
    }
  }

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
    this.loadedFor = this.auth.identity()?.userId ?? null;
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
              classificationType: r.kind,
              groupPaths: r.groupPaths ?? [],
            })),
        );
        this.snapshotRecents();
        this.listLoaded.set(true);
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
    this.persist();
  }

  setQuery(query: string): void {
    if (!query.trim() && this._query().trim()) this.snapshotRecents();
    this._query.set(query);
    this.persist();
  }

  /** Refreshed only on load, chip change and a cleared search — never by the click that pushes a recent. */
  private snapshotRecents(): void {
    this._recentsSnapshot.set(this.recents());
  }

  requestPickerFocus(): void {
    this._pickerFocus.update((n) => n + 1);
  }

  requestActivateFirst(ctrl: boolean): void {
    this._activateFirst.update(({ n }) => ({ n: n + 1, ctrl }));
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
    this.persist();
  }
}
