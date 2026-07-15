# Template authoring — documents, views, windows

How a server-rendered **document** works, what the template can see, and how the three classic
calendar shapes (list, week, month, Wochenprogramm) are built from it. This is the author-facing
companion to [PRD 097](prd/097-event-html-templates-mustache.md); the copy-paste starters below
are pinned end-to-end by `CalendarTemplateRenderingTest` — if the test moves, this file moves.

## The model: document = view + template + window

- A **view** is a stored GraphQL query (edited in `/graphiql`, saved via the view catalog). It
  decides WHAT data exists. Everything the template can render, the query must release first —
  the template has no independent data access (D3), and every resolver runs in the *caller's*
  read scope (§12).
- A **template** is a stored Mustache body. It decides HOW the released data looks. Logic-less:
  sections (`{{#list}}…{{/list}}`), inverted sections (`{{^flag}}…{{/flag}}`), variables — no
  expressions, no arithmetic, no scripting.
- A **document** pairs the two and pins **`defaultVariables`** (typically the window). One view
  can serve many documents: `kalender_woche` pins a week window + week template,
  `kalender_monat` a month window + month template — same view.
- Rendered at `GET /api/documents/<name>`. The response is sanitized (no scripts; inline `style`
  attributes and `<style>` elements DO survive — the geometry technique depends on them) and
  wrapped in the server shell.

### URL parameters — `@param` and `@window`

The view declares its public URL surface; everything else is a 400:

```graphql
query kalender($filter: ReservationFilter!) @view(title: "Kalender")
  @window(from: {anchor: WEEK_START}, to: {anchor: WEEK_START, offset: 7})
  @param(name: "resource", into: "filter.allocatableIdsIn")
{ … }
```

- `@window` resolves the date window server-side per request and makes `?from=` / `?to=`
  overridable on the document URL.
- `@param(name:, into:, required:)` maps a public URL key onto a private variable path
  (`?resource=…` → `filter.allocatableIdsIn`). `required: true` → a missing param renders the
  same 404 as a nonexistent document.
- **GET filter forms (2026-07-15):** a document may carry a native
  `<form method="get">` submitting to its own URL — a script-free resource picker / date field.
  The fields are ordinary URL params, gated like a typed URL (undeclared key → 400). The
  document CSP allows exactly this shape: `sandbox allow-forms` + `form-action 'self'` — a GET
  to self reads, never writes; Phase 9's write forms are a separate, capability-sealed design.
- **The editor's vars field IS the query string** — same names, same gate, and the preview
  errors on an undeclared key exactly where the live URL 400s. `Live ↗` opens the saved
  document with the field carried over verbatim. In the preview, submitting a GET form (or
  clicking nav) writes its params into the field visibly and re-renders.
- Details: [PRD 074 § Window and inputs directives](prd/074-graphql-declarative-views.md).

### Navigation — the `{{#nav}}` block + `?date=` links (2026-07-15)

Navigation is **template data, not chrome**: every windowed document (view `@window` or a
document-level window) gets a `nav` entry in the model — `prevUrl` / `todayUrl` / `nextUrl` /
`label` — and the template decides whether and where to render it. Script-free plain links, so
the CSP sandbox is untouched.

The shortest form is the **builtin partial** — all builtin templates lead with it:

```html
{{> rapla/nav}}
```

It expands server-side to the canonical block below (`.rapla-nav` is the shell's offered
default styling). Want different markup/placement? Don't reference the partial — write your
own block:

```html
{{#nav}}
<nav class="rapla-nav">
  <a href="{{prevUrl}}" title="Zurück">&#9664;</a>
  <a href="{{todayUrl}}">Heute</a>
  <a href="{{nextUrl}}" title="Weiter">&#9654;</a>
  <span class="rapla-nav-range">{{label}}</span>
</nav>
{{/nav}}
```

Wrap it in `@media print { .rapla-nav { display: none; } }` — nav belongs on screen, not paper
(the shell CSS does this for `.rapla-nav` already).

The mechanism is one URL parameter:

- `?date=YYYY-MM-DD` — "resolve the window **as if today were this day**". `?date=2026-03-04` on
  a week window renders the week of Mo 02.03.; on a `MONTH_START` window, March. Malformed → 400.
- The links are unit-free: **prev** points at the day before the window start (the anchor snaps
  it into the previous period), **next** at the exclusive window end (the first day of the next
  period) — correct for weeks, months and Mo–Fr day-sets alike. **Heute** drops `date`.
- **`?date=` is a caller gesture and OUTRANKS stored defaults** (like `?from/?to`): a document
  with pinned absolute dates in `defaultVariables` shows its pinned range on the bare URL, but a
  nav click navigates away from it — no defaults surgery needed to make a document navigable.
- Declared `@param`s (`?resource=…`) carry through the links; explicit `?from=`/`?to=` do NOT
  (a nav click means "leave the pinned range, go back to the anchored window").
- No `{{#nav}}` in the template = no navigation — author's choice. In the editor **preview**
  the nav WORKS: a click writes the target window into the vars box (visible, hand-editable —
  a nav click IS a variables edit) and re-renders. Mechanism: the sandboxed preview iframe
  cannot navigate, so the preview shell's script postMessages the server-resolved target window
  (the `data-nav-from`/`data-nav-to`/`data-nav-today` attributes — a contract custom nav markup
  can adopt too) to the editor. Author-written absolute `http(s)` links open in a new tab from
  the preview; relative links are inert there — test them via the editor's **Live ↗** link,
  which opens the saved document at its real URL.
- Partials: only the builtin `rapla/*` catalog resolves for now — `rapla/nav` and
  `rapla/print-hint` (the screen-only "Strg+P" hint, hidden when printing; opt-in template
  content — NOT in the default templates, add it to hand-out documents like a Leihschein). A
  dangling `{{> name}}` fails validate-on-save with the line and the list of available partials.

## What the template sees

The Mustache model is the GraphQL `data` tree of the view, plus pipeline entries:

| key | source | contents |
|---|---|---|
| `<root fields>` | the query itself | e.g. `appointmentBlocks` rows, `strips` — exactly the selected fields, aliases included |
| `groups` | `@column(group: true)` | rows bucketed by the marked column: `{{#groups}}{{label}}…{{#rows}}…{{/rows}}{{/groups}}`. For fields with a configured domain (`timeslot`), empty groups render too, in configured order |

**Convention: select the data field FIRST, `strips` after** — columns and grouping derive from
the first root field of the query.

## The geometry primitives (PRD 097 Phase 5)

The server does no layout "engine" work: it serves **numbers**, the template substitutes them
into inline styles, CSS grid does the pixels. All indices are 1-based and CSS-ready.

Which view needs what (the orientation table — decided 2026-07-14):

| view | selects | explicitly does NOT need |
|---|---|---|
| **Tagesliste** (day-sectioned list) | `groups` (via `@column(group: true)`) + plain fields (`times`, `name`, …; `wholeDay` for "ganztägig") | no `strips`, no `segments`, no `bars`, no `banner` — a list has no geometry (the null case: the simple view pays nothing) |
| **Wochenprogramm** (timeslot × day matrix) | `strips` (with `weekdays:` for Mo–Fr) + `band: timeslot @column(group: true)` (server-configured bands → `{{#groups}}`, incl. empty ones; left column = `{{label}}`) + `segments { dayIndex }`; cards stack in query sort order via CSS flow; recommended: `bandBars: bars(scope: BANNER)` as a spanning row so whole-day events (Feiertag) stay visible | no minute geometry (`startMin`/`endMin` unused), no lanes; bands are DATA, never template structure |
| **Monthview** | `strips` + `bars(scope: ALL)` | no `segments`; **no `banner`** — month paints *every* block as a bar, banner or not |
| **Weekview** (minute-proportional time grid) | `strips` + `segments` + `banner` + `bandBars: bars(scope: BANNER)` | — the only view needing everything, because it is the only one with **two regions** (header band + time columns) and therefore the only one that must *route* blocks |

Mental model: `segments` = time-column geometry · `bars` = day-spanning geometry · `timeslot` =
categorical row label (coarse bands, grouped) · `banner` = the router (needed only where a view
has both regions) · `strips` = the frame (any 2D view, never a list).

### Field reference

- **`strips(filter: $filter)`** — the day scaffold. Pass the SAME `$filter` variable as the data
  field; only `from`/`to`/`weekdays` are read. Snaps outward to full Monday-first weeks: 1 strip
  for a week window, ~5 for a month. Week is the degenerate 1-strip month — nothing in the query
  says "week" or "month"; the window decides. Bigger grids come through the window
  (`@window(from: {anchor: MONTH_START}, to: {anchor: MONTH_START, offset: 6, unit: WEEKS})` =
  stable 6-row month).
- **`ReservationFilter.weekdays`** — the rendered day set (`[MONDAY, …, FRIDAY]`). Drops blocks
  on excluded days AND shapes `strips` — one declaration, both consumers.
- **`segments { strip dayIndex startMin endMin lane laneCount clippedStart clippedEnd }`** —
  per covered day, minutes clipped, midnight split, collision lanes over THIS query's result.
- **`bars(scope: ALL|BANNER) { strip startDay span row clippedStart clippedEnd }`** — day-spanning
  bars, chunked at strip edges and day-set gaps, stacked longer-first (`row`).
- **`banner`** — Rule B: whole-day OR a full calendar day inside `[start, end)`. Banner blocks
  emit `segments: []`; paint them via `bandBars: bars(scope: BANNER)` in a header strip.
- **`wholeDay`**, **`timeslot`** — "ganztägig" flag; server-configured band label.

### CSS techniques

- **Number substitution**: `style="grid-column: {{dayIndex}}; grid-row: {{startMin}} / {{endMin}}"`
  with `grid-auto-rows: 1px` (or a row `repeat(1440, …)`) gives minute-proportional blocks.
- **Lane widths**: Mustache cannot divide — `calc()` does:
  `width: calc(100% / {{laneCount}}); margin-left: calc(100% / {{laneCount}} * ({{lane}} - 1))`.
- **Named grid lines** when an axis column offsets the day columns:
  `grid-template-columns: [axis] 48px [d1] 1fr [d2] 1fr …` + `grid-column: d{{dayIndex}}` —
  string concatenation, no arithmetic. ⚠️ `calc()` inside grid-LINE numbers
  (`grid-row: calc({{strip}} * 4)`) is **not portable** — use named lines or separate containers
  per strip instead.

## Copy-paste starters

The four seed shapes (pinned by `CalendarTemplateRenderingTest`):

### Tagesliste — no Phase-5 fields at all

```graphql
query tagesliste($filter: ReservationFilter!) @view(title: "Tagesliste")
  @window(from: {anchor: WEEK_START}, to: {anchor: WEEK_START, offset: 7})
{
  appointmentBlocks(filter: $filter) {
    tag: start @column(group: true, format: "EE dd.MM.")
    times  name
  }
}
```
```mustache
{{#groups}}<h2>{{label}}</h2><ul>{{#rows}}<li><b>{{times}}</b> {{name}}</li>{{/rows}}</ul>{{/groups}}
```

### Unified Kalender view — one body, week AND month templates

```graphql
query kalender($filter: ReservationFilter!) @view(title: "Kalender")
  @window(from: {anchor: WEEK_START}, to: {anchor: WEEK_START, offset: 7})
  @param(name: "resource", into: "filter.allocatableIdsIn")
{
  appointmentBlocks(filter: $filter) {
    name  times  banner
    segments { dayIndex startMin endMin lane laneCount clippedStart clippedEnd }
    bars { strip startDay span row }
    bandBars: bars(scope: BANNER) { strip startDay span row }
  }
  strips(filter: $filter) { index days { index label } }
}
```

Week template (header + banner band + minute columns):

```mustache
<style>
.week { display: grid; grid-template-columns: repeat(7, 1fr); grid-auto-rows: 1px; }
.hdr  { display: grid; grid-template-columns: repeat(7, 1fr); }
.block { overflow: hidden; border-radius: 3px; background: #cde; }
</style>
<div class="hdr">{{#strips}}{{#days}}<div style="grid-column: {{index}}">{{label}}</div>{{/days}}{{/strips}}</div>
<div class="hdr">{{#appointmentBlocks}}{{#bandBars}}<div style="grid-column: {{startDay}} / span {{span}}; grid-row: {{row}}">{{name}}</div>{{/bandBars}}{{/appointmentBlocks}}</div>
<div class="week">
  {{#appointmentBlocks}}{{#segments}}
  <div class="block" style="grid-column: {{dayIndex}}; grid-row: {{startMin}} / {{endMin}};
      width: calc(100% / {{laneCount}}); margin-left: calc(100% / {{laneCount}} * ({{lane}} - 1))">
    {{^clippedStart}}<b>{{times}}</b>{{/clippedStart}} {{name}}</div>
  {{/segments}}{{/appointmentBlocks}}
</div>
```

Month template (same view, different document — window pinned to a month):

```mustache
{{#strips}}<div class="weekrow">{{#days}}<div class="daycell" style="grid-column: {{index}}">{{label}}</div>{{/days}}</div>{{/strips}}
{{#appointmentBlocks}}{{#bars}}
<div class="bar" data-strip="{{strip}}" style="grid-column: {{startDay}} / span {{span}}; grid-row: {{row}}">{{name}}</div>
{{/bars}}{{/appointmentBlocks}}
```

### Wochenprogramm — timeslot × day matrix (bands are data)

```graphql
query wochenprogramm($filter: ReservationFilter!) @view(title: "Wochenprogramm")
  @window(from: {anchor: WEEK_START}, to: {anchor: WEEK_START, offset: 7})
{
  appointmentBlocks(filter: $filter) {
    name
    band: timeslot @column(group: true)
    segments { dayIndex }
  }
  strips(filter: $filter) { days { index label } }
}
```
```mustache
<div class="kopf">{{#strips}}{{#days}}<span>{{label}}</span>{{/days}}{{/strips}}</div>
{{#groups}}<section class="band"><h3>{{label}}</h3>
<div style="display: grid; grid-template-columns: repeat(5, 1fr)">
  {{#rows}}{{#segments}}<div class="card" style="grid-column: {{dayIndex}}">{{name}}</div>{{/segments}}{{/rows}}
</div></section>{{/groups}}
```

Pin Mo–Fr via the document's `defaultVariables`:
`{"filter":{…,"weekdays":["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY"]}}`. The bands come
from the server timeslot configuration; configured-but-empty bands still render as empty
sections.

## Unified or split views — the author decides

Both shapes work on the same GraphQL: ONE view selecting the union (documents pick shape via
template + pinned window, the SPA via `renderModes`) — or SPLIT sibling views with the same body
and trimmed selections (one selects `segments`, the other `bars`). Geometry is computed lazily
per selection, so a view pays only for what it selects; switching shapes is a pure authoring act
(copy view, trim selection, repoint document). The platform's only hard opinions are the security
ones (§12, D3, the `@param` gate).
