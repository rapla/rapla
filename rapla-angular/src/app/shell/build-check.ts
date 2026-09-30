import { ChangeDetectionStrategy, Component, DOCUMENT, Injectable, inject } from '@angular/core';
import { HttpErrorResponse, HttpInterceptorFn, HttpResponse } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { tap } from 'rxjs';

import { TPipe } from '../i18n/i18n.service';

export const BUILD_HEADER = 'X-Rapla-Build';
export const MISMATCH_HEADER = 'X-Rapla-Build-Mismatch';
const MAIN_BUNDLE = /main-[\w-]+\.js/;

/** PRD 125 — the build id is the hashed main bundle this page was loaded with; 'dev' under ng serve. */
export function clientBuild(doc: Document): string {
  for (const script of Array.from(doc.querySelectorAll('script[src]'))) {
    const m = MAIN_BUNDLE.exec(script.getAttribute('src') ?? '');
    if (m) return m[0];
  }
  return 'dev';
}

@Component({
  selector: 'app-reload-dialog',
  imports: [MatDialogModule, MatButtonModule, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>{{ 'new_version_available' | t }}</h2>
    <mat-dialog-content>
      <p>{{ 'new_version_reload_question' | t }}</p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button matButton (click)="ref.close(false)">{{ 'cancel' | t }}</button>
      <button matButton="filled" (click)="ref.close(true)">{{ 'reload' | t }}</button>
    </mat-dialog-actions>
  `,
})
export class ReloadDialogComponent {
  readonly ref = inject<MatDialogRef<ReloadDialogComponent, boolean>>(MatDialogRef);
}

/**
 * PRD 125 D1 — the server only signals a newer SPA; the user decides. One dialog per
 * server build: Abbrechen keeps working (unsaved changes stay savable) until a different
 * build shows up.
 */
@Injectable({ providedIn: 'root' })
export class BuildCheckService {
  private readonly dialog = inject(MatDialog);
  private readonly handled = new Set<string>();
  readonly build = clientBuild(inject(DOCUMENT));
  reload = (): void => location.reload();

  onServerBuild(serverBuild: string | null): void {
    if (!serverBuild || this.build === 'dev' || serverBuild === this.build) return;
    if (this.handled.has(serverBuild)) return;
    this.handled.add(serverBuild);
    this.dialog
      .open<ReloadDialogComponent, void, boolean>(ReloadDialogComponent, { disableClose: true })
      .afterClosed()
      .subscribe((ok) => {
        if (ok) this.reload();
      });
  }
}

export const buildCheckInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.includes('/api/')) return next(req);
  const check = inject(BuildCheckService);
  return next(req.clone({ setHeaders: { [BUILD_HEADER]: check.build } })).pipe(
    tap({
      next: (event) => {
        if (event instanceof HttpResponse) check.onServerBuild(event.headers.get(MISMATCH_HEADER));
      },
      error: (err: unknown) => {
        if (err instanceof HttpErrorResponse)
          check.onServerBuild(err.headers?.get(MISMATCH_HEADER) ?? null);
      },
    }),
  );
};
