import { describe, expect, it } from 'vitest';
import { toMutationResult, type GqlErrorShape } from './mutation-result';

interface Payload {
  createReservation: { id: string };
}

interface Envelope {
  data?: Payload;
  errors?: GqlErrorShape[];
}

function resp(partial: Envelope): Envelope {
  return partial;
}

describe('toMutationResult (PRD 091 Phase 2.1)', () => {
  it('maps data without errors to ok', () => {
    const r = toMutationResult(resp({ data: { createReservation: { id: 'e1' } } }));
    expect(r.kind).toBe('ok');
    if (r.kind === 'ok') expect(r.data.createReservation.id).toBe('e1');
  });

  it('maps CONCURRENT_MODIFICATION to concurrent — even mixed with other codes', () => {
    const r = toMutationResult(
      resp({
        errors: [
          {
            message: 'stale',
            extensions: { code: 'CONCURRENT_MODIFICATION', path: 'expectedLastChanged' },
          },
          { message: 'other', extensions: { code: 'REQUIRED', path: 'x' } },
        ],
      }),
    );
    expect(r.kind).toBe('concurrent');
  });

  it('maps pure permission errors to denied', () => {
    for (const code of ['PERMISSION_DENIED', 'FORBIDDEN', 'UNAUTHENTICATED']) {
      const r = toMutationResult(
        resp({ errors: [{ message: 'no', extensions: { code, path: '' } }] }),
      );
      expect(r.kind).toBe('denied');
    }
  });

  it('maps validation codes to invalid with code+path preserved', () => {
    const r = toMutationResult(
      resp({
        errors: [
          {
            message: 'appointment id is required',
            extensions: { code: 'REQUIRED', path: 'input.appointments[0].id' },
          },
        ],
      }),
    );
    expect(r.kind).toBe('invalid');
    if (r.kind === 'invalid') {
      expect(r.issues).toEqual([
        {
          code: 'REQUIRED',
          path: 'input.appointments[0].id',
          message: 'appointment id is required',
        },
      ]);
    }
  });

  it('keeps ID_COLLISION as invalid (draft layer decides idempotency, PRD 056 §9)', () => {
    const r = toMutationResult(
      resp({ errors: [{ message: 'exists', extensions: { code: 'ID_COLLISION', path: '' } }] }),
    );
    expect(r.kind).toBe('invalid');
  });

  it('mixed denied + validation counts as invalid (not denied)', () => {
    const r = toMutationResult(
      resp({
        errors: [
          { message: 'no', extensions: { code: 'PERMISSION_DENIED', path: 'a' } },
          { message: 'bad', extensions: { code: 'INVALID_VALUE', path: 'b' } },
        ],
      }),
    );
    expect(r.kind).toBe('invalid');
  });

  it('errors without extensions map to UNKNOWN code', () => {
    const r = toMutationResult(resp({ errors: [{ message: 'boom' }] }));
    expect(r.kind).toBe('invalid');
    if (r.kind === 'invalid') expect(r.issues[0].code).toBe('UNKNOWN');
  });

  it('empty envelope (no data, no errors) is a transport failure', () => {
    const r = toMutationResult(resp({}));
    expect(r.kind).toBe('transport');
  });
});
