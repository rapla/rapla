import { Directive, HostListener, input, output } from '@angular/core';

/** PRD 127 — a split-pane grip: dragging reports the new width, a double click asks for the default. */
@Directive({ selector: '[appResizeDivider]' })
export class ResizeDividerDirective {
  /** The width when the drag starts. */
  readonly appResizeDivider = input.required<number>();
  readonly widthChange = output<number>();
  readonly widthReset = output<void>();

  private start: { x: number; width: number } | null = null;

  @HostListener('pointerdown', ['$event'])
  protected down(event: PointerEvent): void {
    event.preventDefault();
    (event.target as Element).setPointerCapture?.(event.pointerId);
    this.start = { x: event.clientX, width: this.appResizeDivider() };
  }

  @HostListener('pointermove', ['$event'])
  protected move(event: PointerEvent): void {
    if (this.start) this.widthChange.emit(this.start.width + event.clientX - this.start.x);
  }

  @HostListener('pointerup')
  @HostListener('pointercancel')
  protected up(): void {
    this.start = null;
  }

  @HostListener('dblclick')
  protected double(): void {
    this.widthReset.emit();
  }
}
