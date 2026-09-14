# Equipment planning / lending desk (archetype C)

Findings from analysing a real **equipment-lending deployment** (a university media-support
unit). This documents a **third deployment archetype** beside A (university timetabling) and
B (weekly seminar program) from [README.md](README.md): **the lending desk** — booking
*portable equipment* to *external borrowers* for *multi-day loan periods*.

> Fully anonymised per AGENTS.md §17: no real persons, no concrete figures, dates, or
> deployment identifiers. Borrowers appear as *Borrower A/B/…*. The source dataset contains
> real borrower PII — none of it is reproduced here.

---

## Deployment shape

- **Many small equipment types, one loan event type.** A dozen-plus resource types, each a
  kind of portable equipment — cameras, tripods, audio recorders, microphones, projection
  gear, speakers, lighting, laptops/tablets, video-conference kits, and a large
  cables-and-adapters pool (the single biggest type by far). Plus one **person type** for
  borrowers and a single event type ("Ausleihe" / loan) with just a name attribute.
- **A few hundred bookable items overall**; the fleet is *unit-granular*: the same model
  bought N times becomes N individually bookable resources, named model + unit counter
  ("Camera model X *III*", "Tripod type Y *04*").
- **Very few rapla users** (a couple of admin accounts) — and *every* loan in the dataset
  is owned by **one** operator account.
- **No periods, no repeating rules** — time is a flat continuum, not a term structure.

## The domain model they built

- **Persons = borrowers, not staff.** The person type carries surname, first name, email,
  phone, department and a student-id-like number — a **borrower registry** living inside
  the resource model. Borrowers are **not rapla users**; they never log in.
- **A loan = one reservation** named after the borrower ("Borrower A"), with a **single
  appointment spanning the whole loan period** — from hours up to multiple weeks. No
  repeating rules at all.
- **Allocations = borrower + items**: the person resource *plus* a small number of
  equipment items are allocated to the same loan event. Booking the borrower makes their
  concurrent loans visible as "conflicts" on the person.
- **Alphabetical grouping categories** (surname buckets, a storage location, and a cable
  subtree with per-cable-kind subcategories) drive a `categorization` attribute on persons
  and accessories — a hand-built browse/tree substitute.
- **Serial / inventory numbers** are attributes on some types — asset-management data
  creeping into the scheduler.

## Workarounds observed (= capability gaps)

1. **The "RETURNED" pseudo-resource.** A dedicated type (instances named "ZURÜCK", colors
   `rapla:disabled`) exists only so staff can allocate a "returned" marker onto a loan and
   *see return status on the calendar*. → **Check-out / check-in state is not a rapla
   concept**; the deployment fakes a loan lifecycle with a bookable dummy resource. (In the
   snapshot the markers are not yet allocated to any loan — the workaround is staged, not
   yet in routine use.)
2. **Attribute-schema churn.** Nearly every type carries several **unnamed placeholder
   attributes** ("Attribut", empty defaults) plus typo'd attribute keys — the schema was
   grown interactively in the type editor and spare slots were pre-provisioned because
   adding/renaming attributes later feels costly.
3. **Type proliferation instead of one "equipment" type + category.** Many near-identical
   types (name/description/features/…) exist mainly to get **per-type calendar grouping and
   filtering**. The cable category subtree shows the same need solved the *other* way
   within one type. → grouping-by-attribute is not first-class enough.
4. **Dangling allocations.** Some allocation refs point to since-deleted resources (loans
   survive their items' deletion). Harmless to the engine, but the loan record silently
   loses "what was lent".

## Use cases (UC-C*)

Actors: the **Desk operator** (media-centre staff; the only writer — all loans are
staff-mediated, there is **no self-service**) and the **Borrower** (walk-in/email requester,
never touches rapla).

| # | Use case | Surface today | Notes |
|---|---|---|---|
| UC-C1 | **Take a loan request** — pick borrower (or register new), pick items, set loan period | Swing: new loan event, allocate person + items | The core write. Event name = borrower name (manual convention duplicating the person allocation) |
| UC-C2 | **Find an available item of kind X for period T** — "a camera from Mon to Fri" | manual: open the type's calendar, scan for gaps | **The [PRD 092](../prd/092-free-slot-search.md) free-slot search, verbatim** — same shape as UC-3 (free room), over equipment types + multi-day windows |
| UC-C3 | **Return an item** — record the loan came back | the "ZURÜCK" pseudo-resource hack (gap #1) | No loan lifecycle (out ▸ returned ▸ overdue) exists |
| UC-C4 | **Overdue / what's-out overview** — what is out right now, what's late | none — read the calendar | Wants a *table lens over active loans*, not a week grid |
| UC-C5 | **Borrower lookup** — history + contact of a borrower | person calendar / search | Person-as-resource makes this fall out of the anchor model |
| UC-C6 | **Maintain inventory** — add the Nth unit of a model, serials, groupings | type/resource editors | Source of gap #2/#3 churn |

## Implications for the SPA / PRD design

- **The week view is *not* central here** — the first archetype where it isn't. Loans span
  weeks; the natural lenses are **item-availability-over-a-date-range** and a **table of
  active loans**. Confirms README implication #1: no hardcoded central surface — this
  deployment's landing view is a *table/month*, same render-mode-agnostic host.
- **[PRD 092](../prd/092-free-slot-search.md) (free-slot search) gets a second, stronger customer.** "Free camera Mon–Fri"
  is UC-3 with equipment instead of rooms and *multi-day* windows — the gap-enumeration
  design must not assume within-day slots.
- **Anchor model works unchanged:** anchor = an equipment *type* (all cameras) or a
  *borrower* — both are ordinary allocatable selections (`typeKeyIn` / single person).
  The alphabetical category buckets are exactly the "derived group" flavour from the
  README selection model.
- **Person-allocation is load-bearing beyond timetabling** — here the person *is the
  counterparty*, and their PII (email, phone, student id) lives in classification
  attributes. §12 read-scoping and export stripping must treat person attributes as
  sensitive by default; this dataset would leak borrower PII through any unscoped
  person-attribute exposure.
- **Loan lifecycle is a real product gap.** The ZURÜCK hack and the missing overdue view
  say: a minimal **status on the reservation** (planned ▸ out ▸ returned; the
  `planningstatus` plugin is enabled and points the same direction) plus a status-filterable
  table would replace the pseudo-resource entirely. Now specified as
  [PRD 093](../prd/093-loan-lifecycle.md); do **not** design
  the SPA event sheet in a way that assumes status ∉ reservation.
- **Single-writer deployments exist.** One operator owns every event; conflict *warnings*
  matter (double-lending an item), multi-user coordination doesn't. All types grant
  `allocate_conflicts` — conflicts are advisory, booking through them is normal.
