# Domain model

Rapla's domain has roughly a dozen entity types, related by a handful
of patterns:

- **Composite parents** (Reservation owns Appointments; DynamicType
  owns Attributes) — implemented via `ParentEntity`.
- **Schema-on-data classification** (Allocatable / Reservation / User
  point at a DynamicType through a Classification holding string
  values) — see [dynamic-types.md](dynamic-types.md).
- **Reference-by-id with deferred resolution** (`ReferenceInfo<T>`
  + `EntityResolver`) — entities don't hold each other directly,
  they hold typed id refs that resolve on demand.

The rest of this page is a reference catalog. For each type:
**Interface** points at the public API (`rapla-core`); **Impl** points
at the `internal` package implementation. **Points to** lists the
outgoing relationships that matter when you trace data.

## Type hierarchy

```
RaplaObject              (root, type discriminator)
   └── Entity<T>         (storable, has getId())
          ├── User
          ├── Category
          ├── Allocatable
          ├── Reservation
          ├── Appointment
          ├── Period
          ├── Attribute
          ├── DynamicType
          ├── Preferences
          └── ExternalSyncEntity
   └── Permission        (RaplaObject but not Entity — embedded in containers)
   └── Classification    (RaplaObject but not Entity — embedded in Classifiable)

Mixins (interfaces, not classes)
   ├── Ownable           — has owner User
   ├── Annotatable       — has key/value annotations
   ├── Timestamp         — has createDate, lastChangedBy, lastChanged
   ├── PermissionContainer — owns a list of Permissions
   ├── Classifiable      — has a Classification (→ DynamicType)
   ├── ParentEntity      — owns sub-entities (cascade on delete)
   └── DynamicTypeDependant — reacts to schema changes
```

`RaplaType` is the static registry that maps each `Entity` subtype to a
short `localname` (`"reservation"`, `"resource"`, `"user"`, …) used by
serialization. See `rapla-core/src/main/java/org/rapla/entities/RaplaType.java`.

---

## Identity, references, resolution

Every persistent entity has a String id. References between entities
are stored as `ReferenceInfo<T>(id, Class<T> type)`, not as direct
object pointers. This is what allows lazy loading from the server to
the client, and what lets the storage layer rewrite ids during
import / export.

| Concept | File |
|---|---|
| `ReferenceInfo<T>` | `rapla-core/src/main/java/org/rapla/entities/storage/ReferenceInfo.java` |
| `EntityResolver` | `rapla-core/src/main/java/org/rapla/entities/storage/EntityResolver.java` |
| `EntityReferencer` (mixin) | `rapla-core/src/main/java/org/rapla/entities/storage/EntityReferencer.java` |
| `SimpleEntity` (impl base) | `rapla-core/src/main/java/org/rapla/entities/storage/internal/SimpleEntity.java` |
| `ReferenceHandler` | `rapla-core/src/main/java/org/rapla/entities/storage/internal/ReferenceHandler.java` |

Pattern: code asks `resolver.tryResolve(id, X.class)` to get an entity
back, or `resolver.resolve(id, X.class)` to throw
`EntityNotFoundException` if missing. Both `LocalCache` (the in-memory
index) and `RemoteOperator` (the client transport) implement
`EntityResolver`.

### Id format and assignment

Ids are generated server-side by `LocalAbstractCachableOperator.createId`
(→ `createIdentifier(type, count)`): a `UUID.randomUUID().toString()` whose
**first character is replaced by a type-prefix letter** derived from the entity
type (`replaceFirst`). The letter makes a bare id string self-describing:

The full set of `createId`-UUID-carrying entities. **All prefix letters are hex
characters** (since 2026-07-06), so every server-generated id is a grammatically
valid v4 UUID (the version/variant bits at positions 14/19 are untouched by the
letter replacement):

| Type | localname | Prefix | Example |
|---|---|---|---|
| Reservation | `reservation` | **`e`** (event — `r` was taken by resource) | `e47ac10b-58cc-4372-…` |
| Appointment | `appointment` | **`a`** | `a47ac10b-…` |
| Attribute | `attribute` | **`a`** | `a47ac10b-…` |
| Category | `category` | **`c`** | `c47ac10b-…` |
| DynamicType | `dynamictype` | **`d`** | `d47ac10b-…` |
| Allocatable | `resource` | **`f`** (facility; was **`r`** — not hex) | `f47ac10b-…` |
| User | `user` | **`b`** (Benutzer; was **`u`** — not hex) | `b47ac10b-…` |

**Legacy prefixes `r…` (Allocatable) and `u…` (User)** exist in every store
created before the switch and are *not* migrated — lookup is by full opaque
string, so they resolve exactly as before; only newly generated ids get the
hex-valid letters. A system that wants uniform valid-UUID ids can do an explicit
id migration (own project — ids leak into serialized preferences, exchange-sync
mappings, and external calendar URLs). Pinned by
`rapla-server/.../CreateIdPrefixTest`.

The letters are not type-unique (`a` = Appointment *or* Attribute) — the type
always travels separately in `ReferenceInfo`. Rapla treats all ids as opaque
strings and never parses them back into `java.util.UUID`. A seed-based variant
`createId(type, seed)` derives a deterministic MD5-based UUID — its only use is
the legacy old-format id migration in `RaplaXMLReader.getId` /
`OldIdMapping.isTextId` (numeric / `resource_123`-style ids from ancient data
files), dormant in normal operation.

Not every entity type gets a `createId` UUID — several have **derived ids**:

| Type | Id shape |
|---|---|
| Preferences | `preferences_<userId>`, system prefs `preferences_0` (`PreferencesImpl.getPreferenceIdFromUser`) |
| Conflict | composite `CONFLICT;<allocId>;<app1Id>;<app2Id>;<date>`, parsed by `split(";")` |
| Period | wraps the id of its `rapla:period` allocatable |
| ExternalSyncEntity | the external system's own id (e.g. Exchange key) — set from the `externalID`, never generated |

And some `RaplaType`-registered types have **no id at all** — they are value
objects embedded in Preferences, not `Entity` (their registration only serves
type-name serialization): `RaplaConfiguration`, `CalendarModelConfiguration`,
`RaplaMap`.

**The prefix letter is not load-bearing.** No code derives an entity's type from
the id's first character — the type always travels explicitly in
`ReferenceInfo(id, Class)`, and `LocalCache` indexes by the full id string. The
letter is historical + human-readable + a structural cross-type-uniqueness nicety
(an `e…` reservation never collides with an `a…` appointment even on an identical
UUID tail). `replaceFirst` runs **only** on server-generated ids.

**Client-supplied ids are stored verbatim** — the GraphQL create path does
`setId(clientId)` with no rewrite; the server never mutates an id it receives.
**Syntax rule for new ids** (`Tools.isValidEntityId`, enforced in
`checkIdIntegrity` at the dispatch choke point): ASCII alphanumerics + hyphen,
alphanumeric first char, length 8–64 — deliberately *not* a UUID-structure check
(ids are opaque; the charset just excludes `;` / whitespace / escaping hazards).
Applies to NEW Reservation / Appointment / Allocatable entities only; ids already
persistent are grandfathered (legacy `period_1`-style ids keep saving). Collision
handling: [PRD 056](../prd/056-graphql-events-write-api.md) §9.

**Client ids are MANDATORY on GraphQL creates** (decided 2026-07-06, [PRD 056](../prd/056-graphql-events-write-api.md) §9):
`createReservation` (reservation + every appointment) and `createAllocatable`
reject id-less input with `REQUIRED` — the server-generate fallback is removed.
Why:

- **Idempotency is id-based, with no content comparison** ([PRD 056](../prd/056-graphql-events-write-api.md) OQ5 revised):
  a retry whose response was lost re-sends the same client-minted id, gets
  `ID_COLLISION`, and maps that to "already applied". Only a client-generated id
  makes this possible — a server-generated id gives the retry no shared key, so
  an id-less create is structurally non-idempotent (silent duplicates).
- **Every workaround re-invents the client token with extra infrastructure:** a
  separate idempotency-key store needs a multi-pod-shared key→response table
  with TTL/GC; JMAP-style tempIds need a mapping protocol; both still require
  the client to mint a random string.
- **One contract instead of two:** the old "B′" conditional ("appointment ids
  required only when allocation restrictions reference a subset") collapses —
  restrictions always join on appointment ids that are always present. The SPA
  can also use its id optimistically (no temp-id swap).
- **Precedent — the calendar standards do exactly this:** iCalendar requires a
  *creator*-generated `UID` on every VEVENT (RFC 5545; RFC 7986 recommends a
  random UUID), and CalDAV creates via `PUT` on a client-chosen URL with
  `If-None-Match: *` → `412` on collision — rapla follows that model with
  `ID_COLLISION` instead of `412`. Google Calendar's optional client id exists
  precisely for retry idempotency (409 = already there); MS Graph (strictly
  server-ids) had to retrofit `transactionId` — a client-minted random token —
  to stop duplicate-on-retry.

Exception: **server-initiated** creates (e.g. `copyReservations`) mint server
ids — there is no client input to be idempotent against. A copy re-ids its
appointments too (`clone()` keeps sub-entity ids; keeping them would trip the
foreign-reservation appointment guard, `checkIdIntegrity` #2).

---

## User

**Interface:** `rapla-core/src/main/java/org/rapla/entities/User.java`
**Impl:** `rapla-core/src/main/java/org/rapla/entities/internal/UserImpl.java`

The authentication / authorization principal. Owns Reservations and
Allocatables; can belong to Category groups; can be flagged `isAdmin()`
to bypass all permission checks.

**Key fields / methods:**

- `getUsername()`, `getEmail()`, `getName()`
- `isAdmin()` — global admin bypass
- `getGroupList()` — `Collection<Category>` of groups the user is in
  (direct membership)
- `getGroupsIncludingParents()` — transitive group set with all
  ancestor groups (used by permission resolution)
- `getPerson()` — optional reference to an Allocatable of type
  `rapla:person`, so a user can have a contact-info record

**Points to:** Allocatable (person), Category (groups).
**Referenced by:** every Ownable, every `Timestamp.lastChangedBy`,
Permission (`user`).

---

## Category

**Interface:** `rapla-core/src/main/java/org/rapla/entities/Category.java`
**Impl:** `rapla-core/src/main/java/org/rapla/entities/internal/CategoryImpl.java`

A hierarchical tree of categories. Categories play three roles:

1. **User groups** — the children of the conventional `user-groups`
   root category are the groups users can be assigned to.
2. **Permission targets** — a Permission can target a group Category.
3. **Attribute values** — Attributes of type `CATEGORY` reference
   nodes in a category subtree, so e.g. a "department" attribute
   on a Reservation uses categories.

**Key methods:** `addCategory()`, `getCategories()`, `getParent()`,
`getKey()`, `getPath()` (slash-separated key path), `isAncestorOf()`.
The root is `superCategory` — `facade.getSuperCategory()`.

---

## Allocatable

**Interface:** `rapla-core/src/main/java/org/rapla/entities/domain/Allocatable.java`
**Impl:** `rapla-core/src/main/java/org/rapla/entities/domain/internal/AllocatableImpl.java`

A bookable thing. Rooms, equipment, and persons are all Allocatables —
distinguished by their `Classification.getType()` (DynamicType
classified as `resource` vs `person`). `Allocatable.isPerson()` is
sugar for "this allocatable's DynamicType is annotated as person."

**Key methods:**

- `getClassification()` — the schema-driven attribute bag
- `getName(Locale)` — formatted name, evaluating the DynamicType
  `nameformat` template
- `getPermissionList()` — the per-allocatable ACL
- `isPerson()`
- `getAllocateInterval()` — optional global "this resource is only
  available between X and Y" window

**Points to:** Classification → DynamicType, Permission[], User (owner).
**Referenced by:** Reservation (`allocatables` and per-appointment
`restrictions`), Permission (group ACLs), Attributes of type
`ALLOCATABLE`.

---

## Reservation

**Interface:** `rapla-core/src/main/java/org/rapla/entities/domain/Reservation.java`
**Impl:** `rapla-core/src/main/java/org/rapla/entities/domain/internal/ReservationImpl.java`

The central event entity. A Reservation is one logical event (e.g. a
course) with one or more Appointments (its time-slots) that allocate
one or more Allocatables.

**Key methods:**

- `addAppointment(Appointment)` / `getAppointments()` /
  `getSortedAppointments()` (chronological)
- `addAllocatable(Allocatable)` / `getAllocatables()`
- `setRestriction(Allocatable, Appointment[])` —
  per-appointment allocation: by default an allocatable applies
  to every appointment in the reservation, but you can restrict
  it to just some
- `getRestriction(Allocatable)` / `getAppointmentsFor(Allocatable)`

**The restriction model — granularity note.** Restrictions are
**appointment-level**, not block-level. A restriction binds an
allocatable to specific appointments; if an appointment recurs,
every materialized block of that appointment inherits the same
allocatable set. Two consequences for downstream callers:

- "Which allocatables are bound to *this appointment*?" is the
  natural query — `getAppointmentsFor()` inverted. External APIs
  ([PRD 035](../prd/done/035-graphql-foundations.md)) expose this as `Appointment.allocatables` (pre-resolved
  through restriction); blocks inherit it via their parent.
- The restriction structure itself (`appointmentIds` per
  allocatable, with `null` meaning "bound to all") is only relevant
  to editor-shaped consumers that round-trip the reservation back
  on save. Read-only / listview consumers should never see it.
- `getFirstDate()`, `getMaxEnd()` — derived bounds across appointments
- `getClassification()` — the metadata bag (title, course code, etc.)
- `getPermissionList()` — per-reservation ACL (who can read this event)
- `getRequestStatus(Allocatable)` — request workflow state

**Points to:** Appointment[] (composite), Allocatable[] (referenced),
Classification → DynamicType, Permission[], User (owner).

---

## Appointment + Repeating + AppointmentBlock

**Interface:** `rapla-core/src/main/java/org/rapla/entities/domain/Appointment.java`
**Impl:** `rapla-core/src/main/java/org/rapla/entities/domain/internal/AppointmentImpl.java`

A single time block, optionally with a repeat rule. Always belongs to
exactly one Reservation (its parent).

**Key methods:** `getStart()`, `getEnd()`, `getRepeating()`, `move()`,
`overlapsAppointment(other)`, `createBlocks(start, end, list)`.

**Repeating** (`rapla-core/src/main/java/org/rapla/entities/domain/Repeating.java`)
is the recurrence rule:

| `RepeatingType` | Step rule |
|---|---|
| `DAILY` | `start + interval × N` days |
| `WEEKLY` | `start + 7 × interval × N` days, optionally restricted to specific weekdays |
| `MONTHLY` | **Nth weekday of the month** (e.g. "third Thursday"), NOT same day-of-month |
| `YEARLY` | Same date next year. **Feb 29 anchor: skips non-leap years entirely** — does NOT roll to Feb 28. |

Stored fields: `interval`, `type`, `number` (count) or `end` (date),
`weekdays` (Set<Integer>), `exceptions` (Set<LocalDateTime>). See
[../conflict-detection.md](../conflict-detection.md) for the overlap
algorithm and [conflicts-and-events.md §Repeating](conflicts-and-events.md#repeating)
for worked examples + the regression-test pins.

**AppointmentBlock**
(`rapla-core/src/main/java/org/rapla/entities/domain/AppointmentBlock.java`)
is the materialized form of one occurrence. It is **not persisted** —
it's computed by expanding the appointment's repeating rule on demand.
A block has start, end, a back-reference to its appointment, and an
`isException` flag. Calendar views render blocks, not appointments.

**Recurrence-aware consumers (iCal, CalDAV) work at the Appointment
level, not the block level** — they consume RRULE / EXDATE natively
and expand client-side. Rapla's own calendar UI expands server-side
into blocks because the UI grid is indexed by time, not by
appointment. External APIs ([PRD 035](../prd/done/035-graphql-foundations.md)) therefore expose appointments
as a primary read shape with blocks as an opt-in materialization
(`Appointment.blocks(from:, to:)`) — same query root, two
consumption shapes.

---

## DynamicType, Attribute, Classification

These define the schema-on-data system. See
[dynamic-types.md](dynamic-types.md) for the full treatment;
the entities at a glance:

- **DynamicType** (`rapla-core/.../entities/dynamictype/DynamicType.java`)
  — the schema. Has a key (`"course"`), a list of Attributes, and
  annotations (most importantly `nameformat`).
- **Attribute** (`Attribute.java`) — one column. Has a key, a type
  (STRING / INT / DATE / BOOLEAN / CATEGORY / ALLOCATABLE), constraints,
  defaultValue, multi-select flag.
- **Classification** (`Classification.java`) — instance of a
  DynamicType. Holds attribute values internally as
  `Map<attributeKey, List<String>>`. Refs (CATEGORY / ALLOCATABLE) are
  stored as ids and resolved through `EntityResolver`.

`Classifiable` is the mixin for "I have a Classification": Allocatable,
Reservation, and the User's person record all implement it.

---

## Period

**Interface:** `rapla-core/.../entities/domain/Period.java`
**Impl:** `rapla-core/.../entities/domain/internal/PeriodImpl.java`

An academic / business term (e.g. "Summer 2026"). A lightweight
named time interval, optionally tagged with categories. Used by views
that want to filter to "this semester."

---

## Permission

**Interface:** `rapla-core/.../entities/domain/Permission.java`
**Impl:** `rapla-core/.../entities/domain/internal/PermissionImpl.java`

One ACL row. Has `user` xor `group`, an `accessLevel`, and optionally
a time window (absolute `pStart`/`pEnd` or relative
`minAdvance`/`maxAdvance`). Embedded in PermissionContainer entities
(Allocatable, Reservation, DynamicType, Category).

`AccessLevel` is an enum with nine ordered values from `DENIED` to
`ADMIN`. See [permissions.md](permissions.md) for the model.

---

## Preferences, RaplaConfiguration, CalendarModelConfiguration

| Type | What it stores |
|---|---|
| `Preferences` | Per-user or system-wide config. Indexed by `TypedComponentRole<T>`. |
| `RaplaConfiguration` | XML-shaped config tree (extends `DefaultConfiguration`). |
| `CalendarModelConfiguration` | Snapshot of a calendar view (start/end/selected dates, view type, selected allocatables, filters, title). Shareable as a "saved view." |
| `RaplaMap<T>` | Generic typed map for collections of `RaplaObject`s, e.g. saved export profiles. |

All four live under `rapla-core/.../entities/configuration/`.

---

## Conflict (facade-level, computed)

**Interface:** `rapla-core/src/main/java/org/rapla/facade/Conflict.java`
**Impl:** `rapla-core/.../facade/internal/ConflictImpl.java`

A Conflict is *not* an Entity in the same sense as Reservation — it's
computed by the server's `ConflictFinder` and surfaced through the
facade. Each Conflict identifies one Allocatable and two
(Reservation, Appointment) pairs that overlap on it. See
[conflicts-and-events.md](conflicts-and-events.md) and
[../conflict-detection.md](../conflict-detection.md).

**Aggregation granularity:** the conflict primitive is
**appointment-pair plus a list of dates**, not block-pair. Two
weekly recurring lectures sharing Room A produce **one** Conflict
spanning N dates, not N Conflicts — the Swing client renders them
that way, and external APIs ([PRD 035](../prd/done/035-graphql-foundations.md)) preserve the same shape
(`{ allocatable, otherAppointment, dates: [LocalDate!]! }`). A
block-pair shape would force every consumer to re-aggregate.

---

## CalendarSelectionModel

**Interface:** `rapla-core/src/main/java/org/rapla/facade/CalendarSelectionModel.java`

Mutable, in-memory view state. Holds: which dates are visible, which
allocatables are selected, which filters are active, which view type
("week", "month", "table", …). The Swing client mutates this; views
listen and re-render. Saving a CalendarSelectionModel produces a
`CalendarModelConfiguration` that can be persisted into Preferences.

---

## ER summary

Arrows are "points to / contains":

```
User ─owns─▶ Reservation, Allocatable, Preferences, DynamicType
User ─memberOf─▶ Category[ ]   (groups)

Category ─parent─▶ Category    (hierarchy)

Allocatable ─classification─▶ DynamicType
            ─permissions─▶ Permission[ ]
            ─owner─▶ User

Reservation ─owns─▶ Appointment[ ]               (composite)
            ─allocates─▶ Allocatable[ ]          (reference)
            ─restriction─▶ Map<Allocatable, Appointment[ ]>
            ─classification─▶ DynamicType
            ─permissions─▶ Permission[ ]
            ─owner─▶ User

Appointment ─parent─▶ Reservation
            ─repeating─▶ Repeating?              (optional)
            ─generates─▶ AppointmentBlock[ ]    (computed, not persisted)

DynamicType ─owns─▶ Attribute[ ]
            ─permissions─▶ Permission[ ]

Classification ─type─▶ DynamicType
               ─values─▶ Map<attrKey, List<String>>
                           (where String is either a literal value
                            or the id of a Category / Allocatable)

Permission ─grantedTo─▶ User | Category | (everybody)

Conflict ─involves─▶ Allocatable, Appointment×2, Reservation×2
                                                 (computed)

Period ─tagged─▶ Category[ ]
```

## See also

- [dynamic-types.md](dynamic-types.md) — Classification, Attribute,
  ParsedText name templates
- [permissions.md](permissions.md) — Permission resolution
- [conflicts-and-events.md](conflicts-and-events.md) — Reservation as
  event container
- [reservation-edit.md](reservation-edit.md) — entity lifecycle
  through the edit dialog
