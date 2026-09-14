import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

import { AuthService } from '../auth/auth.service';
import { type ResourceItem } from './resource-selection-store';

/** Wire shape of {@code /api/recents} + {@code /api/favorites} (PRD 089). The
 *  server resolves presentation live (D3); {@code color}/{@code typeKey} may be
 *  null for a user entry. */
interface UserListItem {
  id: string;
  kind: 'resource' | 'user';
  label: string;
  color: string | null;
  typeKey: string | null;
}

function toResourceItem(dto: UserListItem): ResourceItem {
  return {
    id: dto.id,
    label: dto.label,
    color: dto.color ?? undefined,
    kind: dto.kind,
    typeKey: dto.typeKey ?? undefined,
  };
}

/** The id+kind a write needs; presentation is resolved server-side on read. */
function toRequest(item: ResourceItem): { id: string; kind: 'resource' | 'user' } {
  return { id: item.id, kind: item.kind ?? 'resource' };
}

/**
 * PRD 089 Phase 4 — server-backed recents + favorites. Replaces the SPA's old
 * per-browser {@code localStorage} lists with per-user server storage (rapla
 * Preferences). Loads both lists when an identity appears (login / impersonation
 * switch) and clears them when it disappears, so two accounts on one browser
 * never bleed. Writes are optimistic against in-memory signals and then
 * reconciled with the authoritative list the server returns.
 *
 * Cookie auth is automatic (relative URLs); the SPA holds no token.
 */
@Injectable({ providedIn: 'root' })
export class RecentsFavoritesService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  private readonly _recents = signal<ResourceItem[]>([]);
  private readonly _favorites = signal<ResourceItem[]>([]);

  readonly recents = this._recents.asReadonly();
  readonly favorites = this._favorites.asReadonly();

  private lastUserId: string | null = null;

  constructor() {
    // Reload (or clear) the lists whenever the identity changes — login,
    // logout, impersonation switch. Reading the signal registers the dependency.
    effect(() => {
      const userId = this.auth.identity()?.userId ?? null;
      if (userId === this.lastUserId) return;
      this.lastUserId = userId;
      if (userId) {
        void this.reload();
      } else {
        this._recents.set([]);
        this._favorites.set([]);
      }
    });
  }

  async reload(): Promise<void> {
    await Promise.all([this.loadRecents(), this.loadFavorites()]);
  }

  private async loadRecents(): Promise<void> {
    try {
      const items = await firstValueFrom(this.http.get<UserListItem[]>('/api/recents'));
      this._recents.set(items.map(toResourceItem));
    } catch {
      this._recents.set([]);
    }
  }

  private async loadFavorites(): Promise<void> {
    try {
      const items = await firstValueFrom(this.http.get<UserListItem[]>('/api/favorites'));
      this._favorites.set(items.map(toResourceItem));
    } catch {
      this._favorites.set([]);
    }
  }

  isFavorite(id: string): boolean {
    return this._favorites().some((x) => x.id === id);
  }

  /** Add/promote a recent (newest first, no duplicate); POST /api/recents. */
  async pushRecent(item: ResourceItem): Promise<void> {
    this._recents.update((xs) => (xs.some((x) => x.id === item.id) ? xs : [item, ...xs]));
    try {
      const items = await firstValueFrom(
        this.http.post<UserListItem[]>('/api/recents', toRequest(item)),
      );
      this._recents.set(items.map(toResourceItem));
    } catch {
      // optimistic value stands; a later reload reconciles.
    }
  }

  /** Clear all recents; DELETE /api/recents. */
  async clearRecents(): Promise<void> {
    this._recents.set([]);
    try {
      await firstValueFrom(this.http.delete<UserListItem[]>('/api/recents'));
    } catch {
      // optimistic clear stands.
    }
  }

  /** Pin (POST /api/favorites) or unpin (DELETE /api/favorites/{id}). */
  async toggleFavorite(item: ResourceItem): Promise<void> {
    const wasFavorite = this.isFavorite(item.id);
    if (wasFavorite) {
      this._favorites.update((xs) => xs.filter((x) => x.id !== item.id));
      try {
        const items = await firstValueFrom(
          this.http.delete<UserListItem[]>(`/api/favorites/${encodeURIComponent(item.id)}`),
        );
        this._favorites.set(items.map(toResourceItem));
      } catch {
        // optimistic value stands.
      }
    } else {
      this._favorites.update((xs) => [...xs, item]);
      try {
        const items = await firstValueFrom(
          this.http.post<UserListItem[]>('/api/favorites', toRequest(item)),
        );
        this._favorites.set(items.map(toResourceItem));
      } catch {
        // optimistic value stands.
      }
    }
  }

  readonly favoriteIds = computed(() => new Set(this._favorites().map((x) => x.id)));
}
