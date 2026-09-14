import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { EntityIdChipComponent } from './entity-id-chip.component';

describe('EntityIdChipComponent', () => {
  const writeText = vi.fn();

  beforeEach(async () => {
    writeText.mockReset().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText },
      configurable: true,
    });
    await TestBed.configureTestingModule({ imports: [EntityIdChipComponent] }).compileComponents();
  });

  afterEach(() => vi.restoreAllMocks());

  function create(id: string) {
    const fixture = TestBed.createComponent(EntityIdChipComponent);
    fixture.componentRef.setInput('id', id);
    fixture.detectChanges();
    return fixture;
  }

  it('renders the shortened id, full id in the tooltip', () => {
    const fixture = create('e1d7469a-763e-433d-a7cd-f846da9c0ee1');
    const btn = (fixture.nativeElement as HTMLElement).querySelector('button')!;
    expect(btn.textContent).toContain('e1d7469a');
    expect(btn.textContent).toContain('0ee1');
    expect(btn.textContent).not.toContain('763e-433d'); // middle elided
    expect(btn.title).toContain('e1d7469a-763e-433d-a7cd-f846da9c0ee1');
  });

  it('click copies the FULL id and shows transient feedback', async () => {
    const fixture = create('e1d7469a-763e-433d-a7cd-f846da9c0ee1');
    const btn = (fixture.nativeElement as HTMLElement).querySelector('button')!;
    btn.click();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(writeText).toHaveBeenCalledWith('e1d7469a-763e-433d-a7cd-f846da9c0ee1');
    expect(btn.textContent).toContain('kopiert');
  });
});
