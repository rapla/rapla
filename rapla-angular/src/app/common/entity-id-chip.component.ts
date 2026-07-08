import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';

/**
 * Small copyable rapla-id chip for edit surfaces (event sheet, allocatable
 * dialog) — lets users refer to an entity unambiguously ("die Veranstaltung
 * e1d7469a…"). Shows a shortened id, click copies the FULL id to the
 * clipboard with transient feedback.
 */
@Component({
  selector: 'app-entity-id-chip',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: `
    button {
      font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
      font-size: 11px;
      color: #5c6774;
      background: #eef1f5;
      border: 1px solid #d9dee6;
      border-radius: 99px;
      padding: 1px 8px;
      cursor: copy;
      white-space: nowrap;
    }
    button:hover {
      background: #e3edfc;
      color: #1a73e8;
    }
    button.copied {
      background: #e6f4ea;
      color: #1e8e3e;
    }
  `,
  template: `
    <button
      type="button"
      [class.copied]="copied()"
      [title]="'ID kopieren: ' + id()"
      (click)="copy($event)"
    >
      {{ copied() ? '✓ kopiert' : shortId() }}
    </button>
  `,
})
export class EntityIdChipComponent {
  readonly id = input.required<string>();
  readonly copied = signal(false);
  readonly shortId = computed(() => {
    const v = this.id();
    return v.length > 14 ? `${v.slice(0, 8)}…${v.slice(-4)}` : v;
  });

  copy(event: Event): void {
    event.stopPropagation();
    void navigator.clipboard?.writeText(this.id()).then(() => {
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 1500);
    });
  }
}
