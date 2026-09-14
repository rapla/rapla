import { describe, expect, it } from 'vitest';

import { documentEntries, documentMenuItems } from './document-menu';
import type { RowContext } from './row-context';

/** PRD 111 D4 — kind + typeKey → the type's `documents` → one direct entry per document. */
describe('documentEntries', () => {
  const types = new Map([
    [
      'ausleihe',
      [
        { name: 'Leihschein', param: 'reservationId' },
        { name: 'Rueckgabe', param: 'reservationId' },
      ],
    ],
    ['geraet', [{ name: 'Geraetebogen', param: 'id' }]],
  ]);

  const ctx = (over: Partial<RowContext>): RowContext => ({
    primary: null,
    entities: [],
    subjects: [],
    block: { appointmentId: null, start: null, isException: false },
    rows: [{}],
    viewName: 'v',
    ...over,
  });

  it('offers one entry per annotated document, in annotation order', () => {
    const items = documentEntries(
      ctx({ primary: { kind: 'reservation', id: 'e1', typeKey: 'ausleihe' } }),
      types,
    );
    expect(items.map((i) => i.label)).toEqual(['Leihschein', 'Rueckgabe']);
    expect(items[0].url).toBe('/api/documents/Leihschein?reservationId=e1');
  });

  it('encodes the id into the query string', () => {
    const items = documentEntries(
      ctx({ primary: { kind: 'resource', id: 'a/1 x', typeKey: 'geraet' } }),
      types,
    );
    expect(items[0].url).toBe('/api/documents/Geraetebogen?id=a%2F1+x');
  });

  it('offers nothing without a typeKey (view did not select it — fail closed)', () => {
    expect(documentEntries(ctx({ primary: { kind: 'reservation', id: 'e1' } }), types)).toEqual([]);
  });

  it('offers nothing for a type that carries no documents', () => {
    expect(
      documentEntries(
        ctx({ primary: { kind: 'reservation', id: 'e1', typeKey: 'vorlesung' } }),
        types,
      ),
    ).toEqual([]);
  });

  it('offers nothing on multi-select (a document takes exactly one id)', () => {
    expect(
      documentEntries(
        ctx({
          primary: null,
          rows: [{}, {}],
          subjects: [{ kind: 'reservation', id: 'e1', typeKey: 'ausleihe' }],
        }),
        types,
      ),
    ).toEqual([]);
  });

  it('offers nothing before the catalog has loaded', () => {
    expect(
      documentEntries(
        ctx({ primary: { kind: 'reservation', id: 'e1', typeKey: 'ausleihe' } }),
        new Map(),
      ),
    ).toEqual([]);
  });
});

describe('documentMenuItems', () => {
  it('maps entries onto RowMenuItems that open a new tab', () => {
    const opened: string[] = [];
    const items = documentMenuItems(
      {
        primary: { kind: 'reservation', id: 'e1', typeKey: 'ausleihe' },
        entities: [],
        subjects: [],
        block: { appointmentId: null, start: null, isException: false },
        rows: [{}],
        viewName: 'v',
      },
      {
        documentsByTypeKey: () =>
          new Map([['ausleihe', [{ name: 'Leihschein', param: 'reservationId' }]]]),
      },
      (url) => opened.push(url),
    );
    expect(items.map((i) => i.label)).toEqual(['Leihschein']);
    items[0].run();
    expect(opened).toEqual(['/api/documents/Leihschein?reservationId=e1']);
  });
});
