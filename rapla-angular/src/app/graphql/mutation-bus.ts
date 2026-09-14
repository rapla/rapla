import { Injectable } from '@angular/core';
import { Subject } from 'rxjs';

/**
 * Client-side "something changed on the server" bus. GraphqlService.mutate
 * emits after every successful mutation; views re-query on it.
 *
 * Interim mechanism: this only sees the OWN client's mutations. It is the
 * seam a future server change listener (push/SSE, update-history polling)
 * plugs into — replace the emitter, keep the consumers.
 */
@Injectable({ providedIn: 'root' })
export class MutationBus {
  readonly mutated$ = new Subject<void>();
}
