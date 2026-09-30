import { DOCUMENT, DestroyRef, Injector, runInInjectionContext, signal } from '@angular/core';

import { UnsavedChangesService } from './unsaved-changes';

function setup() {
  const injector = Injector.create({ providers: [{ provide: DOCUMENT, useValue: document }] });
  const svc = runInInjectionContext(injector, () => new UnsavedChangesService());
  let destroy = (): void => undefined;
  const ref = { onDestroy: (fn: () => void) => (destroy = fn) } as unknown as DestroyRef;
  return { svc, ref, destroy: () => destroy() };
}

function unloadPrevented(): boolean {
  const event = new Event('beforeunload', { cancelable: true });
  window.dispatchEvent(event);
  return event.defaultPrevented;
}

describe('UnsavedChangesService', () => {
  it('asks before unload only while a registered editor is dirty', () => {
    const t = setup();
    const dirty = signal(false);
    t.svc.register(dirty, t.ref);
    expect(unloadPrevented()).toBe(false);
    dirty.set(true);
    expect(unloadPrevented()).toBe(true);
    t.destroy();
  });

  it('forgets an editor when it is destroyed', () => {
    const t = setup();
    t.svc.register(signal(true), t.ref);
    t.destroy();
    expect(t.svc.any()).toBe(false);
    expect(unloadPrevented()).toBe(false);
  });
});
