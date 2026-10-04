import { describe, it, expect } from 'vitest';
import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { ResizeDividerDirective } from './resize-divider.directive';

@Component({
  imports: [ResizeDividerDirective],
  template: `<div
    class="grip"
    [appResizeDivider]="width()"
    (widthChange)="changes.push($event)"
    (widthReset)="resets = resets + 1"
  ></div>`,
})
class Host {
  readonly width = signal(290);
  readonly changes: number[] = [];
  resets = 0;
}

const fire = (el: Element, type: string, clientX: number) =>
  el.dispatchEvent(new MouseEvent(type, { clientX, bubbles: true }));

/** PRD 127 — dragging the grip reports the new width; a double click resets. */
describe('ResizeDividerDirective', () => {
  it('a drag reports the start width plus the pointer movement, and stops on release', () => {
    const f = TestBed.createComponent(Host);
    f.detectChanges();
    const grip = (f.nativeElement as HTMLElement).querySelector('.grip')!;
    fire(grip, 'pointerdown', 100);
    fire(grip, 'pointermove', 180);
    fire(grip, 'pointermove', 150);
    fire(grip, 'pointerup', 150);
    fire(grip, 'pointermove', 400);
    expect(f.componentInstance.changes).toEqual([370, 340]);
  });

  it('a move without a press reports nothing', () => {
    const f = TestBed.createComponent(Host);
    f.detectChanges();
    fire((f.nativeElement as HTMLElement).querySelector('.grip')!, 'pointermove', 400);
    expect(f.componentInstance.changes).toEqual([]);
  });

  it('a double click resets', () => {
    const f = TestBed.createComponent(Host);
    f.detectChanges();
    fire((f.nativeElement as HTMLElement).querySelector('.grip')!, 'dblclick', 0);
    expect(f.componentInstance.resets).toBe(1);
  });
});
