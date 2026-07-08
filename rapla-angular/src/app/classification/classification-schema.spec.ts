import { describe, expect, it } from 'vitest';
import { parseClassificationSdl, remapValues } from './classification-schema';

/**
 * Tier-5 — PRD 096 Phase 1.1. Fixture mirrors the LIVE printed SDL from
 * GET /api/graphql/schema: graphql-java's printer puts a space before the
 * argument colon (`value : "..."`), fields come one per line with 2-space
 * indent, VALUE_LIST roots surface as enums with description-line labels.
 */
const SDL = `
directive @displayName(value: String!) on FIELD_DEFINITION

interface Classification {
  type: DynamicType!
  typeKey: String!
}

interface AllocatableClassification implements Classification {
  type: DynamicType!
  typeKey: String!
}

enum gruppierungen_c7 {
  "HDMI Kabel"
  c1
  "XLR Kabel"
  c2
  c4
}

"""
Generated classification for DynamicType \`event\`.
"""
type eventClassification implements Classification & ReservationClassification {
  name: String @displayName(value : "eventname") @required @editView(value : "title")
  beschreibung: String @displayName(value : "Beschreibung \\"lang\\"")
  intern: String @displayName(value : "Intern") @editView(value : "no-view")
  notiz: String @displayName(value : "Notiz") @editView(value : "additional")
  type: DynamicType!
  typeKey: String!
}

type roomClassification implements AllocatableClassification & Classification {
  name: String
  seats: Int @displayName(value : "Plätze")
  projector: Boolean @displayName(value : "Beamer")
  renovated: LocalDateTime @displayName(value : "Renoviert am")
  raumart: Category @displayName(value : "Raumart") @rootCategory(path : "Raumtypen")
  gruppe: gruppierungen_c7 @displayName(value : "Gruppe") @rootCategory(path : "gruppierungen/c7")
  gebaeude: Allocatable @displayName(value : "Gebäude") @expectedType(key : "building")
  ausstattung: [gruppierungen_c7!] @displayName(value : "Ausstattung") @multiplicity(value : LIST)
  zubehoer: [Allocatable!] @displayName(value : "Zubehör") @multiplicity(value : PACKAGE)
  type: DynamicType!
  typeKey: String!
}
`;

describe('parseClassificationSdl (PRD 096 D1)', () => {
  const types = parseClassificationSdl(SDL);

  it('finds the generated types with their kind, skipping the interfaces', () => {
    expect([...types.keys()].sort()).toEqual(['event', 'room']);
    expect(types.get('event')!.kind).toBe('RESERVATION');
    expect(types.get('room')!.kind).toBe('ALLOCATABLE');
  });

  it('skips the interface fields type/typeKey', () => {
    const keys = types.get('event')!.attributes.map((a) => a.key);
    expect(keys).toEqual(['name', 'beschreibung', 'intern', 'notiz']);
  });

  it('reads @displayName (printer spacing + escaped quotes), falls back to the key', () => {
    const event = types.get('event')!.attributes;
    expect(event[0].label).toBe('eventname');
    expect(event[1].label).toBe('Beschreibung "lang"');
    const room = new Map(types.get('room')!.attributes.map((a) => [a.key, a]));
    expect(room.get('name')!.label).toBe('name');
  });

  it('maps SDL types to attribute valueTypes', () => {
    const room = new Map(types.get('room')!.attributes.map((a) => [a.key, a]));
    expect(room.get('name')!.valueType).toBe('STRING');
    expect(room.get('seats')!.valueType).toBe('INT');
    expect(room.get('projector')!.valueType).toBe('BOOLEAN');
    expect(room.get('renovated')!.valueType).toBe('DATE');
    expect(room.get('raumart')!.valueType).toBe('CATEGORY');
    expect(room.get('gruppe')!.valueType).toBe('CATEGORY');
    expect(room.get('gebaeude')!.valueType).toBe('ALLOCATABLE');
  });

  it('resolves VALUE_LIST enums with description labels; tree categories stay null', () => {
    const room = new Map(types.get('room')!.attributes.map((a) => [a.key, a]));
    expect(room.get('gruppe')!.enumValues).toEqual([
      { key: 'c1', label: 'HDMI Kabel' },
      { key: 'c2', label: 'XLR Kabel' },
      { key: 'c4', label: 'c4' },
    ]);
    expect(room.get('raumart')!.enumValues).toBeNull();
    expect(room.get('raumart')!.rootCategoryPath).toBe('Raumtypen');
  });

  it('reads @required, @editView, @expectedType, list wrappers and @multiplicity', () => {
    const event = new Map(types.get('event')!.attributes.map((a) => [a.key, a]));
    expect(event.get('name')!.required).toBe(true);
    expect(event.get('name')!.editView).toBe('title');
    expect(event.get('beschreibung')!.required).toBe(false);
    expect(event.get('beschreibung')!.editView).toBe('main');
    expect(event.get('intern')!.editView).toBe('no-view');
    expect(event.get('notiz')!.editView).toBe('additional');
    const room = new Map(types.get('room')!.attributes.map((a) => [a.key, a]));
    expect(room.get('gebaeude')!.expectedTypeKey).toBe('building');
    expect(room.get('gebaeude')!.list).toBe(false);
    expect(room.get('gebaeude')!.multiplicity).toBe('SINGLE');
    expect(room.get('ausstattung')!.list).toBe(true);
    expect(room.get('ausstattung')!.multiplicity).toBe('LIST');
    expect(room.get('zubehoer')!.multiplicity).toBe('PACKAGE');
  });
});

describe('remapValues (PRD 096 Phase 2.2 — Swing newClassificationFrom parity)', () => {
  const types = parseClassificationSdl(SDL);
  const event = types.get('event')!.attributes;
  const room = types.get('room')!.attributes;

  it('keeps same-key same-type values, drops the rest', () => {
    const values = { name: 'Kamera-Schulung', beschreibung: 'mit Stativ' };
    expect(remapValues(values, event, room)).toEqual({ name: 'Kamera-Schulung' });
  });

  it('drops values whose valueType differs on the target', () => {
    const from = [...room];
    const values = { name: 'x', seats: 12 };
    // event has no seats; name survives
    expect(remapValues(values, from, event)).toEqual({ name: 'x' });
  });

  it('never invents keys and ignores values absent from the source descriptors', () => {
    const values = { unknown: 'y', name: 'z' };
    expect(remapValues(values, event, room)).toEqual({ name: 'z' });
  });
});
