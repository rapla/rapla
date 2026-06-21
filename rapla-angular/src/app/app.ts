import { Component, inject, signal } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
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
    MatListModule,
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

  /** Server view catalog (PRD 074 listViews) → the sidenav entries. */
  readonly views = signal<ViewInfo[]>([]);

  constructor() {
    this.catalog.listViews().subscribe((vs) => this.views.set(vs));
  }
}
