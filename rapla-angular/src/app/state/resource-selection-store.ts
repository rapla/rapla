import { Injectable, computed, inject, signal } from '@angular/core';

import { RecentsFavoritesService } from './recents-favorites.service';

export type ResourceSelectionTab = 'recents' | 'favorites' | 'group';

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
}

/**
 * The persistent left ResourceSelection — the pool you pick/step resources from.
 * Three sources feed the same click-to-step rhythm: {@code recents} (resources
 * you found), {@code favorites} (pinned ★), {@code group} (a loaded group).
 * {@link activeId} marks the "▶ gezeigt" item. Distinct from the FilterStore:
 * this is the candidate pool; a click here {@code replace}s the filter.
 *
 * PRD 089: recents + favorites are now PER-USER SERVER state, owned by
 * {@link RecentsFavoritesService} (rapla Preferences, follows the user across
 * devices, isolated per account). The store re-exposes the service signals so
 * its consumers (the ResourceSelection component, omnibox) are unchanged.
 * {@code group} is still a transient in-memory list.
 */
@Injectable({ providedIn: 'root' })
export class ResourceSelectionStore {
  private readonly lists = inject(RecentsFavoritesService);

  private readonly _group = signal<ResourceItem[]>([]);
  private readonly _groupLabel = signal<string | null>(null);
  private readonly _activeTab = signal<ResourceSelectionTab>('recents');
  private readonly _activeId = signal<string | null>(null);

  readonly recents = this.lists.recents;
  readonly favorites = this.lists.favorites;
  readonly group = this._group.asReadonly();
  readonly groupLabel = this._groupLabel.asReadonly();
  readonly activeTab = this._activeTab.asReadonly();
  readonly activeId = this._activeId.asReadonly();

  readonly activeList = computed<ResourceItem[]>(() => {
    switch (this._activeTab()) {
      case 'favorites':
        return this.favorites();
      case 'group':
        return this._group();
      default:
        return this.recents();
    }
  });

  setActiveTab(tab: ResourceSelectionTab): void {
    this._activeTab.set(tab);
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
