# PRD 111 — Wiring stored documents into the UI

**Status:** DECIDED 2026-09-02 (D1–D6) and **v1 IMPLEMENTED the same day** (plugin `org.rapla.plugin.documents`: `documents` type annotation, `DynamicType.documents` resolver, row-menu provider for table/week/month, Swing `DocumentsAnnotationEdit`; reactor green; shipped in the Siegen package). Later phases (event sheet, resource tree, picker in the type editor) open.
**Related:** [PRD 097 § Phase 7](097-event-html-templates-mustache.md), [PRD 074 § Window and inputs directives](074-graphql-declarative-views.md), [PRD 094](094-spa-main-view-actions-and-popups.md), [PRD 099](099-spa-table-selection.md), [PRD 089](089-server-side-recents-favorites.md)

## The question

A stored document (PRD 097: view + Mustache template, rendered at `GET /api/documents/<name>`)
is today reachable only by typing its URL. Users need it offered *in the SPA*, on the object it is
about — a loan slip on the event, a device sheet on the resource, an occupancy list on the room.

**What is undecided is not "can we add a menu entry" but where the wiring between a document and a
UI surface lives, and how precisely it can be targeted.** A boolean `inMenu` on the document is the
smallest thing that works; a per-deployment assignment layer with selectors is the widest. This PRD
is the concept work to pick a point on that line.

Immediate driver: an equipment-lending delivery needs one document on the event context menu
(details in the private delivery folder, not here). That delivery must not decide this design.

## Verified current state (2026-09-02)

Server:
- `GET /api/documents` lists documents for the caller — already permission-scoped
  (`DocumentCatalogService.list`), and the render endpoint reports an unreadable document exactly
  like a missing one (§12).
- Views declare their public inputs: `directive @param(name, into, required) repeatable on QUERY`
  (`schema.graphqls:934`), parsed by `ViewParamDirectives.parse`. **No type information**: a param
  is `ID!` whether it wants a reservation or a resource.
- `@view(listed: false)` (`ViewCatalogService:400`, 2026-08-12) already keeps a
  parameterised view out of the SPA switcher and the GraphiQL load dialog while staying renderable.
- `listViews` is the existing precedent for a caller-scoped catalog query over GraphQL.
- There is **no** `documents` GraphQL query — the document catalog is REST-only today.

SPA:
- No document knowledge at all: `/api/documents` appears nowhere in `rapla-angular/`.
- Row menus: `ROW_MENU_PROVIDERS` (multi-provider, `row-menu.ts:33`) with exactly one consumer,
  `view-host.component.ts:607` — one provider covers table, week and month.
- `RowMenuProvider.items(ctx)` is **synchronous** (`row-menu.ts:29`): no server round-trip while
  building a menu. Anything the menu decides must already be in memory.
- `RowContext` (`row-context.ts`) carries `primary` (`EntityRef {kind, id, canModify}`),
  `entities` (secondary allocatable refs from cells), `subjects`, `block {appointmentId, start,
  isException}`, the raw `rows`, and `viewName`. `EntityKind = 'reservation' | 'allocatable' | 'user'`.
- **`EntityRef` carries no dynamic-type / classification key.** Type-based targeting therefore has
  no data today (see § The evaluation-context constraint).
- The event sheet is not a `RowContext` consumer — an entry there is separate work, not a free rider.

## Axis 1 — where the wiring lives

1. **On the view's param** — `@param(…, kind: RESERVATION)`. The param's entity kind is intrinsic to
   the query (`$reservationId` feeds `reservation(id:)` and can be nothing else), so it is stated
   where it is true, and it is authored once for every document over that view.
   *Flaw:* a view backs many documents (the calendar view already backs a week and a month
   document); placement declared here is inherited by all of them.
2. **On the document** — `inMenu` + `title`. Matches how exposure is already configured
   (`isPublic`, `groups`, `defaultVariables`, `window`), and the template editor is where an author
   would look. *Flaw:* repeats per document what the view already implies about its params.
3. **Split (1) + (2)** — the view says *what each param is*, the document says *whether it is
   offered and under what label*. Each fact stated once; the "binding" of a click to a param falls
   out of the kind. Current front-runner from the design discussion.
4. **A separate assignment artifact** — a preference (server-side, like PRD 089's prefs JSON)
   mapping UI surfaces → documents, authored by an admin rather than by the document author.
   *For:* one place to see and order the whole menu; can be scoped per user group; documents stay
   pure content. *Against:* a second artifact to keep in sync, dangling references when a document
   is deleted, and two places to look when an entry does not appear.

Note that (4) is not exclusive with (1)–(3): a document could carry a default exposure that an
admin preference overrides. Whether that flexibility is worth two authorities is a real question,
not a rhetorical one.

## Axis 2 — targeting granularity

- **a. Entity kind only** — reservation / allocatable / user. Enough for the immediate driver.
- **b. + dynamic type** — "this loan slip only on events of type `ausleihe`". The first genuinely
  needed refinement in a multi-type deployment: a loan slip offered on a lecture is noise.
- **c. + classification predicate** — "only on resources whose `kategorie` is `Kamera`". Rapla
  already has an expression engine (`compute(expr:)`, PRD 073) — a selector should reuse it rather
  than invent a predicate language.
- **d. + permission / user group** — "only for the loan-desk group". Note the catalog is already
  caller-scoped, so this is about *relevance*, not access.

Each step up costs data in the click context (Axis 3) and authoring UI.

## Axis 3 — the evaluation-context constraint (the hard one)

The menu is built **synchronously** from the row of the view the user is *browsing*. The document
being offered belongs to a *different* view. So a selector can only see what the browsing view
happens to have selected — and the document author does not control the browsing view.

Consequences to weigh:

- Kind-only targeting works today (`EntityRef.kind`).
- Type/classification targeting needs the browsing view to select the type key (a `@hidden` field
  convention such as `typeKey`), which every existing stored view would have to adopt — or
  `RowContext` must be enriched from a source other than the row.
- A server-evaluated selector needs either a pre-fetched per-context answer (impossible in general)
  or an **async menu API** — a change to `RowMenuProvider.items()`'s contract, and to every provider.
- Filtering *after* the click (open the document, let it render empty / 404) is the one option that
  needs no data — and the worst UX; it makes a broken entry look like a broken document.

**This axis, not Axis 1, is what actually bounds the design.** A concept that promises classification
selectors without answering it is not implementable.

## Axis 4 — which surfaces

Row context menu (table / week / month) is one seam and covers the common case. Beyond it:
the event sheet (not a `RowContext` consumer), the resource tree / resource detail, a toolbar or
"Print…" submenu, and multi-select (PRD 099: `primary` is null, `subjects` carries N — a document
takes one id, so either the entry hides or a list-valued param is filled). Each surface is separate
work; the concept should say which are in the model, not which are built first.

## Axis 5 — lifecycle and authoring

- **Label + i18n.** A menu label lives in the data file, so the JAR's i18n bundles (PRD 103) cannot
  carry it. Options: literal only; or resolve the label through the bundle with the literal as
  fallback (a deployment writes a key when it needs nine languages).
- **Ordering and grouping** when several documents match one object.
- **Dangling wiring** — a document deleted while an assignment (Axis 1.4) still references it.
- **Discoverability** — an author who set everything right and still sees no entry needs a reason
  ("no menu shows this document because …"), which argues for validation at save time over silent
  non-appearance.
- **Migration** — whatever is decided must be reachable from what a deployment already has: stored
  documents today carry no wiring at all, and absence must keep meaning "not offered".

## Non-goals

Anonymous/published document links (PRD 097 Phase 8), a plugin mechanism for UI contributions
(the SPA is one Angular build; a plugin would publish the same metadata from inside the JAR), and
in-SPA rendering of document HTML (PRD 102's sandboxed container tier) — a link opening a new tab
is the model here.

## Decisions (user ruling 2026-09-02)

**D1 — Placement is an annotation on the dynamic type, value = document name(s).** A dynamic
type (event type, resource type, person type) carries an annotation — working key `documents` in
`DynamicTypeAnnotations` — whose value lists the names of stored documents offered on entities of
that type, in menu order. *That annotation is the only thing that is configured.* Consequences:
- Type targeting is built in: a loan slip hangs on `ausleihe`, not on every event. No selector
  anywhere (selectors are out — user ruling in the same session).
- Curated by the admin in the type editor, like `nameformat`/`colors`/`location`. A freshly
  authored document appears nowhere until it is hung on a type — accepted; that is curation.
- The document **name** is the reference, the menu label, and the future i18n key (like type and
  attribute keys: stored identifier + translated label on top). No `title` field; `@view(title:)`
  stays the view's title.
- Editor: v1 = the annotation is a free string in the existing type editor; later = a selection
  of existing documents. Save-time validation can check that each name exists and that the
  document's derived kind (D2) matches the type's classification kind (reservation ↔ event type,
  allocatable ↔ resource/person type).
- Dangling: a deleted document leaves its name in the annotation. v1: the server drops unknown
  names when answering; a picker-based editor later removes the cause.
- Users have no dynamic type, so there is no home for user-anchored documents. Accepted.

**D2 — Binding is derived from the view, not declared.** Context documents are by-id views:
the `@param` variable feeds the `id` argument of a root field (`reservation(id:)`,
`allocatable(id:)`, `user(id:)`), and that root field's return type is the kind. Derived at
save/catalog time by the server; nothing to author, nothing that can drift from the query. A
view whose param feeds a list path (`filter.allocatableIdsIn`) is not bindable and cannot be
hung on a type (validation error). An explicit `anchor:` on `@param` is NOT introduced; it stays
available as a later override if derivation ever proves wrong, without changing existing views.

**D3 — The annotation is exposed on the type; the SPA builds the menu from memory.** The SPA
already loads every dynamic type at bootstrap. `DynamicType` gets a resolved field
`documents: [DocumentRef!]!` (`DocumentRef { name, param }`): the server joins the type's
`documents` annotation with the document catalog *as the caller* (unreadable or deleted names
are dropped — the dangling case from D1 never reaches the client) and derives `param` per D2.
No round-trip on right-click, no view execution, `RowMenuProvider.items()` stays synchronous —
Axis 3 is answered by data the SPA already holds. Requirement that follows: the row subject must
carry the type key. The subject alias grows one selection,
`reservation @hidden { id canModify classification { typeKey } }` (same for `allocatable`),
`EntityRef` gets an optional `typeKey`; the builtin views in `ViewCatalogService` select it, and
a stored view that does not simply shows no document entries (fail closed — a stored view opts
in by selecting the key, no convention is imposed). The raw annotation map is NOT exposed in
v1; a future SPA type editor may add it (see § Open, annotation registry).

**D4 — The feature decides where its entries are inserted (rapla 2 menu model).** There is no
generic server-side "actions for entity" query. The documents feature ships its own SPA
contribution — one `RowMenuProvider` (PRD 094 D3 multi-provider, one consumer covers table,
week and month) that maps the row subject's kind + typeKey → `type.documents` → entries
`name` → `/api/documents/<name>?<param>=<id>` (new tab); later the same feature adds its entry
to the event sheet and the resource tree in the same way. Other features (plugins) contribute
their own providers reading their own annotations. Multi-select hides document entries (one id
per document). Server plugins cannot add SPA menu entries — the SPA is one build (§ Non-goals).

**D6 — It is a plugin, enabled by default, gated the rapla 3 way (both layers).** Scope of the
plugin = the context wiring only: the `documents` annotation + its Swing editor field, the
`DynamicType.documents` resolver, the SPA menu provider. The document pipeline itself
(`/api/documents`, template editor, builtin week/month documents) stays core and always on.
Gates, both defaulting to ON like tableview/export2ical (works without configuration, inert
without an annotation):
1. static: `@ConditionalOnProperty(prefix = "rapla.services", name = "org.rapla.plugin.documents",
   matchIfMissing = true)` — a deployment can remove the beans entirely;
2. runtime: system preference `<PLUGIN_ID>.enabled` with `ENABLE_BY_DEFAULT = true`, listed and
   toggled via the admin plugins controller (`/plugins/{id}/enabled`).
Two readers of the runtime flag: the resolver (empty list when off) and the Swing editor field
(hidden when off). The SPA never reads the flag.

**Implementation defaults (settled with the ruling, change only if implementation contradicts):**
menu shows direct entries, one per document, in annotation order (no submenu); annotation key
`documents`, value = comma-separated document names.

## Prerequisites (small, named so the shape does not read cheaper than it is)

- **Writing the annotation.** Nothing edits it today: `DynamicTypeEditUI.setAnnotations()` writes a
  fixed set, and the SPA has no type editor. The Swing type editor renders a
  `Set<AnnotationEditTypeExtension>` (core: Location, ConflictCreation, ResourceTreeName,
  ExportEventName/Description; plugins: tableview, eventtimecalculator, planningstatus — visible
  only when the plugin is on the classpath, the rapla 2 model). A `DocumentsAnnotationEdit`
  modelled on `LocationAnnotationEdit` (~40 lines, `@Service`, text field; later a picker fed by
  `GET /api/documents`) is the v1 editor. Until it exists the annotation is set by editing the
  data file.
- **Known-keys validation on the GraphQL type mutation** (schema ~1545, unknown keys rejected):
  add the key; longer-term a registry fed by the same contributors, so a plugin annotation
  editable in Swing is not rejected over GraphQL, and a future SPA type editor can ask the server
  which keys exist and what value shape they have ("string first, picker later" without per-key
  UI code).
- **`DynamicType.documents` resolver** + `EntityRef.typeKey` + builtin-view subject selection (D3).

### Options discussed and dropped

- **Anchor + placement on the document / view** (`@param(anchor:)` implying "appears on every
  reservation") — no type targeting without a selector; superseded by D1.
- **Selectors** (`types:` on an anchor, `compute(expr:)` predicates, "limit the view"): out for
  v1 (user ruling). "Limit the view" additionally fails for context documents, which are by-id
  and have no filter to limit — `ReservationFilter` has no id list and forces a window, and
  switching to a list root would reshape the template.
- **"Empty view result ⇒ no entry"** as the applicability mechanism — pointless once placement is
  on the type; the server never needs to run the view to build the menu.
- **`inMenu` boolean / `title` field on the document** — the name is the label (D1); placement is
  the type's (D1).
- **URL template in the annotation value** (`leihschein?reservationId={id}`) — duplicates the
  param the view already knows; binding is derived (D2).
- **Admin preference artifact mapping surfaces → documents** — the type annotation IS the admin
  assignment, on an existing entity with an existing editor and mutation; no new artifact.
- **Plugin contributing the menu entry** — the SPA is one Angular build; a plugin would publish
  the same metadata from inside the JAR, against "customer-specific stays outside the JAR".
- **SPA runs each document's view on right-click** (N requests, client-side emptiness) — replaced
  by D3.
- **Server-side right-click query** (`documentsFor(kind,id)` / generic `actionsFor`) — one
  round-trip per right-click and a server-owned menu; the user ruled for exposure on the type +
  feature-owned insertion (D3/D4).

## Later phases (same feature, not v1)

- Event sheet + resource tree entries (D4).
- Annotation editor as a picker of existing documents (D1); SPA type editor + annotation-key
  registry (§ Prerequisites).
- Label i18n by name once the SPA has an i18n layer.

## Interim note

The immediate delivery does not need any of this: a menu entry gated on the existing REST catalog
(no server change) puts one document on the event context menu today, and is replaced by whatever
is built from the decisions above without invalidating anything authored in a data file. The
delivery-side interim is a separate decision.

## Residue (design session close-out, 2026-09-02)

- If a param-level anchor directive is ever taken up: make the argument an **enum**, not a String — the value set is closed (mirrors `row-context.ts` `EntityKind`), GraphQL validation then rejects typos and GraphiQL autocompletes; a String typo yields a document that silently never appears (worst failure mode for config living in customer data).
- Two save-time rules exist only in the param-level variant and vanish in the annotation variant: at most one anchored param per entity kind; no `required: true` when several params are anchored. Their disappearance is an argument for the chosen document-level wiring.
- The menu label must stay document-level: two documents over one view would otherwise produce two identical `@view(title:)` entries.
- Parked: a view with a required `@param` can never render standalone, so `@view(listed: false)` is arguably implied — kept explicit to avoid surprising deployments that rely on the hint page.
- Parked: save-time check that a document's derived kind does not contradict the param's `into` target where derivable.
- v1 implementation deviations: `DynamicType.documents` is nullable (plugin removable); `CategoryKindClassifier` untouched; catalog is loaded when the SPA store is constructed (first right-click must already see it).
