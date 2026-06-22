import { Component, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

import { ViewCatalogService } from './view-catalog.service';
import { LastViewStore } from './last-view-store';
import { pickLandingView } from './view-landing';

/**
 * The landing route ({@code ''} and the {@code '**'} catch-all). The SPA is one
 * page — the generic {@link ViewHostComponent} renders every server-declared
 * view by name — so "landing" is just choosing WHICH view to open: the
 * last-opened one (persisted by {@link LastViewStore}) when it is still in the
 * catalog, else the first catalog view. View names are deployment-specific
 * (server-seeded), so nothing is hardcoded. Replaces the deleted REST
 * reservations table as the default landing (PRD 078).
 */
@Component({
  selector: 'app-default-view-redirect',
  imports: [],
  template: `
    @if (empty()) {
      <p class="meta">Keine Ansichten verfügbar.</p>
    } @else {
      <p class="meta">lädt…</p>
    }
  `,
  styles: [
    `
      .meta {
        color: rgba(0, 0, 0, 0.6);
        font-size: 0.85rem;
        margin: 1.25rem auto;
        max-width: 1100px;
        padding: 0 1rem;
      }
    `,
  ],
})
export class DefaultViewRedirectComponent implements OnInit {
  private readonly router = inject(Router);
  private readonly catalog = inject(ViewCatalogService);
  private readonly lastView = inject(LastViewStore);

  /** True once the catalog is known to be empty — render a friendly note. */
  readonly empty = signal(false);

  ngOnInit(): void {
    this.catalog.listViews().subscribe({
      next: (views) => {
        const target = pickLandingView(
          this.lastView.get(),
          views.map((v) => v.name),
        );
        if (target) {
          // replaceUrl so the catch-all landing does not pollute browser history.
          this.router.navigate(['/views', target], { replaceUrl: true });
        } else {
          this.empty.set(true);
        }
      },
      error: () => this.empty.set(true),
    });
  }
}
