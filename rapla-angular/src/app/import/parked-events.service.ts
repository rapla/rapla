import { Injectable, signal } from '@angular/core';

/** A staged item waiting in the Parkstreifen — NOT yet stored anywhere: taking
 *  over only parks; the server-side create happens when the chip is dropped. */
export interface ParkedItem {
  sourceId: string;
  name: string;
  kind: 'v' | 'p';
  lectureTemplateId: string | null;
  examTemplateId: string | null;
  /** Kurs allocatables the item belongs to — become allocations of the draft. */
  groups: { id: string; name: string }[];
}

/**
 * PRD 104 v3 Parkstreifen (reworked 2026-08-11, user rule "beim Parken soll noch
 * gar nichts gespeichert werden"): purely client-side staging of the items the
 * user took over. Nothing exists server-side until a chip is DROPPED — on a free
 * slot (creates there, the ONE undoable "platziert" action) or on an existing
 * event (binds). Reload or Abbrechen simply forgets the list; the Halde items
 * stay OPEN.
 */
@Injectable({ providedIn: 'root' })
export class ParkedEventsService {
  readonly items = signal<readonly ParkedItem[]>([]);

  add(items: ParkedItem[]): void {
    if (items.length === 0) return;
    const known = new Set(this.items().map((i) => i.sourceId));
    this.items.set([...this.items(), ...items.filter((i) => !known.has(i.sourceId))]);
  }

  remove(sourceId: string): void {
    this.items.set(this.items().filter((i) => i.sourceId !== sourceId));
  }

  clear(): void {
    this.items.set([]);
  }
}
