import {
  Component,
  afterRenderEffect,
  computed,
  effect,
  inject,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive, Router, NavigationEnd } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { filter, map, startWith } from 'rxjs/operators';
import { MatSidenavContainer, MatSidenavModule } from '@angular/material/sidenav';
import { MatDividerModule } from '@angular/material/divider';
import { MatMenuModule } from '@angular/material/menu';
import { MatIconModule } from '@angular/material/icon';

import { AppToolbarComponent } from './shell/app-toolbar.component';
import { ResourceSelectionComponent } from './shell/resource-selection.component';
import { ViewControlStripComponent } from './shell/view-control-strip.component';
import { ChipRailComponent } from './shell/chip-rail.component';
import { ResizeDividerDirective } from './shell/resize-divider.directive';
import { NavWidthStore } from './state/nav-width-store';
import {
  ViewCatalogService,
  selectionOf,
  type SelectionSource,
  type ViewInfo,
} from './views/view-catalog.service';
import { FilterStore } from './state/filter-store';
import { ReviewStore } from './state/review-store';
import { ViewStateStore } from './state/view-state-store';
import { AuthService } from './auth/auth.service';
import { TPipe, t } from './i18n/i18n.service';

@Component({
  selector: 'app-root',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatSidenavModule,
    MatMenuModule,
    MatDividerModule,
    MatIconModule,
    AppToolbarComponent,
    ResourceSelectionComponent,
    ViewControlStripComponent,
    ChipRailComponent,
    ResizeDividerDirective,
    TPipe,
  ],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  private readonly catalog = inject(ViewCatalogService);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  protected readonly navWidth = inject(NavWidthStore);
  private readonly container = viewChild(MatSidenavContainer);

  /** PRD 118 D8-8 — the demo instance names itself; text from GET /api/auth/me, none elsewhere. */
  readonly demoBanner = computed(() => this.auth.identity()?.demoBanner ?? null);

  /** Server view catalog (PRD 074 listViews) → the entries of the view picker. */
  readonly views = signal<ViewInfo[]>([]);
  /** The menu's three blocks: planning views, stored views, Prüfen views (conflicts / requests). */
  protected readonly planViews = computed(() =>
    this.views().filter((v) => !v.selection && v.source === 'BUILTIN'),
  );
  protected readonly customViews = computed(() =>
    this.views().filter((v) => !v.selection && v.source !== 'BUILTIN'),
  );
  protected readonly reviewViews = computed(() => this.views().filter((v) => !!v.selection));

  /**
   * The open view is the one in the URL ({@code views/:viewName}, PRD 078) — the router is the
   * only thing that actually knows it.
   */
  private readonly routedView = toSignal(
    this.router.events.pipe(
      filter((e) => e instanceof NavigationEnd),
      startWith(null),
      map(() => this.viewNameOfRoute()),
    ),
    { initialValue: null as string | null },
  );

  /**
   * Label of the picker. The catalog arrives asynchronously, so fall back to the raw view name
   * until its title is known, and to a neutral word off a view route — never an empty button.
   */
  readonly activeTitle = computed(() => {
    const active = this.routedView();
    if (!active) return t('app_view');
    return this.views().find((v) => v.name === active)?.title ?? active;
  });

  private readonly review = inject(ReviewStore);
  private readonly filter = inject(FilterStore);
  private readonly viewState = inject(ViewStateStore);

  /** PRD 128 D7 — the open view decides the left pane's source and with it the chip context (D3). */
  readonly selection = computed(() =>
    selectionOf(this.views().find((v) => v.name === this.routedView())),
  );

  /** PRD 128 D7 — open items: ACTIVE conflicts (not the disabled ones) and open requests. */
  private readonly openCounts = computed<Record<SelectionSource, number>>(() => ({
    resources: 0,
    conflicts: this.review.counts().reduce((n, c) => n + (c.disabled ? 0 : c.count), 0),
    requests: this.review.requests().length,
  }));

  /** The red badges next to the selector: one per Prüfen view with open items. */
  readonly badges = computed(() =>
    this.views()
      .map((v) => ({ view: v, badge: this.badgeOf(v) }))
      .filter((b) => b.badge !== null),
  );

  protected badgeOf(view: ViewInfo): { icon: string; count: number; title: string } | null {
    const source = selectionOf(view);
    const count = this.openCounts()[source];
    if (source === 'resources' || !count) return null;
    return source === 'conflicts'
      ? { icon: 'warning', count, title: t('shell_open_conflicts') }
      : { icon: 'pending_actions', count, title: t('shell_open_requests') };
  }

  /** PRD 127 — the rail's split-pane grip; null = back to the default. */
  protected resizeNav(width: number | null): void {
    if (width === null) this.navWidth.reset();
    else this.navWidth.set(width);
  }

  private viewNameOfRoute(): string | null {
    let route = this.router.routerState.root;
    while (route.firstChild) route = route.firstChild;
    return route.snapshot.paramMap.get('viewName');
  }

  constructor() {
    // Side mode measures the drawer for the content margin only on open/close — re-measure once the new width is rendered.
    afterRenderEffect(() => {
      this.navWidth.width();
      this.container()?.updateContentMargins();
    });
    this.catalog.listViews().subscribe((vs) => this.views.set(vs));
    this.review.ensureLoaded();
    effect(() => {
      const name = this.routedView();
      const view = this.views().find((v) => v.name === name);
      if (view) untracked(() => this.viewState.enterView(view.name, view.defaultRenderMode));
    });
    effect(() => {
      const source = this.selection();
      untracked(() => {
        this.filter.setContext(source === 'resources' ? 'plan' : source);
        if (source !== 'resources') this.review.refresh(source);
      });
    });
  }
}
