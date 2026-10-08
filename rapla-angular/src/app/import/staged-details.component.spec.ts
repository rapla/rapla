import { TestBed } from '@angular/core/testing';
import { MatBottomSheet } from '@angular/material/bottom-sheet';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { ImportWorklistService } from './import-worklist.service';
import { StagedDetailsButtonComponent } from './staged-details.component';

/** WP23x C4: ⓘ = a popover on desktop, a bottom sheet below 720 px; both show the source
 *  record loaded from stagedEventDetails. */
describe('StagedDetailsButtonComponent', () => {
  const open = vi.fn();

  function render() {
    TestBed.resetTestingModule();
    open.mockClear();
    TestBed.configureTestingModule({
      imports: [StagedDetailsButtonComponent],
      providers: [
        provideAnimationsAsync(),
        { provide: MatBottomSheet, useValue: { open } },
        {
          provide: ImportWorklistService,
          useValue: {
            stagedEventDetails: () =>
              of({ sourceItemId: 'v:1', changedSince: null, fields: [{ key: 'NAME', value: 'Mathe' }] }),
          },
        },
      ],
    });
    const f = TestBed.createComponent(StagedDetailsButtonComponent);
    f.componentRef.setInput('label', 'T4INF4211.1');
    f.componentRef.setInput('sourceItemId', 'v:1');
    f.componentRef.setInput('changedSince', null);
    f.detectChanges();
    return f.nativeElement as HTMLElement;
  }

  it('offers a popover trigger for desktop', () => {
    expect(render().querySelector('button.info-desktop')?.hasAttribute('aria-haspopup')).toBe(true);
  });

  it('opens a bottom sheet with the item from the mobile button', () => {
    (render().querySelector('button.info-mobile') as HTMLButtonElement).click();
    expect(open).toHaveBeenCalledTimes(1);
    expect((open.mock.calls[0] as unknown[])[1]).toEqual({
      data: { sourceItemId: 'v:1', changedSince: null },
    });
  });
});
