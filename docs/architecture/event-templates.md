# Event templates (Vorlagen)

How rapla's **event templates** work — the storage model, the permission rules, the Swing
instantiation semantics, and the SPA "Neu" picker built on top
([PRD 104](../prd/104-spa-template-picker.md), [PRD 099 § D6](../prd/099-reservation-prototype-prefill.md#decisions-locked)).

> **Naming trap:** this page is about *Vorlagen* — reusable event blueprints a user
> instantiates from the "Neu" flow. The other "templates" in rapla are the **Mustache
> document templates** of the server-rendered document system ([PRD 097](../prd/097-event-html-templates-mustache.md),
> [templates.md](../templates.md)). The two systems share nothing but the word.

## Storage model

- A template is an **Allocatable of the rapla-internal DynamicType `rapla:template`**
  (`StorageOperator.RAPLA_TEMPLATE`). Its classification carries `name` and
  `fixedtimeandduration` (a Boolean — see instantiation semantics below).
- The template's content is **1..n ordinary Reservations** linked to it via the entity
  annotation `RaplaObjectAnnotations.KEY_TEMPLATE` (= `"template"`) holding the template
  allocatable's id. Multi-reservation templates are normal (the dhbw "_Semestervorlage …"
  entries hold whole semester weeks).
- Deployments reach ~1500 templates; Swing renders them as a balanced alphabetic menu tree
  (`BalancedHierarchicalMenu`, rapla-core, max 25/node, range labels like "Aa … Ab – Ak").

## Permissions

`PermissionController` **delegates template-reservation permissions to the template**
(`PermissionController.java` ≈ 608–630):

- `canRead(reservation)` → `canRead(template)` when the reservation carries the template
  annotation; `canModify(reservation)` → `canModify(template)` likewise.
- A freshly created template has **all default permissions removed** (Swing
  `TemplateEdit.createTemplate`) — owner/admin-only. A *shared* template carries an explicit
  permission row (e.g. blanket READ).
- §12 consequence: any API listing templates or template reservations filters by
  `canRead(template, caller)` at the output boundary; an unreadable template answers exactly
  like a nonexistent one.

## Instantiation semantics (Swing = the spec)

Entry: `EditTaskPresenter` `CREATE_RESERVATION_FROM_TEMPLATE` →
`RaplaFacade.copyReservations(reservations, beginn, keepTime, user)` (`FacadeImpl.copy`).

- **Target cascade** (`RaplaComponent.getMarkedInterval`): marked/dragged calendar interval >
  the calendar's selected date (at the configured worktime start) > today.
- **`keepTime`** = the template's `fixedtimeandduration` attribute (default true). It governs
  ONLY the time-of-day handling and the relative placement to the FIRST reservation:
  - `true` — per-appointment **day** offset to the first reservation is kept; original
    times-of-day are kept. (Days move, clocks don't.)
  - `false` — one minute-exact `Duration` shift for all appointments; times follow the
    destination (relevant when the user dragged a concrete slot).
  - Identical for a single day-aligned reservation — the switch only matters for
    **multi-reservation templates** and minute-exact placement.
- **Series** shift along: repeating exceptions and open repeating ends are re-anchored by day
  distance, so a shifted series keeps its shape and length.
- Everything is a **copy with fresh ids**; nothing persists until the editor saves.

## SPA flow (PRD 104, 2026-07-24)

- `newEventOptions` (GraphQL) returns the caller's "Neu" options: `eventTypes` (RESERVATION
  types with `canCreate`, empty when the **defaultwizard** plugin is disabled — dhbw runs
  this way) and `templates` (§12-filtered, name-sorted, each with a server-computed grouping
  `path` — token-prefix clustering with alphabetic range fallback, `TemplatePathBuilder`;
  currently not consumed by the UI, kept for grouped rendering later).
- ONE "Neu" button → **unified picker dialog** (`NewEventPickerComponent`): types pinned on
  top, templates below, kind markers, one search over both, 6 mixed recents
  (per-user localStorage). Skip only for exactly 1 type + 0 templates. Week/month
  **drag-create opens the same picker** when >1 option exists; a template pick lands on the
  dragged slot.
- Instantiation: `reservationsFromTemplate(templateId)` returns the template's reservations
  as plain `Reservation`s (§12: empty when unknown/unreadable); the SPA loads the FIRST one
  through the event sheet's `reservation(id:)` path and builds a fresh draft
  (`draftFromTemplate`: re-keyed ids, remapped allocation restrictions, `persisted=false`,
  uniform date shift per the Swing cascade). Multi-reservation instantiation and the
  `fixedtimeandduration` distinction are **provided for, not built** (PRD 104 D9).
