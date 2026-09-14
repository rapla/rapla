# U5 feature shots — demo data added on top of data-plain.xml

Server: worktree `rapla-demo-features`, port 8052, `-Duser.language=de`, data file `data/data-plain.xml` (local copy).

## Added (GraphQL, admin)

Resources (type `resource`): Lecture hall 1, Seminar room 2, Computer lab, Projector A, Camera A.
Persons (type `person`): Prof. Lehmann (lehmann@example.org), Dr. Vogel (vogel@example.org), Alice Baker (alice.baker@example.org).
Removed: resource `test` that came with data-plain.xml.

Events (type `event`, week 2026-09-14):

| Event | Appointments | Resources |
|---|---|---|
| Algorithms lecture | Mo 14.09. + Mi 16.09. 08:30–10:00 | Lecture hall 1, Prof. Lehmann, Projector A |
| Databases lab | Di 15.09. 10:15–12:30, Do 17.09. 13:00–15:15 | Computer lab, Dr. Vogel |
| Mathematics II | Di 15.09. + Fr 18.09. 08:30–10:00 | Lecture hall 1, Dr. Vogel |
| Team meeting | Mo 14.09. 13:00–14:00 | Seminar room 2, Alice Baker, Prof. Lehmann |
| Video workshop | Mi 16.09. 13:00–16:30 | Seminar room 2, Camera A, Alice Baker |
| Exam preparation | Do 17.09. 09:00–11:30 | Seminar room 2, Prof. Lehmann |
| Research seminar | Fr 18.09. 13:00–15:00 | Lecture hall 1, Dr. Vogel, Alice Baker, Projector A |
| Guest talk | Mo 14.09. 09:00–10:00 | Lecture hall 1 (deliberate conflict with Algorithms lecture) |
| Staff meeting | Mi 16.09. 08:30–09:30 | Seminar room 2 (conflict candidate for the resource picker shot) |

Stored view `week` (public): copy of builtin `rapla_kalender` with `renderModes: [week, day, table]` and the hidden drag-gate fields (`reservation { id canModify appointmentCount }`, `appointment { id repeating { type } }`, `isException`). Builtin views on master offer no week grid.

API keys for admin: `Calendar sync` and `Reporting script` (365 days, shown in the API-keys dialog). The Swing auto-login keys `demo-swing` and `demo-swing-cli2` were deleted again after use.

## Images

| File | Visible |
|---|---|
| week-view.png | SPA view "Week", 4 scope chips, 10 events, "Video workshop" mid-drag to Do 11:30–15:00 (preview box) |
| week-view-swing.png | Swing week KW 38, resource tree, same events |
| event-editor.png | SPA event sheet "Algorithms lecture", resources in edit mode: Lecture hall 1 "belegt an ①", others "frei", "gilt für" per resource |
| resource-picker-availability.png | SPA event sheet "Algorithms lecture" → Ressourcen → "+ Ressource…", search "a": hits with live availability — Camera A, Alice Baker, Computer lab "frei", Seminar room 2 "belegt an ②"; "→ Zuordnen" per hit |
| status-page.png | /server status page (version, build date, Java version) |
| api-keys.png | SPA user menu → Account settings → Manage API keys: dialog with keys "Calendar sync" and "Reporting script" (read scope, created, expiry, key suffix, delete), "New key" |
| template-editor.png | /template-editor/: builtin document "wochenplan" on stored view "week", URL params `resource=<Lecture hall 1>&date=2026-09-14`; data tree left, Mustache source middle, resolved variables below, live preview KW 38 right |
| login.png | /login in German (`?lang=de`): language picker, username/password, "Angemeldet bleiben". No external IdP buttons — data-plain/dev config has no IdP, so the "— oder —" separator stands alone; an IdP login picker needs a server config with Keycloak/Google/Entra |
| i18n-switch.png | /login in English (browser locale en-US) — pair with login.png: switching the picker to "Deutsch" reloads as `?lang=de`; picker lists cs, de, en, es, fi, fr, nl, pl, pt |
| login-providers.png | /login in English with buttons "Sign in with Microsoft", "Sign in with Google", "Sign in with Keycloak" above the local username/password login. Rendered live, unedited. Server started with an extra scratch yml outside the repo (`rapla.oauth.external.{microsoft,google,keycloak}.enabled=true`, placeholder tenant all-zero GUID, client-id `demo`, Keycloak base-url `https://login.example.org` realm `demo`, no secrets); the providers cannot actually log anyone in |
| graphiql.png | /graphiql: docs explorer on Query → appointmentBlocks (description, type, arguments), prettified WeekOverview query, JSON result with this week's blocks |

## Observations

- SPA drop saves the move immediately (snackbar "verschoben · Rückgängig"), no review dialog.
- Swing under WSLg: window capture works (`import -window <id>`), but xdotool mouse and keyboard input does not reach the Java window, so Swing dialogs cannot be opened from the agent side.
