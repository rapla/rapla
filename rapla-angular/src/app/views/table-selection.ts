import { computed, signal } from '@angular/core';

export interface SelectionMods {
  shift?: boolean;
  ctrl?: boolean;
}

/**
 * PRD 099 — headless table-selection model (Swing/Excel semantics).
 *
 * Pure TS state machine over a caller-supplied RENDERED row order (sorted /
 * grouped, data rows only — group headers never enter the key list). Pointer:
 * plain = replace, Ctrl/⌘ = toggle, Shift = range from the anchor (Ctrl+Shift
 * adds the range). Keyboard: arrows move the active row, Shift extends,
 * Home/End jump, Ctrl+A selects all, Escape clears. Touch (mobile roadmap):
 * {@link enterSelectionMode} switches plain taps to toggle — the long-press
 * gesture wiring is the consuming surface's job. Swing analog: JTable's
 * MULTIPLE_INTERVAL_SELECTION as read through SwingTableView.getSelectedEvents.
 */
export class TableSelection<K> {
  private keys: K[] = [];
  private anchorKey: K | null = null;
  private readonly selectedSet = signal<ReadonlySet<K>>(new Set<K>());

  readonly selected = this.selectedSet.asReadonly();
  readonly active = signal<K | null>(null);
  readonly selectionMode = signal(false);
  readonly count = computed(() => this.selectedSet().size);

  /** Install the rendered row order; selection/anchor/active are reconciled
   *  (vanished keys drop out — a full clear on re-query is the caller's call). */
  setRows(keys: readonly K[]): void {
    this.keys = [...keys];
    const present = new Set(keys);
    const kept = new Set([...this.selectedSet()].filter((k) => present.has(k)));
    if (kept.size !== this.selectedSet().size) this.selectedSet.set(kept);
    if (this.anchorKey !== null && !present.has(this.anchorKey)) this.anchorKey = null;
    const active = this.active();
    if (active !== null && !present.has(active)) this.active.set(null);
    if (kept.size === 0 && this.selectionMode()) this.selectionMode.set(false);
  }

  /** Reconcile the selected set from an EXTERNAL source of truth (e.g. the
   *  filter chips the rail mirrors into) — unknown keys drop out, anchor and
   *  active stay untouched. Call before an interaction when the mirrored
   *  state can change behind the model's back (PRD 099 Phase 4). */
  syncSelected(keys: readonly K[]): void {
    const present = new Set(this.keys);
    this.selectedSet.set(new Set(keys.filter((k) => present.has(k))));
  }

  isSelected(key: K): boolean {
    return this.selectedSet().has(key);
  }

  /** Selected keys in rendered order (the order bulk actions run in). */
  selectedKeys(): K[] {
    return this.keys.filter((k) => this.selectedSet().has(k));
  }

  pointer(key: K, mods: SelectionMods = {}): void {
    if (!this.keys.includes(key)) return;
    if (this.selectionMode() && !mods.shift) {
      this.toggle(key);
    } else if (mods.shift && this.anchorKey !== null) {
      const range = this.range(this.anchorKey, key);
      this.selectedSet.set(mods.ctrl ? new Set([...this.selectedSet(), ...range]) : new Set(range));
    } else if (mods.ctrl) {
      this.toggle(key);
    } else {
      this.selectedSet.set(new Set([key]));
      this.anchorKey = key;
    }
    this.active.set(key);
    if (this.selectionMode() && this.selectedSet().size === 0) this.selectionMode.set(false);
  }

  /** Handle a navigation key; returns true when consumed (→ preventDefault). */
  key(key: string, mods: SelectionMods = {}): boolean {
    switch (key) {
      case 'ArrowDown':
        return this.move(1, mods);
      case 'ArrowUp':
        return this.move(-1, mods);
      case 'Home':
        return this.moveTo(0, mods);
      case 'End':
        return this.moveTo(this.keys.length - 1, mods);
      case 'a':
      case 'A':
        if (!mods.ctrl) return false;
        this.selectedSet.set(new Set(this.keys));
        return true;
      case 'Escape':
        if (this.selectedSet().size === 0 && !this.selectionMode()) return false;
        this.clear();
        return true;
      default:
        return false;
    }
  }

  enterSelectionMode(): void {
    this.selectionMode.set(true);
  }

  clear(): void {
    this.selectedSet.set(new Set<K>());
    this.anchorKey = null;
    this.active.set(null);
    this.selectionMode.set(false);
  }

  private toggle(key: K): void {
    const next = new Set(this.selectedSet());
    if (next.has(key)) next.delete(key);
    else next.add(key);
    this.selectedSet.set(next);
    this.anchorKey = key;
  }

  private range(from: K, to: K): K[] {
    const a = this.keys.indexOf(from);
    const b = this.keys.indexOf(to);
    if (a < 0 || b < 0) return b >= 0 ? [to] : [];
    const [lo, hi] = a <= b ? [a, b] : [b, a];
    return this.keys.slice(lo, hi + 1);
  }

  private move(delta: 1 | -1, mods: SelectionMods): boolean {
    if (this.keys.length === 0) return false;
    const active = this.active();
    const current = active === null ? -1 : this.keys.indexOf(active);
    const next =
      current < 0
        ? delta === 1
          ? 0
          : this.keys.length - 1
        : Math.min(Math.max(current + delta, 0), this.keys.length - 1);
    return this.moveTo(next, mods);
  }

  private moveTo(index: number, mods: SelectionMods): boolean {
    if (this.keys.length === 0 || index < 0 || index >= this.keys.length) return false;
    const key = this.keys[index];
    if (mods.shift) {
      if (this.anchorKey === null) this.anchorKey = this.active() ?? key;
      this.selectedSet.set(new Set(this.range(this.anchorKey, key)));
    } else {
      this.selectedSet.set(new Set([key]));
      this.anchorKey = key;
    }
    this.active.set(key);
    return true;
  }
}
