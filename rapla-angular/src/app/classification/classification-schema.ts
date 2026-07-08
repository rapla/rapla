/**
 * PRD 096 Phase 1 — client side of the PRD 055 β "schema-as-data" decision.
 *
 * The attribute structure of a DynamicType is NOT a descriptor endpoint; it
 * IS the generated `<typeKey>Classification` SDL type. Plain introspection
 * cannot see the custom directives (@displayName, @required, @expectedType,
 * @rootCategory, @multiplicity, @editView), so the descriptor source is the printed SDL
 * from GET /api/graphql/schema. The generator emits one field per line and
 * graphql-java's printer normalizes spacing (`value : "..."`), which this
 * hand parser tolerates — no `graphql` npm dependency needed.
 */

export type AttributeValueType = 'STRING' | 'INT' | 'BOOLEAN' | 'DATE' | 'CATEGORY' | 'ALLOCATABLE';
export type Multiplicity = 'SINGLE' | 'LIST' | 'BELONGS_TO' | 'PACKAGE';
export type ClassificationKind = 'RESERVATION' | 'ALLOCATABLE';
export type EditView = 'title' | 'main' | 'additional' | 'no-view';

export interface EnumValue {
  key: string;
  label: string;
}

export interface AttributeDescriptor {
  key: string;
  /** @displayName, falling back to the key. */
  label: string;
  valueType: AttributeValueType;
  list: boolean;
  required: boolean;
  /**
   * @editView placement — 'title' (prominent header field, derived from the
   * nameformat's direct attribute references), 'main' (default), 'additional'
   * (details), 'no-view' (hidden in editors).
   */
  editView: EditView;
  multiplicity: Multiplicity;
  /** ALLOCATABLE attrs — target DynamicType key (@expectedType). */
  expectedTypeKey: string | null;
  /** CATEGORY tree attrs — allowed root key path (@rootCategory). */
  rootCategoryPath: string | null;
  /** CATEGORY VALUE_LIST attrs — the generated enum's values; null for tree categories. */
  enumValues: EnumValue[] | null;
}

export interface ClassificationType {
  typeKey: string;
  kind: ClassificationKind;
  attributes: AttributeDescriptor[];
}

const SCALAR_TYPES: Record<string, AttributeValueType> = {
  String: 'STRING',
  Int: 'INT',
  Boolean: 'BOOLEAN',
  LocalDateTime: 'DATE',
  Category: 'CATEGORY',
  Allocatable: 'ALLOCATABLE',
};

/** Interface fields present on every generated type — not attributes. */
const NON_ATTRIBUTE_FIELDS = new Set(['type', 'typeKey']);

const RESERVED_TYPE_NAMES = new Set([
  'Classification',
  'AllocatableClassification',
  'ReservationClassification',
]);

function unescapeStringLiteral(raw: string): string {
  return raw.replace(/\\(["\\])/g, '$1');
}

interface ParsedDirectives {
  displayName: string | null;
  required: boolean;
  editView: EditView;
  expectedTypeKey: string | null;
  rootCategoryPath: string | null;
  multiplicity: Multiplicity | null;
}

function parseDirectives(rest: string): ParsedDirectives {
  const out: ParsedDirectives = {
    displayName: null,
    required: false,
    editView: 'main',
    expectedTypeKey: null,
    rootCategoryPath: null,
    multiplicity: null,
  };
  const stringArg =
    /@(displayName|expectedType|rootCategory|editView)\s*\(\s*\w+\s*:\s*"((?:[^"\\]|\\.)*)"\s*\)/g;
  for (const m of rest.matchAll(stringArg)) {
    const value = unescapeStringLiteral(m[2]);
    if (m[1] === 'displayName') out.displayName = value;
    else if (m[1] === 'expectedType') out.expectedTypeKey = value;
    else if (m[1] === 'editView') out.editView = value as EditView;
    else out.rootCategoryPath = value;
  }
  const mult = /@multiplicity\s*\(\s*value\s*:\s*(\w+)\s*\)/.exec(rest);
  if (mult) out.multiplicity = mult[1] as Multiplicity;
  if (/@required\b/.test(rest)) out.required = true;
  return out;
}

function parseEnums(sdl: string): Map<string, EnumValue[]> {
  const enums = new Map<string, EnumValue[]>();
  const blocks = sdl.matchAll(/^enum\s+(\w+)\s*\{([\s\S]*?)^\}/gm);
  for (const block of blocks) {
    const values: EnumValue[] = [];
    let pendingLabel: string | null = null;
    for (const rawLine of block[2].split('\n')) {
      const line = rawLine.trim();
      if (line.length === 0) continue;
      const desc = /^"((?:[^"\\]|\\.)*)"$/.exec(line);
      if (desc) {
        pendingLabel = unescapeStringLiteral(desc[1]);
        continue;
      }
      const key = /^(\w+)\s*$/.exec(line);
      if (key) {
        values.push({ key: key[1], label: pendingLabel ?? key[1] });
        pendingLabel = null;
      }
    }
    enums.set(block[1], values);
  }
  return enums;
}

/**
 * Parse the printed SDL into per-typeKey attribute descriptors. Only the
 * generated `<typeKey>Classification` object types are considered; the
 * interfaces and the rest of the schema are ignored.
 */
export function parseClassificationSdl(sdl: string): Map<string, ClassificationType> {
  const enums = parseEnums(sdl);
  const result = new Map<string, ClassificationType>();
  const blocks = sdl.matchAll(/^type\s+(\w+)Classification\s+implements\s+([^{]+)\{([\s\S]*?)^\}/gm);
  for (const block of blocks) {
    const typeKey = block[1];
    if (RESERVED_TYPE_NAMES.has(typeKey + 'Classification')) continue;
    const kind: ClassificationKind = block[2].includes('ReservationClassification')
      ? 'RESERVATION'
      : 'ALLOCATABLE';
    const attributes: AttributeDescriptor[] = [];
    for (const rawLine of block[3].split('\n')) {
      const field = /^\s{2}(\w+)\s*:\s*(\[)?(\w+)!?(?:!?\])?!?\s*(.*)$/.exec(rawLine);
      if (!field) continue;
      const key = field[1];
      if (NON_ATTRIBUTE_FIELDS.has(key)) continue;
      const list = field[2] === '[';
      const sdlType = field[3];
      const directives = parseDirectives(field[4]);
      const scalar = SCALAR_TYPES[sdlType];
      const enumValues = scalar ? null : (enums.get(sdlType) ?? []);
      attributes.push({
        key,
        label: directives.displayName ?? key,
        valueType: scalar ?? 'CATEGORY',
        list,
        required: directives.required,
        editView: directives.editView,
        multiplicity: directives.multiplicity ?? (list ? 'LIST' : 'SINGLE'),
        expectedTypeKey: directives.expectedTypeKey,
        rootCategoryPath: directives.rootCategoryPath,
        enumValues,
      });
    }
    result.set(typeKey, { typeKey, kind, attributes });
  }
  return result;
}

/** Tree categories + allocatable refs read as objects — select their id. */
export function readsAsObject(d: AttributeDescriptor): boolean {
  return d.valueType === 'ALLOCATABLE' || (d.valueType === 'CATEGORY' && d.enumValues === null);
}

/**
 * Field selections for a full-value classification read (the pass-through
 * needs EVERY attribute, not just rendered ones). Object-valued attributes
 * select `{ id }`; `normalizeClassificationValues` flattens them back to ids
 * so the values echo cleanly into the write-side @oneOf input variant.
 */
export function valueSelections(attributes: AttributeDescriptor[]): string {
  return attributes.map((d) => (readsAsObject(d) ? `${d.key} { id }` : d.key)).join(' ');
}

function normalizeValue(raw: unknown): unknown {
  if (Array.isArray(raw)) return raw.map(normalizeValue);
  if (raw !== null && typeof raw === 'object' && 'id' in (raw as Record<string, unknown>)) {
    return (raw as Record<string, unknown>)['id'];
  }
  return raw;
}

export function normalizeClassificationValues(
  cls: Record<string, unknown>,
): Record<string, unknown> {
  const values: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(cls)) {
    if (k === '__typename' || k === 'typeKey' || k === 'type') continue;
    values[k] = normalizeValue(v);
  }
  return values;
}

/**
 * PRD 096 Phase 2.2 — type-change value remapping (Swing
 * `newClassificationFrom` parity, client-side): an attribute value survives
 * the switch when the target type has an attribute with the SAME key and the
 * SAME valueType/cardinality. Everything else is dropped — the server stores
 * the supplied classification as-is, and the target @oneOf input only knows
 * the target type's fields.
 */
export function remapValues(
  values: Record<string, unknown>,
  from: AttributeDescriptor[],
  to: AttributeDescriptor[],
): Record<string, unknown> {
  const fromByKey = new Map(from.map((d) => [d.key, d]));
  const out: Record<string, unknown> = {};
  for (const target of to) {
    if (!(target.key in values)) continue;
    const source = fromByKey.get(target.key);
    if (!source) continue;
    if (source.valueType !== target.valueType || source.list !== target.list) continue;
    out[target.key] = values[target.key];
  }
  return out;
}
