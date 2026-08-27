import { Component, computed, inject, signal } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive, Router, NavigationEnd } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { filter, map, startWith } from 'rxjs/operators';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatMenuModule } from '@angular/material/menu';
import { MatIconModule } from '@angular/material/icon';

import { AppToolbarComponent } from './shell/app-toolbar.component';
import { ResourceSelectionComponent } from './shell/resource-selection.component';
import { ViewControlStripComponent } from './shell/view-control-strip.component';
import { ChipRailComponent } from './shell/chip-rail.component';
import { ViewCatalogService, type ViewInfo } from './views/view-catalog.service';

@Component({
  selector: 'app-root',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatSidenavModule,
    MatMenuModule,
    MatIconModule,
    AppToolbarComponent,
    ResourceSelectionComponent,
    ViewControlStripComponent,
    ChipRailComponent,
  ],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  private readonly catalog = inject(ViewCatalogService);
  private readonly router = inject(Router);

  /** Server view catalog (PRD 074 listViews) → the entries of the view picker. */
  readonly views = signal<ViewInfo[]>([]);

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
    if (!active) return 'Sicht';
    return this.views().find((v) => v.name === active)?.title ?? active;
  });

  private viewNameOfRoute(): string | null {
    let route = this.router.routerState.root;
    while (route.firstChild) route = route.firstChild;
    return route.snapshot.paramMap.get('viewName');
  }

  constructor() {
    this.catalog.listViews().subscribe((vs) => this.views.set(vs));
  }
}
