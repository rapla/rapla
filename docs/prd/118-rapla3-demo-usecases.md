# PRD 118 — Rapla 3 demo: four use-case data sets, screenshots, demo site and demo instance

**Status:** in-progress 2026-09-15 — demo live at https://demo.rapla.org with the hardened build (allowlist from configuration, stale-cookie fix, remember-choice cookie, Switch to user on), PRD 119 phases 1–3b (one search field, picker with chips + group tree, display caps, Termine/week defaults) and the U1 seed v6 (stored views Weekly overview + Equipment loans, loan form, hidden kind attribute); four use-case data sets + SPA screenshots + feature shots merged (docs/demo/); site pages live on rapla.org; open: Swing shots by the user, deep links D8-4…D8-7/D8-10, PRD 119 into the demo (Wed 2026-09-16).
**Related:** PRD 104 (template picker, shown in every use case), PRD 097 / 111 (document templates, loaning), PRD 093 (loaning), PRD 090 / 113 (permissions), PRD 074 (declarative GraphQL views), PRD 034 + `rapla/rapla-releases` (docker image the demo instance runs), `rapla/site` (the public website that consumes the screenshots).

## Abstract

Rapla 3 has no public demo. This PRD collects four self-contained use cases, each backed by its own `docs/demo/<usecase>/demo-<usecase>.xml`, a scripted screenshot list, and a short story for the website. The end state is: one server start per data file shows the scenario in the SPA (and Swing), Playwright produces the screenshots reproducibly, the website in `rapla/site` uses them, and a public demo instance runs one of the data sets with a nightly reset.

## Goal

- `docs/demo/<usecase>/demo-<usecase>.xml` for hochschule, ausleihe, seminarhaus, einsatzplan (not `data/`, which is gitignored — ruled 2026-09-15) each start the server (`-Drapla.file-datasources.raplafile=...`) without errors and contain only dummy data (AGENTS.md § 17).
- One screenshot script per use case (Playwright, `rapla-angular/tests/demo/`) writes the listed images to `docs/demo/<usecase>/` deterministically (fixed "today", fixed window size, fixed language).
- `rapla/site` has one page per use case built from those images and the stories below.
- A demo instance (docker image from `rapla/rapla-releases`) serves one data set with read-only guest login and a nightly data reset.

## Use-case catalogue (locked 2026-09-14)

Each use case names the domain, the resource types, the personas, the features it is meant to show, and the scenes to screenshot. Dates are relative to a fixed demo "today" so that the calendar is populated whatever the real date is (see § Implementation, D2).

### U1 Hochschule / Schule — timetable

- **Domain:** a small faculty with two courses of study, three semesters each. Also covers a school (classes instead of courses).
- **Resource types:** room / lecture hall (attributes seats, beamer, building), lecturer (person), course (intake-year group, person-like), and **loanable equipment as a full resource type** (user ruling 2026-09-14: the demo instance runs this file, so it must also show lending — mobile beamers, cameras, bicycles, laptop trolley; attributes inventory number, condition, storage location, loanable-by group). Equipment loans appear as events alongside lectures (a lecturer borrows a beamer for a lecture, a student group borrows bikes for an excursion).
- **Personas:** timetable admin, lecturer (edits only own events), student (read-only calendar of a course), group admin for one course of study (PRD 113).
- **Features shown:** week view per course and per room, conflict detection (double-booked room and lecturer), repeating appointments over a period with exceptions (holiday week), periods, event templates ("lecture", "exam"), print of a week plan, iCal subscription URL for a course, permissions (lecturer sees own, admin sees all).
- **Requests (user 2026-09-14):** room requests by lecturers (allocation request on a lecture hall, pending/approved/declined) and equipment loan requests by students, approved by desk staff; pending requests in the demo week so the admin has something to approve. Scenes 09-equipment-loans and 10-requests added.
- **Scenes:** (1) week view course A, (2) week view room with conflict marker, (3) event edit dialog with repeating rule and exceptions, (4) template picker "Neu", (5) month view, (6) student read-only calendar on a phone width, (7) iCal export dialog, (8) Swing client same week (the "still there" shot).

### U2 Ausleihe — equipment loans

- **Domain:** a media centre lending cameras, microphones, projectors, and a van.
- **Resource types:** loanable item (attributes: inventory number, condition, storage location), person (borrower), loan form document template.
- **Personas:** desk staff, borrower (requests a loan for a period), admin.
- **Features shown:** availability view over several weeks, allocation with request/approval, loan document generated from a template (PRD 097 / 111, the Leihschein pattern generalized), resource table with attributes and filter, conflict when an item is requested twice, reminders via mail template (shown as document, not sent).
- **Scenes:** (1) resource table filtered by "camera", (2) availability grid over four weeks, (3) new loan via template picker, (4) generated loan form, (5) conflict dialog, (6) borrower's own loans list.

### U3 Seminarhaus — course centre

- **Domain:** a seminar house with seminar rooms, guest rooms, a kitchen team, and external trainers; multi-day events.
- **Resource types:** seminar room, guest room block (capacity), trainer (person), catering slot, category tree for event kinds (yoga, cooking, leadership).
- **Personas:** house manager, trainer (external, sees own events), kitchen (sees catering slots only).
- **Features shown:** multi-day events across the month grid (PRD 095), categories as attribute with tree picker, per-role calendars driven by permissions, declarative GraphQL views (PRD 074, e.g. "arrivals this week"), Exchange/iCal sync of a trainer calendar (documented, not executed in the demo), colour by category.
- **Scenes:** (1) month grid with multi-day blocks coloured by category, (2) event dialog with category tree, (3) kitchen view (catering only), (4) GraphQL view "arrivals this week" rendered as table, (5) trainer's own calendar, (6) programme Oct–Mar as grouped table by month (`06-programme.png`; ruled 2026-09-14, no yearly view exists).

### U4 Einsatzplan — shift/duty roster

- **Domain (user 2026-09-14):** a hospital with two wards, staff in teams A–D per ward, four 6-hour shifts a day (00–06 / 06–12 / 12–18 / 18–24) as rotating repeating reservations; qualifications as attributes.
- **Resource types:** person (attributes: qualification, phone as dummy), ward (location), shift template events.
- **Personas:** planner, team member (sees own shifts, may swap), read-only display for the station screen.
- **Features shown:** repeating shift patterns (early/late/night), copy/move of blocks (PRD 101), swapping people between shifts, person-centred week view ("who is on"), conflict when a person is on two shifts, table export (CSV) for payroll, station screen view (large, read-only, auto-refresh).
- **Scenes:** (1) week view by person, (2) week view by ward (`02-week-by-ward`), (3) drag-move of a shift with the review dialog, (4) conflict marker, (5) table view with export, (6) station screen full-width.

### U5 Features page — cross-cutting (SPA + Swing)

Not a scenario: the website's feature page needs current screenshots per feature, and the Swing ones are years old. Taken against whichever use-case file fits best (noted per feature). Minimum list: dynamic types editor (attribute types, categories, constraints; Swing + SPA admin), permission editor on a resource type and on a resource (PRD 090/113), user and group administration, period editor, template editor (PRD 104), document templates (PRD 097/111), GraphQL views editor + GraphiQL (PRD 074), API keys dialog, login picker with external IdP, i18n switch, Docker/compose start (terminal shot). Output `docs/demo/features/<feature>[-swing].png`. rapla.org (Hugo, live 2026-09-14) already holds seven placeholders on the Rapla 3 pages that this set must fill first: week view with drag & drop, event editor with availability, GraphiQL editor with schema docs, account settings / API keys, Exchange sync, template editor with live preview, status page; plus a dynamic-types image for the features page.

## Implementation

**D1 — one XML per use case, plain start.** Each file starts from `data/data-plain.xml` (only the default categories and the admin user). No shared "base" file, no generator framework: the files are hand-written (a session may use a throwaway script to emit repeating events, the script is not kept). Ladder rung 7 is justified because the data must be readable and editable by hand later.

**D2 — fixed demo window, rolled by hand once a year (user ruling 2026-09-14 in rapla-demo).** **All data windows start at the beginning of September 2026 (user 2026-09-15: "Anfang September für alle Termine"), so the demo shows events from launch day on.** U1 covers one year, winter + summer term (2026-09-01 … 2027-09-30; the winter lecture period starts in the first September week, not October), terms as `ext:period`, repeating events with an end date inside the term, conflicts and exams as single appointments. Code facts (demo-schule, read 2026-09-14): breaks and holidays are single-date `rapla:exception/rapla:date` entries only — the `rapla:period` exception form in rapla.rng is silently dropped by `ReservationReader`; the period copy sets every weekly series' end to the destination period's end, so a term period is the lecture period only, with exams and breaks as their own periods; exceptions move by a fixed day count (only with "include single appointments"), so moving holidays (Ascension, Whit Monday) need a manual fix after a copy. Applies to U2–U4 as well; U2–U4 start 2026-09-01 as well and cover at least six months. The screenshot script navigates to a fixed date with ◀/▶ or the datepicker (the server resolves the window from the real date). Rolling forward is NOT a year search-and-replace (that shifts every weekday by 1–2 days) but the Swing period copy (`periodcopy` plugin, `CopyPluginMenu`: weekday-aligned, exceptions shifted, endless repeats skipped), done on a local copy of the seed file, then committed as the new seed — never on the live demo, whose nightly reset would overwrite it.

**D2a — key rule (found by three sessions 2026-09-14/15):** a file based on `data-plain.xml` carries the PRD 058 `graphql-key-migration.applied` marker, so the migration is skipped and every hand-written attribute/category key must already be a GraphQL identifier (`event_kinds`, `charge_nurse`; no hyphens); the `user-groups` subtree is exempt. A bad key aborts the server start.

**D2b — permissions fact (demo-seminar 2026-09-15):** event-type group permissions do not grant reading events — `PermissionController.canReadPrivate` checks the reservation's own permission rows, the type's rows only gate READ_TYPE. So persona-scoped calendars (kitchen sees catering only, trainer sees own seminars) need the group read rows on every event, not on the type.

**D2c — colours (demo-seminar recipe, 2026-09-15):** blocks are coloured per event type by a category attribute: the type carries `<rapla:annotation key="colors">color</rapla:annotation>` in its `doc:annotations`; the category attribute (`rapla:category` with root-category constraint) carries `<doc:annotations><rapla:annotation key="color">true</rapla:annotation></doc:annotations>` after its `doc:name`; every category used as a value carries `<rapla:annotation key="color">#7cb342</rapla:annotation>` directly inside `<rapla:category>` (sub-categories too, parents don't inherit). Without an event colour, blocks fall back to the first readable resource's colour. Every demo file uses this so screenshots aren't grey.

**D2d — favorites/recents:** the panel must not open empty in screenshots or on the public demo; each persona gets favorites (and recents where possible) pre-set in the seed or by the screenshot script before the first shot.

**D3 — dummy data only** (AGENTS.md § 17): invented names (`Prof. Lehmann`, `Dr. Vogel`, `Alice Baker`, …), `*@example.org` mail, phone numbers `+49 555 …`. No DHBW, Siegen, Yoga-Vidya specifics; those patterns are generalized, their real data stays in the private docs.

**D4 — screenshots: SPA primary, Swing too (user 2026-09-14).** SPA shots are scripted: Playwright against the dev server started with the use-case file; fixed viewport 1440×900 (plus 400 px for the phone scene), locale `de` (and `en`, D5), fixed date. Output to `docs/demo/<usecase>/NN-<scene>.png`. Scene file names are the ones in each session's checklist in the status file (the PRD scene list gives the number and topic only). Every use case also gets its key scenes in Swing (the same week/dialog), taken on Windows against the same data file and checked in next to the SPA images as `NN-<scene>-swing.png`.

**D5 — language (user 2026-09-14, widened 2026-09-14 after the U2 ruling "english but use translations if possible").** Names of resources, events, templates and personas are English single strings (`Alice Baker`, `Lecture hall 1`, `Camera A`). Type, attribute and category `doc:name`s carry both `lang="en"` and `lang="de"` — that is rapla's own data-side i18n, so the German UI shows German labels. Screenshots: de only for now (the SPA UI has no English; LOCALE_ID is fixed de-DE and PRD 103 excludes it); an en variant would be `NN-<scene>-en.png` and comes from the coordinator. GraphQL resolves names in the server locale, so a de shot runs the worktree server with `-Duser.language=de`. Document templates have no i18n lookup: body text English, labels pulled through views come out translated.

**D6 — one session per use case, plus `rapla-demo` (D8) and a deploy session (D7); `rapla-site` builds the website in parallel.** Data set + screenshot script + scene check belong together; each session works in its own worktree (§ 7) with its own port, and reports a scene checklist (scene → image path → what is visible). This session keeps the catalogue, merges, and owns the demo instance.

**D7 — demo instance (user 2026-09-14): erdkante server, Apache in front.** Rapla 3 is installed on the erdkante host behind the existing Apache (reverse proxy + TLS), the `rapla/rapla-releases` image or the fat JAR as a service — the deploy session decides and documents it in `docs/demo/deploy.md` (host-specific details stay in the private docs). Chosen data set: U1 (confirmed by the user 2026-09-14). Nightly reset of the data file. The other three sets: daily rotation of the seed file or an app-side switch, decided with the D8 WP list; never a merged file.

**D8 — demo compatibility of the application (user 2026-09-14): `rapla-demo` collects the requirements, `rapla-impl` implements, `rapla-review` reviews.** Requirements named by the user: a daily reset of the data; several use-case data sets selectable on the demo instance (one server per set, or a switch); admin account locked or not; deep links that open the demo as a given user (persona links per use case) or straight into a GraphiQL/declarative view. `rapla-demo` turns these into work packages with acceptance criteria in the status file (e.g. a `demo` profile in `application.yml`, a reset job, a login-as-persona link, a demo banner, mail/Exchange/external IdP disabled); each WP is approved by the coordinator before `rapla-impl` starts, test-first per AGENTS.md § 1. Security-relevant points (what a public write sandbox must not allow) go to the gitignored `docs/security/`.

## Plan

- [ ] Phase 0 — this PRD, catalogue locked, sessions started (2026-09-14).
- [x] Phase 1 — data sets: all four start cleanly, scrubbed of credentials, checklists in the status file (2026-09-15; in worktrees, uncommitted).
- [x] Phase 2 — SPA screenshot scripts + images for U1–U4 and the U5 feature set done 2026-09-15 (worktrees, uncommitted); [ ] Swing shots by the user on Windows (lists in the status file).
- [ ] Phase 3 — website pages in `rapla/site` (a separate session is building the site since 2026-09-14; it consumes `docs/demo/<usecase>/NN-<scene>.png` and the stories in § Use-case catalogue — those paths and scene numbers are the contract, keep them stable).
- [ ] Phase 4 — demo instance: (a) app demo-compatible — D8 minimum D8-1 demo profile, D8-3 fail-closed API allowlist, D8-2 password lock, D8-9 reset proof, D8-8 banner all implemented and review-PASS 2026-09-15 (uncommitted); D8-11 (stale cookie → anonymous) and D8-12 (remember-choice cookie only when ticked) live 2026-09-15; deep links D8-4…D8-7, D8-10 parked; [x] (b) installed on erdkante behind Apache (D7, 2026-09-14) and since 2026-09-15 running the HARDENED build: demo profile active, U1 Hochschule seed, key rotated + nightly fresh key, static start page at /, banner on /login, /server + JNLP + /change-password 404 (coordinator probe 2026-09-15); [x] URL on the website (rapla.org/rapla3/demo.html live).

## Open questions

1. ~~Swing shots~~ — resolved 2026-09-14: SPA primary, Swing key scenes per use case, plus the U5 features set.
2. ~~Demo instance hosting~~ — resolved 2026-09-14: erdkante host behind Apache (D7).
3. **Guest login on the demo instance:** anonymous read (no login) or a visible `demo`/`demo` account that may also create events in a sandbox that is reset nightly? (Proposal: `demo` account that may create events, since editing is the point of a demo.)
4. **U4 station screen:** does a read-only auto-refreshing full-screen view exist in the SPA today, or is that a small feature to add first? To be checked by the U4 session before it lists the scene.

## Definition of done

Each of the four data files starts the server; each screenshot script runs end to end on a fresh checkout and reproduces the images bit-for-bit apart from timestamps; `rapla/site` links four use-case pages; the demo instance answers at its public URL with the U1 data set.
