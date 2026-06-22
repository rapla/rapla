import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import {
  ResourceSelectionStore,
  type ResourceSelectionTab,
  type ResourceItem,
} from '../state/resource-selection-store';
import { FilterStore } from '../state/filter-store';
import { AuthService, type Identity } from '../auth/auth.service';

/**
 * The persistent left ResourceSelection. Tabs pick the source (Zuletzt/
 * Favoriten/Gruppe); a local query narrows long lists (e.g. hundreds of
 * lecturers). A plain click {@link FilterStore.replace}s the filter with that
 * resource — the click-to-step rhythm — and marks it active ("▶ gezeigt").
 */
@Component({
  selector: 'app-resource-selection',
  imports: [FormsModule],
  template: `
    <div class="stepper">
      @if (me(); as user) {
        <div
          class="pinned"
          [class.active]="meActive()"
          (click)="scopeToMe(user)"
          title="Auf meine eigenen Veranstaltungen filtern"
        >
          <span class="dot person">👤</span>
          <span class="lbl">{{ user.name || user.username }}</span>
          <span class="meta">meine</span>
        </div>
      }
      <div class="sthead">DURCHSTEPPEN</div>
      <div class="tabs">
        @for (t of tabs; track t.key) {
          <button [class.on]="store.activeTab() === t.key" (click)="store.setActiveTab(t.key)">
            {{ t.label }}
          </button>
        }
      </div>
      @if (store.activeTab() === 'group' && store.groupLabel()) {
        <div class="grouphdr">
          <span>{{ store.groupLabel() }}</span>
          <span class="clr" (click)="store.clearGroup()">× leeren</span>
        </div>
      }
      <input
        class="stsearch"
        placeholder="in der Liste filtern…"
        [ngModel]="query()"
        (ngModelChange)="query.set($event)"
      />
      @for (it of visible(); track it.id; let i = $index) {
        <div class="item" [class.active]="store.activeId() === it.id" (click)="step($event, it, i)">
          <span class="dot" [style.background]="it.color ?? 'transparent'"></span>
          <span class="lbl">{{ it.label }}</span>
          @if (store.activeId() === it.id) {
            <span class="now">▶ gezeigt</span>
          }
          <button
            class="fav"
            [class.on]="store.isFavorite(it.id)"
            [attr.aria-label]="store.isFavorite(it.id) ? 'Aus Favoriten entfernen' : 'Zu Favoriten'"
            [title]="store.isFavorite(it.id) ? 'Aus Favoriten entfernen' : 'Zu Favoriten'"
            (click)="toggleFav($event, it)"
          >
            {{ store.isFavorite(it.id) ? '★' : '☆' }}
          </button>
        </div>
      } @empty {
        <div class="more">— leer</div>
      }
    </div>
  `,
  styles: [
    `
      .stepper {
        width: 250px;
        display: flex;
        flex-direction: column;
        overflow: auto;
        height: 100%;
      }
      .pinned {
        display: flex;
        align-items: center;
        gap: 0.55rem;
        margin: 0.6rem 0.6rem 0.2rem;
        padding: 0.5rem 0.6rem;
        border: 1px solid rgba(63, 81, 181, 0.25);
        border-radius: 6px;
        background: rgba(63, 81, 181, 0.06);
        cursor: pointer;
        font-size: 0.84rem;
      }
      .pinned:hover {
        background: rgba(63, 81, 181, 0.12);
      }
      .pinned.active {
        background: rgba(46, 125, 50, 0.12);
        border-color: #2e7d32;
        font-weight: 600;
      }
      .pinned .dot.person {
        font-size: 0.9rem;
        line-height: 1;
      }
      .pinned .lbl {
        flex: 1;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      .pinned .meta {
        font-size: 0.62rem;
        font-weight: 700;
        color: var(--mat-sys-primary, #3f51b5);
        text-transform: uppercase;
      }
      .sthead {
        padding: 0.75rem 0.9rem 0.5rem;
        font-size: 0.7rem;
        font-weight: 700;
        letter-spacing: 0.04em;
        color: rgba(0, 0, 0, 0.55);
      }
      .tabs {
        display: flex;
        gap: 0.35rem;
        padding: 0 0.75rem 0.5rem;
      }
      .tabs button {
        flex: 1;
        border: 1px solid rgba(0, 0, 0, 0.15);
        background: #fff;
        border-radius: 6px;
        padding: 0.35rem 0.25rem;
        font-size: 0.72rem;
        cursor: pointer;
        color: rgba(0, 0, 0, 0.55);
      }
      .tabs button.on {
        background: var(--mat-sys-primary, #3f51b5);
        color: #fff;
        border-color: transparent;
        font-weight: 600;
      }
      .grouphdr {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 0.35rem 0.9rem;
        background: rgba(106, 27, 154, 0.08);
        font-size: 0.68rem;
        font-weight: 700;
        color: #6a1b9a;
      }
      .grouphdr .clr {
        cursor: pointer;
      }
      .stsearch {
        margin: 0 0.75rem 0.5rem;
        height: 1.9rem;
        border: 1px solid rgba(0, 0, 0, 0.15);
        border-radius: 6px;
        padding: 0 0.6rem;
        font-size: 0.75rem;
      }
      .item {
        display: flex;
        align-items: center;
        gap: 0.55rem;
        padding: 0.5rem 0.9rem;
        font-size: 0.84rem;
        cursor: pointer;
        border-left: 3px solid transparent;
      }
      .item:hover {
        background: rgba(63, 81, 181, 0.06);
      }
      .item.active {
        background: rgba(46, 125, 50, 0.1);
        border-left-color: #2e7d32;
        font-weight: 600;
      }
      .item .dot {
        width: 0.55rem;
        height: 0.55rem;
        border-radius: 50%;
        flex: none;
      }
      .item .lbl {
        flex: 1;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      .item .now {
        font-size: 0.62rem;
        font-weight: 700;
        color: #2e7d32;
      }
      .item .fav {
        border: none;
        background: transparent;
        cursor: pointer;
        font-size: 0.95rem;
        line-height: 1;
        padding: 0 0.1rem;
        color: rgba(0, 0, 0, 0.3);
      }
      .item .fav.on {
        color: #f5a623;
      }
      .item:hover .fav {
        color: rgba(0, 0, 0, 0.45);
      }
      .item:hover .fav.on {
        color: #f5a623;
      }
      .more {
        padding: 0.5rem 0.9rem;
        font-size: 0.75rem;
        color: rgba(0, 0, 0, 0.45);
      }
    `,
  ],
})
export class ResourceSelectionComponent {
  protected readonly store = inject(ResourceSelectionStore);
  private readonly filter = inject(FilterStore);
  private readonly auth = inject(AuthService);

  /** The logged-in user, pinned at the top for a one-click "my events" scope. */
  protected readonly me = this.auth.identity;
  /** True when a `user` scope chip for the logged-in user is active. */
  protected readonly meActive = computed(() => {
    const id = this.auth.identity()?.userId;
    return !!id && this.filter.entries().some((e) => e.kind === 'user' && e.id === id);
  });

  protected readonly query = signal('');

  protected readonly tabs: { key: ResourceSelectionTab; label: string }[] = [
    { key: 'recents', label: 'Zuletzt' },
    { key: 'favorites', label: 'Favoriten' },
    { key: 'group', label: 'Gruppe' },
  ];

  protected readonly visible = computed<ResourceItem[]>(() => {
    const q = this.query().trim().toLowerCase();
    const list = this.store.activeList();
    return q ? list.filter((x) => x.label.toLowerCase().includes(q)) : list;
  });

  /** Index of the last plain/ctrl click — the anchor for Shift-range selection. */
  private anchorIndex = -1;

  /**
   * Click semantics (Swing-like multi-select):
   * - plain click → REPLACE (step to this one resource)
   * - Strg/⌘ click → ADD (accumulate this resource into the filter)
   * - Shift click → RANGE (add every resource between the anchor and this one)
   *
   * Stepping only views — it never reorders the list (Recents stay stable).
   */
  step(event: MouseEvent, it: ResourceItem, index: number): void {
    const entry = (r: ResourceItem) =>
      ({ id: r.id, kind: 'resource', label: r.label, color: r.color }) as const;
    const list = this.visible();

    if (event.shiftKey && this.anchorIndex >= 0) {
      const [a, b] = [this.anchorIndex, index].sort((x, y) => x - y);
      for (let i = a; i <= b; i++) this.filter.add(entry(list[i]));
    } else if (event.ctrlKey || event.metaKey) {
      this.filter.add(entry(it));
      this.anchorIndex = index;
    } else {
      this.filter.replace(entry(it));
      this.anchorIndex = index;
    }
    this.store.setActive(it.id);
  }

  /** ★ pins/unpins to Favoriten without stepping the row. */
  toggleFav(event: Event, it: ResourceItem): void {
    event.stopPropagation();
    this.store.toggleFavorite(it);
  }

  /** Step to the logged-in user's own events — a `user` scope chip (ownerEq:<me>). */
  scopeToMe(user: Identity): void {
    this.filter.replace({ id: user.userId, kind: 'user', label: user.name || user.username });
  }
}
