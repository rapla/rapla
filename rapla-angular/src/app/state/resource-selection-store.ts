import { Injectable, computed, signal } from '@angular/core';

export type ResourceSelectionTab = 'recents' | 'favorites' | 'group';

/** A steppable resource in the selection list. */
export interface ResourceItem {
  id: string;
  label: string;
  color?: string;
}

const RECENTS_CAP = 20;
const RECENTS_KEY = 'rapla.resourceSelection.recents';
const FAVORITES_KEY = 'rapla.resourceSelection.favorites';

function load(key: string): ResourceItem[] {
  try {
    const raw = globalThis.localStorage?.getItem(key);
    return raw ? (JSON.parse(raw) as ResourceItem[]) : [];
  } catch {
    return [];
  }
}

function save(key: string, items: ResourceItem[]): void {
  try {
    globalThis.localStorage?.setItem(key, JSON.stringify(items));
  } catch {
    /* storage unavailable / quota — non-fatal */
  }
}

/**
 * The persistent left ResourceSelection — the pool you pick/step resources from.
 * Three sources feed the same click-to-step rhythm: {@code recents} (resources
 * you found), {@code favorites} (pinned ★), {@code group} (a loaded group).
 * {@link activeId} marks the "▶ gezeigt" item. Distinct from the FilterStore:
 * this is the candidate pool; a click here {@code replace}s the filter.
 *
 * Recents + favorites survive reloads via {@code localStorage}. Recents keep a
 * STABLE order: a newly-found resource lands on top, but re-acting on one that's
 * already there does NOT reshuffle it (so stepping between two recents doesn't
 * reorder the list).
 */
@Injectable({ providedIn: 'root' })
export class ResourceSelectionStore {
  private readonly _recents = signal<ResourceItem[]>(load(RECENTS_KEY));
  private readonly _favorites = signal<ResourceItem[]>(load(FAVORITES_KEY));
  private readonly _group = signal<ResourceItem[]>([]);
  private readonly _groupLabel = signal<string | null>(null);
  private readonly _activeTab = signal<ResourceSelectionTab>('recents');
  private readonly _activeId = signal<string | null>(null);

  readonly recents = this._recents.asReadonly();
  readonly favorites = this._favorites.asReadonly();
  readonly group = this._group.asReadonly();
  readonly groupLabel = this._groupLabel.asReadonly();
  readonly activeTab = this._activeTab.asReadonly();
  readonly activeId = this._activeId.asReadonly();

  readonly activeList = computed<ResourceItem[]>(() => {
    switch (this._activeTab()) {
      case 'favorites':
        return this._favorites();
      case 'group':
        return this._group();
      default:
        return this._recents();
    }
  });

  setActiveTab(tab: ResourceSelectionTab): void {
    this._activeTab.set(tab);
  }

  /** A newly-found resource lands on top; one already present keeps its spot (no reshuffle). */
  pushRecent(item: ResourceItem): void {
    this._recents.update((xs) =>
      xs.some((x) => x.id === item.id) ? xs : [item, ...xs].slice(0, RECENTS_CAP),
    );
    save(RECENTS_KEY, this._recents());
  }

  isFavorite(id: string): boolean {
    return this._favorites().some((x) => x.id === id);
  }

  toggleFavorite(item: ResourceItem): void {
    this._favorites.update((xs) =>
      xs.some((x) => x.id === item.id) ? xs.filter((x) => x.id !== item.id) : [...xs, item],
    );
    save(FAVORITES_KEY, this._favorites());
  }

  loadGroup(label: string, items: ResourceItem[]): void {
    this._group.set(items);
    this._groupLabel.set(label);
    this._activeTab.set('group');
  }

  clearGroup(): void {
    this._group.set([]);
    this._groupLabel.set(null);
  }

  setActive(id: string | null): void {
    this._activeId.set(id);
  }
}
