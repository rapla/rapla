# Dynamic types

Rapla doesn't have a fixed schema for "what is a room" or "what is an
event." Admins define **Dynamic Types** (`DynamicType`) at runtime —
each dynamic type is a schema with a list of **Attributes**, and each
domain entity (Allocatable, Reservation, the User's person record)
holds a **Classification** that points at one DynamicType and stores
its attribute values.

This is the schema-on-data system that makes Rapla deployable to a
university (course / room / lecturer attributes), an office (meeting /
project / desk attributes), or a hospital (procedure / theatre /
equipment attributes) without code changes.

## Concepts in 30 seconds

```
Admin defines:
   DynamicType "course"  ─owns─▶ Attribute "name"        (STRING)
                                 Attribute "credits"     (INT, default 3)
                                 Attribute "instructor"  (ALLOCATABLE → person)

User books:
   Reservation R23 ─classification─▶ ClassificationImpl
                                     {
                                       typeId: "dt-course",
                                       data: {
                                         "name":       ["Calculus 101"],
                                         "credits":    ["4"],
                                         "instructor": ["alloc-789"]
                                       }
                                     }
   R23.getName() → ParsedText("{name} ({credits} credits)")
                 → "Calculus 101 (4 credits)"
```

The instance side stores **everything as strings** (ids for refs, ISO
strings for dates). Typed access via `Classification.getValue(...)` does
the conversion lazily.

## Built-in vs admin-defined types

DynamicTypes carry a `classificationType` annotation
(`DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE`,
`rapla-core/.../entities/dynamictype/DynamicTypeAnnotations.java`).
The values matter:

| Annotation value | Constant | Meaning |
|---|---|---|
| `rapla` | `VALUE_CLASSIFICATION_TYPE_RAPLATYPE` | Built-in / framework type. Don't mess with. |
| `resource` | `VALUE_CLASSIFICATION_TYPE_RESOURCE` | Bookable thing (room, projector). Used as Allocatable schema. |
| `person` | `VALUE_CLASSIFICATION_TYPE_PERSON` | Person resource. Used for Allocatables that represent people. |
| `reservation` | `VALUE_CLASSIFICATION_TYPE_RESERVATION` | Event schema. Used as Reservation classification. |

A user with admin rights defines new DynamicTypes for the latter
three categories through the admin panel. Built-in types
(`rapla:reservation`, `rapla:resource`, `rapla:person`, …) are
reserved for the framework.

## The schema side: DynamicType + Attribute

**DynamicType**
(`rapla-core/src/main/java/org/rapla/entities/dynamictype/DynamicType.java`,
impl: `internal/DynamicTypeImpl.java`)

Holds:

- `key` — String identifier, unique among types (`"course"`, `"meeting"`).
- `name` — `MultiLanguageName` for display (`"Course"` / `"Kurs"`).
- `attributes` — ordered `List<Attribute>`.
- `permissions` — `List<Permission>` controlling who can create instances of this type.
- `annotations` — `Map<String, String>` of UI / formatting hints.

Factory methods on DynamicType:

- `newClassification()` — fresh blank Classification using attribute defaults.
- `newClassificationFrom(other)` — copy-construct from another classification.
- `newClassificationFilter()` — build a query filter for this type.

**Attribute**
(`rapla-core/.../dynamictype/Attribute.java`, impl: `AttributeImpl.java`)

Defines one column. Fields:

- `key` — String (`"name"`, `"credits"`).
- `type` — `AttributeType` enum.
- `defaultValue` — Object.
- `optional` — boolean.
- `constraints` — `Map<String, Object>`.
- `annotations` — `Map<String, String>` (UI hints).

### `AttributeType`

`rapla-core/.../entities/dynamictype/AttributeType.java`:

| Value | Java type stored |
|---|---|
| `STRING` | `String` |
| `INT` | `Long` (yes, long not int) |
| `BOOLEAN` | `Boolean` |
| `DATE` | `LocalDateTime` |
| `CATEGORY` | `Category` (held as id ref) |
| `ALLOCATABLE` | `Allocatable` (held as id ref) |

### `ConstraintIds`

`rapla-core/.../entities/dynamictype/ConstraintIds.java`:

- `KEY_ROOT_CATEGORY` — for `CATEGORY` attributes; restricts the
  picker to a specific subtree.
- `KEY_DYNAMIC_TYPE` — for `ALLOCATABLE` attributes; restricts the
  reference target to allocatables of a given DynamicType
  (e.g. "instructor must be a person").
- `KEY_MULTI_SELECT` — boolean; allows multiple values per key.
- `KEY_BELONGS_TO`, `KEY_PACKAGE` — bookkeeping flags.

### `AttributeAnnotations` (UI hints)

`rapla-core/.../entities/dynamictype/AttributeAnnotations.java`:

- `KEY_EDIT_VIEW` — `main-view`, `additional-view`, or `no-view`.
- `KEY_COLOR` — boolean / hex color hint for visual rendering.
- `KEY_EMAIL` — marks a string attribute as an email field.
- `KEY_EXPECTED_ROWS`, `KEY_EXPECTED_COLUMNS` — text-area sizing.
- `KEY_SORTING` — `ascending` / `descending`.

### `DynamicTypeAnnotations` (type-level)

`rapla-core/.../entities/dynamictype/DynamicTypeAnnotations.java`:

- `KEY_NAME_FORMAT` — template, e.g. `"{name} ({credits} credits)"`.
- `KEY_NAME_FORMAT_PLANNING`, `KEY_NAME_FORMAT_EXPORT` — context-specific
  templates.
- `KEY_CLASSIFICATION_TYPE` — see table above.
- `KEY_COLORS`, `KEY_CONFLICTS`, `KEY_LOCATION` — admin flags.

## The instance side: Classification

**Classification**
(`rapla-core/.../dynamictype/Classification.java`,
impl: `internal/ClassificationImpl.java`)

A Classification is one row of attribute values for one DynamicType.
Internally it stores everything as `Map<attributeKey, List<String>>`
(a list because `KEY_MULTI_SELECT` allows multiple values).

Key methods:

- `getType()` — the DynamicType this is an instance of.
- `getValue(key)` / `getValueForAttribute(attr)` — first value, typed.
- `getValues(attr)` — all values (collection), typed.
- `setValue(key, value)` / `setValues(attr, Collection)` — write.
- `addValue(attr, value)` / `addRefValue(attr, ReferenceInfo)` —
  append to a multi-select.
- `getName(Locale)` — formatted display name (see "ParsedText" below).

### Why values are stored as strings

Three reasons:

1. **Wire format.** XML and JSON both want strings; storing strings
   internally avoids a conversion at every serialization boundary.
2. **Refs.** `CATEGORY` and `ALLOCATABLE` values are always entity
   ids — strings — and they need to round-trip without resolution
   (the referenced entity might not be loaded yet).
3. **Schema migration.** When an admin changes an attribute's type,
   the data map can hold the old string until
   `ClassificationImpl.commitChange()` runs.

The cost: `getValue()` does string parsing (`fromString`) every call
unless cached. For attributes that are read in tight loops (like
`getName()` in calendar rendering), the result is memoized in
`ClassificationImpl.name` (a `TextCache`).

### `Classifiable`

`rapla-core/.../dynamictype/Classifiable.java` — the mixin for "I have
a classification." Implemented by:

- `Allocatable`
- `Reservation`
- The User's person record (an Allocatable of type `person`)

## Name templates: `ParsedText`

Display names are templates, not stored strings. The template lives in
the DynamicType's `KEY_NAME_FORMAT` annotation
(or `KEY_NAME_FORMAT_PLANNING` / `KEY_NAME_FORMAT_EXPORT` for context-specific
overrides).

`ParsedText` (`rapla-core/.../dynamictype/internal/ParsedText.java`)
parses templates like `{name} ({credits} credits)` into static text +
function calls, then evaluates them against a Classification at
render time.

```
Template:    "{name} ({credits} credits)"

ParsedText:  ["", function(getValue, "name"), " (", function(getValue, "credits"), " credits)"]

Eval (Classification, Locale):
   ""                        →  ""
   getValue("name")          →  "Calculus 101"
   " ("                      →  " ("
   getValue("credits")       →  "4"
   " credits)"               →  " credits)"

Result:                       "Calculus 101 (4 credits)"
```

Functions are pluggable via `FunctionFactory`
(`rapla-core/.../entities/extensionpoints/FunctionFactory.java`).
The standard set is `StandardFunctions` (sub-package
`internal/`); plugins like `appointmentnote` and
`eventtimecalculator` register custom functions
(see `ServerCoreConfig.functionFactory` beans).

## UI: where the schema is edited and used

### Editing the schema (admin panel)

`rapla-client/src/main/java/org/rapla/client/swing/internal/edit/DynamicTypeEditUI.java`
is the dialog where admins edit DynamicTypes. Its sub-component
`AttributeEdit` handles per-attribute editing — picking the
AttributeType, setting constraints, choosing default values, editing
annotations. Field editors live in
`rapla-client/.../client/swing/internal/edit/fields/` and are
type-specific (TextField, MultiLanguageField, BooleanField,
DateField, CategorySelectField, AllocatableSelectField).

### Editing instances (every reservation / resource dialog)

Reservation and Allocatable edit dialogs build their attribute UI by
iterating `classification.getType().getAttributes()` and creating one
field editor per Attribute. The field editor reads
`classification.getValue(attr)` and writes back via
`classification.setValue(attr, value)` on commit. Adding a new
Attribute to an existing DynamicType therefore "just works" — every
existing reservation gains an empty field of the new type next time
its dialog opens.

## Wire format

### XML (server file storage)

The XML writer serializes a Classification as a single element named
after the DynamicType key, with one child element per attribute:

```xml
<dynatt:course>
  <dynatt:name>Calculus 101</dynatt:name>
  <dynatt:credits>4</dynatt:credits>
  <dynatt:instructor>allocatable-789</dynatt:instructor>
</dynatt:course>
```

- The element name (`dynatt:course`) doubles as the type discriminator.
- Reference values (CATEGORY, ALLOCATABLE) are written as the target
  entity's id.
- Multi-valued attributes appear as multiple sibling elements with the
  same name.

Reader: `rapla-core/.../storage/xml/DynAttReader.java`.
Writer: `rapla-core/.../storage/xml/ClassifiableWriter.java`.

### JSON (REST wire format)

Jackson (since PRD 011) serializes the implementation directly via
`@JsonProperty` on `ClassificationImpl` private fields. The shape:

```json
{
  "classification": {
    "typeId": "dt-456",
    "type":   "course",
    "data": {
      "name":       ["Calculus 101"],
      "credits":    ["4"],
      "instructor": ["allocatable-789"]
    }
  }
}
```

This is the same shape as the in-memory representation. See
PRD 010 (field-based wire format) and PRD 011 (Jackson 3 migration)
for history.

## Storage and migration

The DynamicType definition itself is persisted (XML or JDBC); existing
Classifications are *not* rewritten when the schema changes:

1. Admin adds Attribute "credits" to DynamicType "course."
2. Existing Reservations of type course are not touched.
3. When one of those Reservations is loaded, `getValues("credits")`
   returns an empty collection (the key isn't in `data`).
4. UI shows the Attribute's `defaultValue` for blank slots.
5. On the next save, `setValue("credits", ...)` populates `data`.

If the admin **removes** an attribute or **changes its type**,
`ClassificationImpl.commitChange()` runs as part of the dispatch
pipeline to reshape existing Classifications (drop the key, or
re-parse the old string into the new type).

`DynamicTypeDependant`
(`rapla-core/.../entities/storage/DynamicTypeDependant.java`) is the
mixin for entities that need to react to schema changes — Allocatable,
Reservation, Preferences, and Classification all implement it.

## Worked example: a course reservation, end to end

**Schema:**

```
DynamicType "course"
  attribute "name"        STRING,     optional=false, default="New Course"
  attribute "credits"     INT,        optional=true,  default=3
  attribute "instructor"  ALLOCATABLE constraint KEY_DYNAMIC_TYPE="person"
  annotation nameformat   "{name} ({credits} credits)"
```

**In memory:**

```
ReservationImpl R23
  classification: ClassificationImpl {
    typeId:  "dt-course"
    type:    "course"        // fallback key if id can't resolve yet
    data: {
      "name":       ["Calculus 101"],
      "credits":    ["4"],
      "instructor": ["allocatable-789"]
    }
    resolver: <bound to LocalCache>
  }
```

**`R23.getName(EN)`:**

1. Reservation.getName delegates to Classification.getName.
2. Classification looks up `KEY_NAME_FORMAT` annotation on its type.
3. ParsedText evaluates `{name}` → `"Calculus 101"`, `{credits}` → `"4"`.
4. Returns `"Calculus 101 (4 credits)"`. Cached in `TextCache.nameString`.

**`R23.getClassification().getValue("instructor")`:**

1. Look up `data["instructor"]` → `["allocatable-789"]`.
2. Attribute type is ALLOCATABLE → `resolver.tryResolve("allocatable-789", Allocatable.class)`.
3. Returns the `AllocatableImpl` for Dr Smith (which itself has
   classification of type `person` with attributes name, email, …).

**On store:**

1. The XML writer emits `<dynatt:course>` with three child elements.
2. Or the Jackson writer emits the JSON shape above.
3. Server applies dispatch, updates `LocalCache`, fires
   `ModificationEvent` to other clients.

## See also

- [domain-model.md](domain-model.md) — entity catalog (DynamicType,
  Attribute, Classification entries)
- [permissions.md](permissions.md) — DynamicType-level permissions
  control who can create / read instances of a given type
- [reservation-edit.md](reservation-edit.md) — how the edit dialog
  builds its UI from the schema
- PRD 010 (Jackson field-based wire format), PRD 011 (Jackson 3) —
  serialization history
