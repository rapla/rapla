import { describe, expect, it, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Subject, of } from 'rxjs';

import { UndoToastService } from './undo-toast.service';
import type { SpaCommand } from './command';
import type { MutationResult } from '../graphql/mutation-result';

const ok: MutationResult<unknown> = { kind: 'ok', data: {} };
const concurrent: MutationResult<unknown> = { kind: 'concurrent', issues: [] };

describe('UndoToastService (PRD 094 Phase 2)', () => {
  let snackOpen: ReturnType<typeof vi.fn>;
  let action$: Subject<void>;
  let service: UndoToastService;

  beforeEach(() => {
    action$ = new Subject<void>();
    snackOpen = vi.fn(() => ({ onAction: () => action$.asObservable() }));
    TestBed.configureTestingModule({
      providers: [{ provide: MatSnackBar, useValue: { open: snackOpen } }],
    });
    service = TestBed.inject(UndoToastService);
  });

  it('successful execute shows the toast with Rückgängig and fires mutated$', () => {
    const mutations: number[] = [];
    service.mutated$.subscribe(() => mutations.push(1));
    const command: SpaCommand = { label: 'gelöscht', execute: () => of(ok), undo: () => of(ok) };
    service.run(command);
    expect(snackOpen).toHaveBeenCalledWith('gelöscht', 'Rückgängig', expect.anything());
    expect(mutations.length).toBe(1);
  });

  it('clicking Rückgängig runs the inverse and fires mutated$ again', () => {
    const undoExec = vi.fn(() => of(ok));
    const mutations: number[] = [];
    service.mutated$.subscribe(() => mutations.push(1));
    service.run({ label: 'gelöscht', execute: () => of(ok), undo: undoExec });
    action$.next();
    expect(undoExec).toHaveBeenCalledTimes(1);
    expect(mutations.length).toBe(2);
    expect(snackOpen).toHaveBeenLastCalledWith('Rückgängig gemacht', undefined, expect.anything());
  });

  it('a CONCURRENT inverse fails loudly, never silently', () => {
    service.run({ label: 'gelöscht', execute: () => of(ok), undo: () => of(concurrent) });
    action$.next();
    const lastMessage = String(snackOpen.mock.calls.at(-1)?.[0] ?? '');
    expect(lastMessage).toContain('inzwischen geändert');
  });

  it('failed execute shows an error toast and does NOT fire mutated$', () => {
    const mutations: number[] = [];
    service.mutated$.subscribe(() => mutations.push(1));
    service.run({ label: 'x', execute: () => of(concurrent), undo: null });
    expect(mutations.length).toBe(0);
    expect(String(snackOpen.mock.calls.at(-1)?.[0])).toContain('inzwischen geändert');
  });
});

describe('UndoToastService — header command history (PRD 094 D2 revised)', () => {
  let snackOpen: ReturnType<typeof vi.fn>;
  let service: UndoToastService;

  beforeEach(() => {
    snackOpen = vi.fn(() => ({ onAction: () => new Subject<void>().asObservable() }));
    TestBed.configureTestingModule({
      providers: [{ provide: MatSnackBar, useValue: { open: snackOpen } }],
    });
    service = TestBed.inject(UndoToastService);
  });

  const cmd = (
    label: string,
    undoResult: MutationResult<unknown> = ok,
    execResult: MutationResult<unknown> = ok,
  ): SpaCommand => ({
    label,
    execute: () => of(execResult),
    undo: () => of(undoResult),
  });

  it('run pushes onto the undo stack; canUndo becomes true, canRedo false', () => {
    expect(service.canUndo()).toBe(false);
    service.run(cmd('A gelöscht'));
    expect(service.canUndo()).toBe(true);
    expect(service.canRedo()).toBe(false);
    expect(service.undoLabel()).toBe('A gelöscht');
  });

  it('undo() runs the inverse, moves the entry to redo; redo() re-executes it', () => {
    const undoExec = vi.fn(() => of(ok));
    const forwardExec = vi.fn(() => of(ok));
    service.run({ label: 'A', execute: forwardExec, undo: undoExec });
    forwardExec.mockClear();
    service.undo();
    expect(undoExec).toHaveBeenCalledTimes(1);
    expect(service.canUndo()).toBe(false);
    expect(service.canRedo()).toBe(true);
    expect(service.redoLabel()).toBe('A');
    service.redo();
    expect(forwardExec).toHaveBeenCalledTimes(1);
    expect(service.canRedo()).toBe(false);
    expect(service.canUndo()).toBe(true);
  });

  it('a new action clears the redo stack', () => {
    service.run(cmd('A'));
    service.undo();
    expect(service.canRedo()).toBe(true);
    service.run(cmd('B'));
    expect(service.canRedo()).toBe(false);
  });

  it('a failed undo still fires mutated$ (a bulk inverse may have partially applied — PRD 099 OQ2)', () => {
    const mutations: number[] = [];
    service.mutated$.subscribe(() => mutations.push(1));
    service.run(cmd('A', concurrent));
    expect(mutations.length).toBe(1);
    service.undo();
    expect(mutations.length).toBe(2);
  });

  it('drop-on-stale: a failed undo drops the entry (not pushed to redo) and shows an error', () => {
    service.run(cmd('A', concurrent));
    service.undo();
    expect(service.canUndo()).toBe(false);
    expect(service.canRedo()).toBe(false); // dropped, not queued for redo
    expect(String(snackOpen.mock.calls.at(-1)?.[0])).toContain('inzwischen geändert');
  });

  it('cap 5: the oldest entry is evicted', () => {
    for (let i = 0; i < 6; i++) service.run(cmd(`C${i}`));
    let count = 0;
    while (service.canUndo()) {
      service.undo();
      count++;
    }
    expect(count).toBe(5);
  });

  it('the toast Rückgängig action pops the same top entry (no double undo)', () => {
    const action$ = new Subject<void>();
    snackOpen.mockReturnValue({ onAction: () => action$.asObservable() });
    const undoExec = vi.fn(() => of(ok));
    service.run({ label: 'A', execute: () => of(ok), undo: undoExec });
    action$.next(); // click Rückgängig in the toast
    expect(undoExec).toHaveBeenCalledTimes(1);
    expect(service.canUndo()).toBe(false); // entry consumed, not still on the stack
  });
});
