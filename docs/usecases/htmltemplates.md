# Use Cases — HTML-Dokumentvorlagen (Mustache, [PRD 097](../prd/097-event-html-templates-mustache.md))

Gesammelt 2026-07-10 per Multi-Agent-Recherche über drei Quellen: ein
reales Uni-Deployment (separates Deployment-Repo — Terminal-Plugin,
Prüfungs-Plugin, GraphQL-Playbook), die rapla-Datenquellen/PRDs (092 freeSlots,
093 Loaning, 074/077 Views, 079/080 Aggregation) und ein freies Brainstorming.
Grundlage für [PRD 097](../prd/097-event-html-templates-mustache.md) — insbesondere
OQ1 (Dokument-Modell A vs. B) und die Phasenplanung.

**Legende.** *Tier* = Machbarkeits-Stufe aus [PRD 097](../prd/097-event-html-templates-mustache.md): flach (Listen, Phase 2),
gruppiert 1D (Sektionen pro Tag/Raum/Kurs — `@column(group:true)` existiert, Phase 3),
2D-Raster (Wochen-/Monatsraster, braucht `CalendarLayoutEngine`, Phase 5).
*Auth* = Dokumentklasse aus Phase 8: Session (eingeloggt, freie Parameter,
§12-Scope des Aufrufers) vs. publiziert (opt-in, anonym, Scope des Veröffentlichers,
Parameter gepinnt/validiert). *Mehrere Dokumente pro View* markiert Fälle, in denen
dieselbe Datenquelle mehrere Templates speist — das Kernargument in OQ1.
Eine dritte, **schreibende** Auth-Klasse ergänzt Session/publiziert: die
*gerichtete Capability* — ein per E-Mail an genau einen Empfänger verschickter,
auf ein Objekt und eine Aktion begrenzter, einmalig einlösbarer Link (Token als
alleinige Autorität, kein Login). Sie trägt die interaktiven Formular-Use-Cases
unten (Phase 9 + [PRD 102](../prd/102-browser-credential-hardening.md) D7).

**Zentraler Befund:** Das Uni-Deployment betreibt heute drei handkodierte Java-HTML-Generatoren
(`SteleKursUebersichtPageGenerator{,2,3}` im Terminal-Plugin), einen anonymen
Display-Export über verschlüsselte `?key=`-URLs (Stele-Endpoint — strukturell das
Phase-8-Muster) und ein Prüfungs-Plugin auf `AbstractHTMLCalendarPage`. Die
Navigationskette Stundenplan → Dozent → Raum → Gebäude ist in `TerminalConstants`
(Link-Titel pro Ressourcentyp) und der GraphQL-navigierbaren Raum→Gebäude-Referenz
(`RoomAttributeIds.BUILDING`) bereits vorgezeichnet. Das Mengengerüst (tausende Räume, weit über zehntausend
Personen, tausende Kurse, dutzende Gebäude) macht Parameter-Pinning statt
Dokument-Vervielfältigung zur Skalierungsfrage.

## Übersicht

| Use Case | Tier | Auth | mehrere Dok./View |
|---|---|---|---|
| Leihschein (Ausleih-Dokument zu einer Reservierung) | flach | Session | nein |
| Leihschein (Ausgabe-Beleg beim Checkout) | flach | Session | ja |
| Rückgabeschein / Rückgabebestätigung | flach | Session | ja |
| Mahnliste / Aktive-Ausleihen-Report (overdue) | gruppiert (1D) | Session | ja |
| Leihschein (Appendix A) | flach | Session | ja |
| Rückgabeschein | flach | Session | ja |
| Mahnschreiben für überfällige Rückgaben | flach | Session | ja |
| Buchungs-/Terminbestätigung als Mail-Anhang | flach | Session | ja |
| Elektronisches Raum-Türschild (Belegung heute / jetzt+nächstes) | flach | publiziert (anonym) | ja |
| Stele-Kursübersicht (Foyer-Anzeige: heutige Veranstaltungen) | gruppiert (1D) | publiziert (anonym) | ja |
| Kurs-Stundenplan als Wochenplan/Aushang (pro Kohorte) | 2D-Raster | beides | ja |
| Freie-Räume-Liste (Raumanfrage-Aushang / Selbstlernraum-Finder) | gruppiert (1D) | beides | ja |
| Freie-Räume-Aushang / Verfügbarkeitsdokument | flach | publiziert (anonym) | ja |
| Raum-Tagesplan / Türschild | gruppiert (1D) | publiziert (anonym) | ja |
| Elektronisches Raum-Türschild (ePaper/Tablet) | flach | publiziert (anonym) | ja |
| Flur-/Foyer-Aushang: alle Räume eines Gebäudes heute | gruppiert (1D) | publiziert (anonym) | ja |
| Wochenplan-Aushang pro Kurs (Stundenplan) | 2D-Raster | beides | ja |
| Prüfungsplan-Aushang | gruppiert (1D) | beides | nein |
| Digital-Signage-Rotation im Foyer | gruppiert (1D) | publiziert (anonym) | ja |
| Erste-Woche-Willkommensplan für Erstsemester | gruppiert (1D) | publiziert (anonym) | ja |
| Ausfall- und Änderungsaushang (heute geändert) | gruppiert (1D) | publiziert (anonym) | nein |
| Dozenten-Wochenplan | 2D-Raster | Session | nein |
| Raum-Wochenbelegungsplan (Ablösung der /rapla/calendar-HTML-Exporte) | 2D-Raster | beides | ja |
| Publizierter Wochen-/Monats-Stundenplan (Ablösung /rapla/calendar) | 2D-Raster | publiziert (anonym) | ja |
| Persönlicher Stundenplan (mein Plan) | gruppiert (1D) | Session | nein |
| Raumwochenplan (einzelner Raum, Wochenraster) | 2D-Raster | beides | ja |
| Gebäude-Überblick (alle Räume eines Gebäudes, jetzt/heute) | gruppiert (1D) | publiziert (anonym) | ja |
| Prüfungsübersicht (Semester-/Kursprüfungsplan) | gruppiert (1D) | beides | ja |
| Raumauslastungs-Report (Standortadmin) | gruppiert (1D) | Session | nein |
| Terminliste / Tages-Agenda (Termine pro Tag) | gruppiert (1D) | beides | ja |
| Belegungsstatistik / Auslastungsreport pro Raum | gruppiert (1D) | Session | ja |
| Inventar-/Ressourcenverzeichnis (Räume je Gebäude, Plätze je Gebäude) | gruppiert (1D) | Session | ja |
| Konfliktreport zu einer Veranstaltung | flach | Session | nein |
| Semesterübersicht (Termine je Periode/Semester) | gruppiert (1D) | beides | ja |
| Raumsuche-Ergebnis als Druckliste (freie Slots) | flach | Session | nein |
| Semesterplan pro Kurs (Terminliste des ganzen Semesters) | gruppiert (1D) | beides | ja |
| Evakuierungs-/Belegungsliste für den Brandschutz | gruppiert (1D) | Session | ja |
| Facility-/Hausmeister-Tagesliste (Rüstliste) | gruppiert (1D) | Session | nein |
| Dozenten-Semesterübersicht / Deputatsliste | gruppiert (1D) | Session | ja |
| CSV-Export-Ablösung (/rapla/calendar.csv) | flach | publiziert (anonym) | ja |
| Raumanfrage-Genehmigung per E-Mail (Accept/Deny-Formular) | flach | gerichtete Capability | ja |
| Slot-Auswahl durch Dozent (1-aus-N angebotene Termine) | flach | gerichtete Capability | ja |

## Ausleihe & Belege ([PRD 093](../prd/093-loan-lifecycle.md))

### Leihschein (Ausleih-Dokument zu einer Reservierung)

Druckbarer Leihschein zu einer Ausleihe-Reservierung: Entleiher, Leihzeitraum, ausgeliehene Geräte/Personen mit Inventarnummer, Unterschriftszeile. Session-Dokument des Ausleihe-Bearbeiters, per window.print als PDF.

- **Tier:** flach · **Auth:** Session · **mehrere Dokumente pro View:** nein
- **Datenquelle:** Einzelne Reservation per ID: classification (Entleiher-Attribut) + appointments.allocatables mit isPerson-Split ([PRD 097](../prd/097-event-html-templates-mustache.md) Appendix A, [PRD 093](../prd/093-loan-lifecycle.md) Loan-Lifecycle / Equipment-Archetyp C)
- **Link-Navigation:** keine (bzw. optional Gerät → Ressourcen-Belegungsplan)
- **Uni-Beleg:** KEIN Befund im Uni-Deployment — grep über ausleih/leih/loan/verleih im Uni-Deployment-Repo leer; kein Geräte-DynamicType (nur 'sonstige'/serviceabteilungen). Beleg kommt aus rapla selbst: [PRD 093](../prd/093-loan-lifecycle.md) (Loaning) + [PRD 097](../prd/097-event-html-templates-mustache.md) Appendix A; für das Uni-Deployment wäre das ein neuer Typ, kein bestehender.

### Leihschein (Ausgabe-Beleg beim Checkout)

Die Ausleihtheke (Archetyp C, docs/usecases/equipment-planning.md) druckt beim Herausgeben eines Geräts einen unterschriftsfähigen Beleg: Entleiher, Leihzeitraum, ausgeliehene Geräte mit Inventarnummer. Der in [PRD 097](../prd/097-event-html-templates-mustache.md) Appendix A komplett durchgearbeitete Referenz-Use-Case.

- **Tier:** flach · **Auth:** Session · **mehrere Dokumente pro View:** ja
- **Datenquelle:** reservation(id: $reservationId) mit classification (Entleiher-Attribut) + appointments { start end allocatables(filter:{isPersonEq}) } — heute vollständig im Schema vorhanden; rollenbasierte Trennung Entleiher/Geliehenes liegt in der Query ([PRD 097](../prd/097-event-html-templates-mustache.md) Appendix A)
- **Link-Navigation:** keine — standalone Druckdokument (window.print → PDF)
- **Uni-Beleg:** nicht das Uni-Deployment — Archetyp-C-Deployment (Ausleihtheke) aus docs/usecases/equipment-planning.md; das Uni-Deployment ist Archetyp A/B (Stundenplanung)

### Rückgabeschein / Rückgabebestätigung

Beim Return an der Theke wird eine Bestätigung gedruckt (was kam zurück, wann, Restposten). Gleiche Datenwurzel wie der Leihschein — nur anderes Template über derselben View; braucht den Loan-Status aus [PRD 093](../prd/093-loan-lifecycle.md), um Rückgabedatum/Status auszuweisen.

- **Tier:** flach · **Auth:** Session · **mehrere Dokumente pro View:** ja
- **Datenquelle:** reservation(id:) heute + Reservation.loanStatus ([PRD 093](../prd/093-loan-lifecycle.md) Phase 1, Status: draft — noch NICHT im Schema); ohne 093 nur als status-loser Beleg möglich
- **Link-Navigation:** keine — Druckdokument; optional Link zurück zum Leihschein desselben Events
- **Uni-Beleg:** nicht das Uni-Deployment (Archetyp C); UC-C3 'Return an item' in equipment-planning.md — heute per ZURÜCK-Pseudoressource gefakt

### Mahnliste / Aktive-Ausleihen-Report (overdue)

Thekenpersonal zieht eine Liste aller offenen Leihen mit abgeleitetem Überfällig-Status (status==out ∧ loanEnd<now), sortiert nach Ende — als druckbares Dokument statt nur SPA-Tabellen-Lens. §12: Entleiher-Details nur bei lesbarer Loan-Reservation.

- **Tier:** gruppiert (1D) · **Auth:** Session · **mehrere Dokumente pro View:** ja
- **Datenquelle:** reservations(filter:) bzw. appointmentBlocks + loanStatus/derived overdue — hängt komplett an [PRD 093](../prd/093-loan-lifecycle.md) (draft, Phase 3 plant die Active-Loans-Table-Lens); overdue wird nie persistiert, nur abgeleitet
- **Link-Navigation:** Mahnlisten-Zeile → Leihschein des Events (reservationId als Parameter)
- **Uni-Beleg:** nicht Uni-Deployment; equipment-planning.md UC-C4/Gap 'missing overdue view — read the calendar'

### Leihschein (Appendix A)

Ausleihe-Beleg für Geräte/Personen einer Loan-Reservierung: Entleiher, Leihzeitraum, Gerätetabelle mit Inventarnummern, Unterschriftszeile. Wird beim Ausleihvorgang gedruckt und unterschrieben.

- **Tier:** flach · **Auth:** Session · **mehrere Dokumente pro View:** ja
- **Datenquelle:** Stored View 'leihschein' — reservation(id:$reservationId) mit classification.entleiher, appointments, allocatables split nach isPersonEq (Appendix A wörtlich)
- **Link-Navigation:** Keine — reines Druckdokument. Höchstens ein interner Verweis auf den Rückgabeschein derselben Reservierung (gleiche reservationId, anderes Dokument).
- **Uni-Beleg:** [PRD 097](../prd/097-event-html-templates-mustache.md) nennt den Leihschein explizit als Phase-2-Zielflow; Equipment-Archetyp C aus [PRD 094](../prd/094-spa-main-view-actions-and-popups.md) ist erstes SPA-Ziel

### Rückgabeschein

Gegenstück zum Leihschein bei der Rückgabe: dieselbe Gerätetabelle plus Zustands-/Vollständigkeits-Checkboxen und Unterschrift des Annehmenden. Belegt, dass die Rückgabe erfolgt ist.

- **Tier:** flach · **Auth:** Session · **mehrere Dokumente pro View:** ja
- **Datenquelle:** EXAKT dieselbe Stored View 'leihschein' (gleiche reservationId) — nur ein anderes Template. Der Paradefall für OQ1-Option A: eine View, mehrere Dokumente.
- **Link-Navigation:** Keine (Druckdokument). Optional Verweis zurück auf den Leihschein.

### Mahnschreiben für überfällige Rückgaben

Briefförmiges Dokument (Anschriftfeld, Betreff, Fristsetzung) an einen Entleiher mit allen überfälligen Leihpositionen — gedruckt oder als Mail-Anhang aus dem Loan-Workflow. Braucht Mustache-Partials für Briefkopf (OQ5).

- **Tier:** flach · **Auth:** Session · **mehrere Dokumente pro View:** ja
- **Datenquelle:** Loans-View ([PRD 093](../prd/093-loan-lifecycle.md) Loan-Lifecycle): überfällige Loans gefiltert nach Entleiher, mit Fälligkeitsdatum und Geräteliste; Mahnstufe/Frist als Request-Variable oder serverseitig berechnet
- **Link-Navigation:** Keine — Brief. Der Sachbearbeiter springt ggf. aus einer Überfälligen-Liste (eigener Use Case) hierher, nicht umgekehrt.

### Buchungs-/Terminbestätigung als Mail-Anhang

Nach Anlage oder Änderung einer Reservierung erhält der Veranstalter eine HTML-Bestätigung (Termin, Raum, Ausstattung, Ansprechpartner) — heute ist die Notification plain text. Gleiche View kann Storno-Bestätigung und Änderungsmitteilung speisen.

- **Tier:** flach · **Auth:** Session · **mehrere Dokumente pro View:** ja
- **Datenquelle:** reservation(id:)-View wie Leihschein-Familie, aber generischer Eventtyp; Rendering serverseitig beim Notification-Versand (kein Browser im Spiel — der Renderer ist derselbe, nur der Konsument ist Mail)
- **Link-Navigation:** Optional ein Link in die SPA auf das Event (Deep-Link, kein Dokument-zu-Dokument-Link). Sonst keine.

## Signage & Aushänge (anonym publiziert)

### Elektronisches Raum-Türschild (Belegung heute / jetzt+nächstes)

Ein Display an der Raumtür zeigt die heutige Belegung des Raums: laufende und nächste Veranstaltung mit Zeitraum, Kurs und Dozent. Betreiber ist der Campus (Stele-Infrastruktur), Betrachter sind Studierende und Besucher ohne Login.

- **Tier:** flach · **Auth:** publiziert (anonym) · **mehrere Dokumente pro View:** ja
- **Datenquelle:** Events/AppointmentBlocks eines Raums für heute (reservations/appointmentBlocks gefiltert auf Raum-Allocatable, Zeitfenster heute), im Scope des Stele-Publikationsusers (heute: ein dedizierter Export-Serviceaccount)
- **Link-Navigation:** Türschild → Raum-Wochenplan; jede Zeile: Kurs → Kurs-Stundenplan, Dozent → Dozentenplan; Kopfzeile: Gebäude → Gebäude-Überblick (TerminalConstants definiert heute schon Link-Titel Belegung/Veranstaltungen/Termine pro Ressourcentyp)
- **Uni-Beleg:** Terminal-Plugin: SteleExportController (dem Stele-Endpoint, anonym via verschlüsseltem ?key=-URL — exakt das Phase-8-Muster), AllocatableExporter, TerminalConstants ROOM_KEY=Raum/LINK_TITEL_RAUM=Belegung, Terminal-Config resource-types enthält Raum/Teilraum; Raum ist ein sehr großer Allocatable-Typ. Ein publiziertes Dokument pro Raum über derselben View (Raum-Parameter gepinnt).

### Stele-Kursübersicht (Foyer-Anzeige: heutige Veranstaltungen)

Digital-Signage-Stele im Foyer listet alle Kurse mit heutigen Veranstaltungen: Kurs, Zeitraum, Veranstaltung, Raum. Heute handkodiertes Java-HTML — der direkteste Ablösungskandidat für ein gespeichertes Mustache-Template.

- **Tier:** gruppiert (1D) · **Auth:** publiziert (anonym) · **mehrere Dokumente pro View:** ja
- **Datenquelle:** Events heute campusweit, gefiltert auf event-types [Pruefung, Lehrveranstaltung], gruppiert nach Kurs; Scope des Stele-Publikationsusers
- **Link-Navigation:** Kurs-Spalte → Kurs-Stundenplan; Raum-Spalte → Raum-Türschild/Raumplan
- **Uni-Beleg:** SteleKursUebersichtPageGenerator{,2,3} (drei Varianten!) malen PrintWriter-HTML mit Spalten Kurs/Zeitraum/Veranstaltung/Raum, Überschrift 'Kurse mit aktuellen Veranstaltungen', konfigurierbarem cssurl (rapla.Uni-Deployment.terminal.*); Admin-Seite /Uni-Deployment/terminal/url für verschlüsselte Export-URLs. Drei Generator-Varianten = drei Dokumente über derselben Datenbasis.

### Kurs-Stundenplan als Wochenplan/Aushang (pro Kohorte)

Wochenraster einer Kohorte (z.B. <Standort>-WIB25A): alle Lehrveranstaltungen mit Dozent und Raum. Als Aushang gedruckt (window.print → PDF) oder als publizierter Link für Studierende, die kein rapla-Login haben.

- **Tier:** 2D-Raster · **Auth:** beides · **mehrere Dokumente pro View:** ja
- **Datenquelle:** AppointmentBlocks der Woche, gefiltert auf Kurs-Allocatable (Playbook Use-Case 5); für den Aushang das 2D-Wochenraster (CalendarLayoutEngine Phase 5), als Agenda-Variante grouped-1d pro Tag schon in Phase 3
- **Link-Navigation:** Blockzelle: Dozent → Dozentenplan, Raum → Raum-Türschild/Raumplan — die vom Nutzer genannte Kette Stundenplan → Dozent → Raum beginnt hier
- **Uni-Beleg:** Kurse + Teilkurse als eigene Allocatable-Typen in großer Zahl; Kurs-Namenskonvention <Standort>-<Programm><Jahrgang> (<Standort>-WIB25A); GraphQL-Playbook Use-Case 5 'Kurs-Stundenplan (Kohorte)' ist die fertige Query; heutige Auslieferung an Studierende läuft über /rapla/calendar-Autoexport + iCal (infrastructure.md Campus-IP-Routing).

### Freie-Räume-Liste (Raumanfrage-Aushang / Selbstlernraum-Finder)

Liste freier Räume für ein Zeitfenster, gefiltert nach Kapazität und Ausstattung, gruppiert nach Gebäude — für das room-only-Nutzerprofil (Raumbucher) und als Display 'freie Selbstlernräume jetzt'.

- **Tier:** gruppiert (1D) · **Auth:** beides · **mehrere Dokumente pro View:** ja
- **Datenquelle:** Playbook Use-Case 2 'Freien Raum finden' (Kandidaten über AnzahlPlaetzeFest + AusstattungListe, dann Belegungsabgleich); sauber erst mit [PRD 092](../prd/092-free-slot-search.md) freeSlots als View-Feld (092 aktuell geparkt) — bis dahin zweistufig
- **Link-Navigation:** Raumzeile → Raum-Türschild und → Raum-Wochenplan
- **Uni-Beleg:** Usecases-Doku des Deployments: room-only ist ein eigenes Nutzerprofil (5 von 12 <Standort>-Usern, ~35–150 sichtbare Allocatables, fast nur Raum); GraphQL-Playbook Use-Case 2 mit verifizierter Kapazitätswahrheit AnzahlPlaetzeFest (Insgesamt/Max sind an MOS/BM null!); Raum-Attribute AusstattungListe, RollstuhlgerechterZugang.

### Freie-Räume-Aushang / Verfügbarkeitsdokument

Aushang oder Anzeige-Display: welche Räume (Kapazität ≥ N, Ausstattung X) sind heute/jetzt frei. Für ein Foyer-Display die natürliche published-anonymous-Anwendung.

- **Tier:** flach · **Auth:** publiziert (anonym) · **mehrere Dokumente pro View:** ja
- **Datenquelle:** Sauber erst mit freeSlots ([PRD 092](../prd/092-free-slot-search.md), draft/geparkt — NICHT im Schema); heutiger Workaround = 2-Schritt: allocatables(filter: Kapazität/Ausstattung) + appointmentBlocks(allocatableIdsIn, Fenster) und Differenz bilden — genau das kann logikloses Mustache NICHT, d.h. dieser Use Case ist bis [PRD 092](../prd/092-free-slot-search.md) blockiert (resourceAvailability ist appointment-granular für den Editor, kein Slot-Enumerator)
- **Link-Navigation:** Raum-Zeile → Raum-Tagesplan (optional)
- **Uni-Beleg:** GraphQL-Playbook 'Schritt A Kandidatenräume / Schritt B welche sind belegt?' dokumentiert den heutigen 2-Schritt-Workaround; Usecases-Doku des Deployments: Room-only-Nutzer (Nutzer 3,4,7,11) sind eine eigene Persona, UC-3 'Find a free room' — Capability-Gap explizit vermerkt ('no direct query today')

### Raum-Tagesplan / Türschild

Pro Raum ein Tages-/Wochenaushang an der Tür oder auf einem Display: heutige Belegungen mit Zeit und Veranstaltungsname. Ein View (Parameter roomId+date), viele publizierte Dokumente mit gepinnter Raum-Variable — der Paradefall für Phase 8 (Variablen gepinnt/validiert, Scope des Veröffentlichers).

- **Tier:** gruppiert (1D) · **Auth:** publiziert (anonym) · **mehrere Dokumente pro View:** ja
- **Datenquelle:** appointmentBlocks(filter:{allocatableIdsIn:[$roomId], window}) — heute vorhanden; als Liste sofort machbar, als Mini-Zeitraster erst mit Phase 5
- **Link-Navigation:** keine — Aushang
- **Uni-Beleg:** kein direkter Beleg; plausible Ableitung aus dem großen Uni-Deployment-Raumbestand (Usecases-Doku des Deployments) und den publizierten Kalender-URLs

### Elektronisches Raum-Türschild (ePaper/Tablet)

Ein an der Raumtür montiertes ePaper-Display oder Tablet pollt eine publizierte URL und zeigt die aktuelle Belegung ('Jetzt: Vorlesung Mathe II, Prof X') plus die nächsten 1-3 Termine. Besucher und Studierende sehen ohne Login, ob und wie lange der Raum belegt ist.

- **Tier:** flach · **Auth:** publiziert (anonym) · **mehrere Dokumente pro View:** ja
- **Datenquelle:** appointmentBlocks-View gefiltert auf eine Ressource (Raum) + Zeitfenster 'heute ab jetzt'; Parameter roomId gepinnt pro publiziertem Dokument, Datum/Uhrzeit serverseitig 'now'
- **Link-Navigation:** Auf ePaper keine (Poll-only). Auf Tablet: QR-Code/Link zum publizierten Raumwochenplan desselben Raums und zur Gebäude-/Flurübersicht. Der QR-Code selbst ist nur eine interpolierte URL-Variable im Template.
- **Uni-Beleg:** Uni-Räume haben heute Papier-Aushänge aus dem /rapla/calendar-Export; ein gepolltes Türschild ist der naheliegende Ersatz

### Flur-/Foyer-Aushang: alle Räume eines Gebäudes heute

Ein DIN-A3-Aushang oder Foyer-Bildschirm listet für ein Gebäude/Stockwerk alle Räume mit ihren heutigen Belegungen — Orientierung für Besucher und Externe ('Wo findet X statt?').

- **Tier:** gruppiert (1D) · **Auth:** publiziert (anonym) · **mehrere Dokumente pro View:** ja
- **Datenquelle:** appointmentBlocks-View gefiltert auf Ressourcengruppe/Gebäude-Attribut, gruppiert per @column(group:true) nach Raum; Parameter buildingId gepinnt
- **Link-Navigation:** Pro Raum-Sektion ein Link/QR zum Türschild bzw. Raumwochenplan dieses Raums — der Aushang ist der natürliche Navigations-Hub eines Gebäudes.

### Wochenplan-Aushang pro Kurs (Stundenplan)

Der Stundenplan eines Kurses/einer Kohorte als Wochenraster — gedruckt fürs Schwarze Brett oder publiziert für Studierende ohne Rapla-Account. Das Kern-Artefakt jeder Hochschulinstallation.

- **Tier:** 2D-Raster · **Auth:** beides · **mehrere Dokumente pro View:** ja
- **Datenquelle:** appointmentBlocks-View gefiltert auf die Kurs-Ressource (Kurs ist bei Uni ein Allocatable-Typ), Wochenfenster; 2D-Positionierung via CalendarLayoutEngine
- **Link-Navigation:** Block → Dozenten-Wochenplan (Klick auf Dozentennamen) und → Raum-Türschild/Raumwochenplan (Klick auf Raumkürzel). Das ist die zentrale Navigationskette Stundenplan → Dozent → Raum aus der Aufgabenstellung.
- **Uni-Beleg:** Die heute massenhaft abonnierten /rapla/calendar?key=…-Kurspläne der Uni sind exakt dieses Dokument; Phase 6 ersetzt deren Renderer unter der eingefrorenen URL

### Prüfungsplan-Aushang

Alle Prüfungen eines Zeitraums/Studiengangs, gruppiert nach Tag: Fach, Uhrzeit, Raum, Aufsicht, zugelassene Hilfsmittel. Hängt in der Prüfungsphase am Schwarzen Brett und wird publiziert für Studierende.

- **Tier:** gruppiert (1D) · **Auth:** beides · **mehrere Dokumente pro View:** nein
- **Datenquelle:** Events-View gefiltert auf DynamicType 'Prüfung' (typeIn — [PRD 059](../prd/done/059-graphql-typed-where-predicates.md)) + Zeitraum, @column(group:true) auf Datum; Hilfsmittel/Aufsicht als Classification-Attribute
- **Link-Navigation:** Raumkürzel → Raum-Türschild/Lageplan (Studierende suchen am Prüfungstag den Raum); optional Aufsicht → Dozenten-Wochenplan (nur in der session-Variante — im publizierten Aushang bewusst ohne Personen-Links).

### Digital-Signage-Rotation im Foyer

Großbildschirm im Eingangsbereich zeigt vollbildschirmfüllend die heutigen Veranstaltungen (öffentliche Events, Gastvorträge, Info-Termine) mit Meta-Refresh/Poll-Rotation. Kiosk-Modus, niemand ist eingeloggt.

- **Tier:** gruppiert (1D) · **Auth:** publiziert (anonym) · **mehrere Dokumente pro View:** ja
- **Datenquelle:** Events-View 'heute, öffentlich markierte Eventtypen' des Veröffentlichers; das Template liefert nur großformatiges HTML + <meta http-equiv=refresh> — Rotation ist reines Template-Feature, kein Server-Feature
- **Link-Navigation:** Keine — Kiosk ohne Eingabegerät. Höchstens ein QR-Code je Event auf eine publizierte Event-Detailseite fürs Handy.

### Erste-Woche-Willkommensplan für Erstsemester

Publiziertes Onboarding-Dokument: die Termine der Einführungswoche eines neuen Kurses, angereichert mit Raum-Wegbeschreibung und Ansprechpartnern — verlinkt aus der Zulassungs-Mail, bevor die Erstsemester Accounts haben.

- **Tier:** gruppiert (1D) · **Auth:** publiziert (anonym) · **mehrere Dokumente pro View:** ja
- **Datenquelle:** Kurs-Belegungs-View (Einführungswoche, kursId gepinnt), gruppiert nach Tag; Wegbeschreibungs-/Kontakttexte als statischer Template-Inhalt oder Classification-Attribute der Räume
- **Link-Navigation:** QR/Link je Raum auf das publizierte Raum-Türschild bzw. den Gebäude-Foyer-Aushang ('finde deinen Raum'); weiter auf den publizierten Kurs-Wochenplan für die Folgewochen. Klassische Kette über ausschließlich publizierte Dokumente — session-Links wären hier tot.

### Ausfall- und Änderungsaushang (heute geändert)

Tagesaktueller Aushang/Monitor-Inhalt: heute entfallene, verlegte oder raumgeänderte Veranstaltungen ('Mathe II fällt aus', 'BWL: heute R 214 statt R 108'). Das Dokument, auf das Studierende morgens als erstes schauen.

- **Tier:** gruppiert (1D) · **Auth:** publiziert (anonym) · **mehrere Dokumente pro View:** nein
- **Datenquelle:** Events-View über heutige Termine mit Änderungs-/Ausfall-Kennzeichnung (Classification-Attribut oder lastChanged>=heute als Where-Prädikat), gruppiert nach Kurs; erfordert ggf. ein whereEvent-Prädikat auf Änderungszeitpunkt ([PRD 059](../prd/done/059-graphql-typed-where-predicates.md)-Mechanik)
- **Link-Navigation:** Kurs → publizierter Kurs-Wochenplan (was gilt stattdessen?), neuer Raum → Türschild. Ohne diese zwei Links ist der Aushang nur halb nützlich.

## Wochen- & Stundenpläne (2D-Raster, Phase 5/6)

### Dozenten-Wochenplan

Persönlicher Wochenplan eines Dozenten über alle Kurse und Standorte: wann, welche Veranstaltung, in welchem Raum. Vom Dozenten selbst oder von Planern (course-planner-Profil) aufgerufen und gedruckt.

- **Tier:** 2D-Raster · **Auth:** Session · **mehrere Dokumente pro View:** nein
- **Datenquelle:** AppointmentBlocks der Woche gefiltert auf Person-Allocatable (Playbook Use-Case 6 'Dozentenplan'); §12 sorgt dafür, dass ein Planer nur seine Programm-Personen sieht
- **Link-Navigation:** Blockzelle: Kurs → Kurs-Stundenplan, Raum → Raumplan/Türschild; Rücklink vom Kurs-Stundenplan hierher (Kette Stundenplan → Dozentenplan)
- **Uni-Beleg:** Person ist der größte Allocatable-Typ; GraphQL-Playbook Use-Case 6; TerminalConstants LINK_TITEL_PERSON='Termine' zeigt, dass Personen-Terminseiten schon im Terminal-Plugin verlinkt werden; Usecases-Doku des Deployments: course-planner sehen jeweils einen begrenzten Ressourcen-Ausschnitt inkl. vieler Personen.

### Raum-Wochenbelegungsplan (Ablösung der /rapla/calendar-HTML-Exporte)

Wochenraster eines Raums als publizierte HTML-Seite bzw. Aushang — genau das, was heute die starren AbstractHTMLCalendarPage-Autoexporte hinter /rapla/calendar liefern. [PRD 097](../prd/097-event-html-templates-mustache.md) Phase 6 tauscht den Renderer hinter den eingefrorenen URLs, Uni-Admins können das Layout danach selbst anpassen.

- **Tier:** 2D-Raster · **Auth:** beides · **mehrere Dokumente pro View:** ja
- **Datenquelle:** AppointmentBlocks der Woche gefiltert auf Raum (Playbook Use-Case 1/1a Wochenplan Campus/Gebäude, Raum+Dozent pro Termin); Phase 5 CalendarLayoutEngine für das Zeitraster
- **Link-Navigation:** Kopf: Gebäude → Gebäude-Überblick; Block: Kurs → Kurs-Stundenplan, Dozent → Dozentenplan — schließt die Kette Raumplan → Gebäude-Überblick
- **Uni-Beleg:** infrastructure.md: Apache-LB routet /rapla/calendar ↔ /rapla/internal_calendar nach Campus-IP (interne vs. externe Sicht) — die HTML-Kalender-Exporte sind produktiv im Einsatz, inkl. iCal-Subscriber im Topologie-Diagramm; RaplaPruefungen importiert AbstractHTMLCalendarPage direkt.

### Publizierter Wochen-/Monats-Stundenplan (Ablösung /rapla/calendar)

Die heute hand-codierten HTML-Export-Seiten (AbstractHTMLCalendarPage → HTMLWeekViewPage/HTMLMonthViewPage/HTMLCompactViewPage) werden hinter den §15-eingefrorenen Routen durch gespeicherte Templates ersetzt — der strategische Hauptzweck (097 Phase 6). Kurs/Studiengang abonniert seinen Plan per URL, ohne Login.

- **Tier:** 2D-Raster · **Auth:** publiziert (anonym) · **mehrere Dokumente pro View:** ja
- **Datenquelle:** appointmentBlocks + color (§12-gated, [PRD 095](../prd/095-month-grid-render-mode.md)) + matchedBy (Lane-Gruppierung, [PRD 100](../prd/100-spa-block-renderer-unification.md) Phase 5) → CalendarLayoutEngine ([PRD 030](../prd/030-server-side-view-rendering.md), resurrect in 097 Phase 5) emittiert das positionierte Modell; Auth-/Publish-Mechanik (AutoExportPlugin.HTML_EXPORT-Flag + URL-Encryption) wird unverändert geerbt
- **Link-Navigation:** Block → Dozenten-/Raum-Dokument wäre der klassische Stundenplan-Drilldown (Stundenplan → Dozent → dessen Plan); heute haben die Export-Seiten keine Links — Mustache-Templates könnten sie erstmals admin-editierbar hinzufügen
- **Uni-Beleg:** Infrastruktur-Doku des Deployments (Apache-Konfiguration) — Apache-RewriteRules für /rapla/calendar und /rapla/internal_calendar: die publizierten HTML-Kalender-Exporte sind in der Produktion des Deployments aktiv in Benutzung (UC-7 'Publish/export a calendar', set-and-forget)

### Persönlicher Stundenplan (mein Plan)

Konsument (Dozent/Student, UC-8 'Read my own schedule') ruft sein eigenes Wochen-/Listen-Dokument ab — heute außerhalb des SPA-Scopes und nur über publizierte Kalender oder iCal abgedeckt; als session-Dokument bekommt jeder Eingeloggte automatisch seinen §12-Ausschnitt ohne pro-Person-Konfiguration.

- **Tier:** gruppiert (1D) · **Auth:** Session · **mehrere Dokumente pro View:** nein
- **Datenquelle:** appointmentBlocks(filter:{allocatableMatching: eigene Person / ownerEq}) — §12-Scope des Aufrufers macht die Personalisierung gratis; als Liste (grouped-1d) sofort, als Wochenraster erst mit Phase 5
- **Link-Navigation:** Termin → Raum-Tagesplan (Wo ist der Raum belegt?) optional
- **Uni-Beleg:** Usecases-Doku des Deployments UC-8-Persona (Consumer, constant) — Personen sind der größte potenzielle Konsumentenkreis; heute über Export2iCalController (/rapla/ical) bzw. publizierte HTML-Kalender bedient

### Raumwochenplan (einzelner Raum, Wochenraster)

Das klassische Wochenraster eines einzelnen Raums — als Druck-Aushang neben der Tür oder als Zielseite des Türschild-QR-Codes. Zeigt alle Belegungen der Woche mit Veranstaltung, Kurs und Dozent.

- **Tier:** 2D-Raster · **Auth:** beides · **mehrere Dokumente pro View:** ja
- **Datenquelle:** Dieselbe Raum-Belegungs-View wie das Türschild (Ressourcenfilter roomId), nur mit Wochen-Zeitfenster; Phase-5-CalendarLayoutEngine liefert das positionierte Modell
- **Link-Navigation:** Klick auf einen Block → Dozenten-Wochenplan des Dozenten bzw. Kurs-Wochenplan des Kurses; Kopfzeile → Gebäudeübersicht. Türschild und Raumwochenplan verlinken wechselseitig.
- **Uni-Beleg:** Entspricht 1:1 dem heutigen AbstractHTMLCalendarPage-Raumexport, den Phase 6 ablösen soll

## Listen, Übersichten & Reports

### Gebäude-Überblick (alle Räume eines Gebäudes, jetzt/heute)

Foyer-Display oder Aushang pro Gebäude: Sektion je Raum mit aktueller/nächster Belegung, plus Gebäude-Stammdaten (Adresse, Öffnungszeiten). Endpunkt der vom Nutzer genannten Navigationskette und Absprungpunkt zurück zu jedem Raum.

- **Tier:** gruppiert (1D) · **Auth:** publiziert (anonym) · **mehrere Dokumente pro View:** ja
- **Datenquelle:** Räume gefiltert über Gebaeude-Referenz bzw. Raumnamen-Präfix (Playbook: nameContains '<Gebäudekürzel>/', Use-Case 1b Aliase, 7b Räume je Gebäude), je Raum die heutigen Events; grouped-1d mit Raum als Gruppenschlüssel
- **Link-Navigation:** Jede Raum-Sektion → Raum-Türschild und → Raum-Wochenplan; Ende der Kette Stundenplan → Dozent → Raum → Gebäude
- **Uni-Beleg:** Gebaeude als eigener Allocatable-Typ; RoomAttributeIds.BUILDING='Gebaeude' (Raum→Gebäude-Referenz, GraphQL-navigierbar: RaumClassification { Gebaeude { displayName } } im Playbook); BuildingAttributeIds: Adresse, Kuerzel, Oeffnungszeiten. Ein publiziertes Dokument pro Gebäude über derselben View.

### Prüfungsübersicht (Semester-/Kursprüfungsplan)

Übersichtsseite der Prüfungstermine eines Kurses/Semesters mit Datum, Raum und Semesterzuordnung — heute eine handkodierte Java-Seite im pruefungen-Plugin, morgen ein View+Template-Dokument, das der Prüfungsplaner selbst pflegt.

- **Tier:** gruppiert (1D) · **Auth:** beides · **mehrere Dokumente pro View:** ja
- **Datenquelle:** Events vom Typ Pruefung, gefiltert auf Kurs/Zeitraum, gruppiert nach Semester bzw. Tag (Playbook Use-Case 4 Prüfungsplanung; Räume mit AnzahlPlaetzePruefung als Kapazitätsattribut)
- **Link-Navigation:** Prüfungszeile: Raum → Raum-Türschild/Raumplan, Kurs → Kurs-Stundenplan
- **Uni-Beleg:** Eigenes Plugin org.rapla.plugin.Uni-Deployment.pruefungen (RaplaPruefungen mit Semester-Datumslogik, baut auf AbstractHTMLCalendarPage auf); DynamicType Pruefung (DynamicTypeKeys.EXAM, ExamAttributeIds); Terminal-Config event-types [Pruefung, Lehrveranstaltung]; Raum-Attribut AnzahlPlaetzePruefung.

### Raumauslastungs-Report (Standortadmin)

Tabellarischer Report der Wochen-Auslastung je Raum (heißeste/kälteste Räume), je Gebäude gruppiert — Planungsgrundlage des Standortadmins, gedruckt oder als Seite. Reiner Listen-Report ohne Zeitraster.

- **Tier:** gruppiert (1D) · **Auth:** Session · **mehrere Dokumente pro View:** nein
- **Datenquelle:** AppointmentBlocks der Woche über alle Räume eines Standorts, aggregiert je Raum (Playbook Use-Case 3 Raumauslastung, 7b Inventar je Gebäude); Aggregation muss der View liefern (compute/Resolver), Mustache malt nur
- **Link-Navigation:** Raumzeile → Raum-Wochenplan; Gebäude-Sektion → Gebäude-Überblick
- **Uni-Beleg:** GraphQL-Playbook Use-Case 3 'Raumauslastung' ('die heißesten/kältesten Räume der Woche') und 7/7b Campus-Raum-Inventar — echte, gegen die Live-DB verifizierte Admin-Fragen des BM-Standortadmins.

### Terminliste / Tages-Agenda (Termine pro Tag)

Planer druckt eine Terminliste eines Zeitfensters, gruppiert nach Tag (oder Ressource), mit Veranstaltung/Zeit/Raum/Dozent-Spalten. Der einfachste produktive Dokumenttyp — jede View, die heute in der SPA als ViewRenderMode.grouped rendert, ist laut 097 OQ3 server-renderbar as-is.

- **Tier:** gruppiert (1D) · **Auth:** beides · **mehrere Dokumente pro View:** ja
- **Datenquelle:** appointmentBlocks(filter: ReservationFilter!) mit @view + @column(group:true); Gruppierung via extensions.view.groupBy → Java-Portierung von groupByColumn/formatGroupLabel (097 OQ3, resolved: thin Java projection). Builtin-View rapla_appointments existiert (renderModes [table, week, month])
- **Link-Navigation:** optional: Terminzeile → Dozenten-Stundenplan / Raum-Tagesplan (Dokument-zu-Dokument über allocatable-Parameter)
- **Uni-Beleg:** GraphQL-Playbook Use-Case 1a 'Raum und Dozent pro Termin getrennt abrufen' — exakt diese Spaltenform, copy-paste-verifiziert gegen die Live-DB

### Belegungsstatistik / Auslastungsreport pro Raum

Campus-Admin erzeugt einen Auslastungsbericht: Stunden (wall-clock oder UE) pro Raum pro ISO-Woche/Monat über ein Semester, optional mit Raumgröße als Spalte ohne Client-Join. Als druckbares Dokument statt GraphiQL-Rohdaten.

- **Tier:** gruppiert (1D) · **Auth:** Session · **mehrere Dokumente pro View:** ja
- **Datenquelle:** appointmentBlockStats(filter, groupBy:[date/allocatables/expr], aggregate:[DURATION_MINUTES×SUM,…]) — [PRD 079](../prd/079-graphql-grouped-aggregates.md) implementiert (v1 2026-06-21) inkl. voller AllocatableFilter auf der Raum-Dimension; StatKey.entity ([PRD 080](../prd/080-typed-entity-stats.md)) macht Raumfelder wie AnzahlPlaetzeFest selektierbar
- **Link-Navigation:** optional: Raum-Zeile → Raum-Wochenplan-Dokument
- **Uni-Beleg:** [PRD 079](../prd/079-graphql-grouped-aggregates.md): 'Auslastung pro Raum, Standort <Standort>' via whereRaum.Gebaeude startsWith MOS — der Uni-Deployment-Fall war die motivierende Query; GraphQL-Playbook verifiziert die Kapazitätspflege via AnzahlPlaetzeFest

### Inventar-/Ressourcenverzeichnis (Räume je Gebäude, Plätze je Gebäude)

Admin druckt ein Ressourcenverzeichnis: Räume pro Gebäude gezählt, Sitzplatzsummen, Ausstattungslisten — Entity-Statistik über die Allocatable-Population statt über Buchungen.

- **Tier:** gruppiert (1D) · **Auth:** Session · **mehrere Dokumente pro View:** ja
- **Datenquelle:** allocatableStats(filter, groupBy, aggregate) ([PRD 080](../prd/080-typed-entity-stats.md) Item 6, implementiert — 'Plätze pro Gebäude') + allocatables(filter:) für die Detailliste; kein Zeitfenster nötig
- **Link-Navigation:** Gebäude-Sektion → Raum-Tagesplan/Türschild-Dokumente der enthaltenen Räume (optional)
- **Uni-Beleg:** GraphQL-Playbook Use-Case 7b 'Inventar-Report: Räume je Gebäude zählen' — als Live-Query gegen die Uni-Deployment-DB dokumentiert

### Konfliktreport zu einer Veranstaltung

Planer druckt vor einer Planungsrunde die Überschneidungen einer Veranstaltung (beide Seiten, betroffene Ressource, Zeitfenster). §12: nur Konflikte, deren beide Seiten + Ressource lesbar sind.

- **Tier:** flach · **Auth:** Session · **mehrere Dokumente pro View:** nein
- **Datenquelle:** conflicts(reservationId: ID!) ([PRD 064](../prd/064-graphql-conflicts-read-api.md), im Schema) — per-Reservation; eine globale 'alle Konflikte im Fenster'-Query existiert NICHT (Lücke für eine echte Konflikt-Sammelliste); potentialConflicts nur für Draft-Preflight
- **Link-Navigation:** Konfliktzeile → Terminlisten-/Detaildokument der Gegenseite (reservationId-Parameter)
- **Uni-Beleg:** GraphQL-Playbook Use-Case 4b 'Kollidiert eine konkrete Buchung?' (2-Schritt: Reservierung per Name finden → conflicts) — inkl. §12-Hinweis, dass ein BM-User MOS-Konflikte nicht sieht

### Semesterübersicht (Termine je Periode/Semester)

Planer druckt die Übersicht eines Studiengangs/Kurses über das ganze Semester: Termine gruppiert nach Woche oder Monat, mit Perioden-Grenzen als Überschriften. Die @view-Fensteranker (fromAnchor/toAnchor/offsets, [PRD 074](../prd/074-graphql-declarative-views.md)) seeden das Default-Fenster.

- **Tier:** gruppiert (1D) · **Auth:** beides · **mehrere Dokumente pro View:** ja
- **Datenquelle:** periods (im Schema) liefert die Semestergrenzen; appointmentBlocks(filter:{window=Periode, allocatableMatching Kurs}) mit @column(group:true) auf ISO-Woche; für Summenzeilen appointmentBlockStats (groupBy ISO_WEEK)
- **Link-Navigation:** Wochen-Sektion → publizierter Wochen-Stundenplan derselben Selektion (Datum-Parameter)
- **Uni-Beleg:** Usecases-Doku des Deployments: Cohort-Planner-Profil (UC-1 'Plan a cohort's week → term', seasonal/intense) — Semesterplanung ist der Uni-Deployment-Kernworkflow

### Raumsuche-Ergebnis als Druckliste (freie Slots)

Sekretariat oder Dozent sucht 'freier Raum, 30 Plätze, Beamer, Do 14-16' und druckt die Trefferliste zur Abstimmung/Weitergabe. Papierform der freeSlots-Suche.

- **Tier:** flach · **Auth:** Session · **mehrere Dokumente pro View:** nein
- **Datenquelle:** freeSlots-View ([PRD 092](../prd/092-free-slot-search.md), geparkt): Zeitfenster + Ressourcenfilter (Kapazität, Ausstattung) → Liste freier Räume/Slots
- **Link-Navigation:** Pro Treffer ein Link auf den Raumwochenplan (Kontext: wie sieht die Umgebung des freien Slots aus?) — im Browser nützlich, im Druck fällt er weg.

### Semesterplan pro Kurs (Terminliste des ganzen Semesters)

Alle Termine eines Kurses über das Semester als kompakte Liste, gruppiert nach Kalenderwoche oder Monat — als PDF-Mail-Anhang zum Semesterstart oder Ausdruck für die Kursmappe. Ergänzt das Wochenraster um die Langfrist-Sicht.

- **Tier:** gruppiert (1D) · **Auth:** beides · **mehrere Dokumente pro View:** ja
- **Datenquelle:** Dieselbe Kurs-Belegungs-View wie der Kurs-Wochenplan, nur Semester-Zeitfenster und Gruppierung nach Woche statt 2D-Raster — zweites Dokument über derselben Quelle
- **Link-Navigation:** Im Browser: Kalenderwochen-Header → Kurs-Wochenplan genau dieser Woche (Parameter-tragender Link — Datumsvariable im Link-Template). Sonst wie Kursplan: Dozent/Raum-Links.

### Evakuierungs-/Belegungsliste für den Brandschutz

Hausmeister/Leitstelle druckt (oder ruft im Notfall ab): welche Räume sind JETZT belegt, mit welcher Veranstaltung und erwarteter Personenzahl (Kursgröße/Kapazität) — Grundlage für Räumungskontrolle und Sammelplatz-Abgleich. Wegen Personenbezug und Missbrauchspotenzial bewusst NICHT anonym publiziert.

- **Tier:** gruppiert (1D) · **Auth:** Session · **mehrere Dokumente pro View:** ja
- **Datenquelle:** appointmentBlocks-View 'alle Räume eines Gebäudes, Zeitpunkt jetzt' mit Kapazitäts-/Teilnehmerzahl-Attributen der Kurs-Ressourcen; §12-Scope eines Facility-Accounts
- **Link-Navigation:** Keine — im Ernstfall zählt nur Papier. (Selbe View wie der Foyer-Aushang, andere Spalten und andere Auth-Klasse — gutes Beispiel dafür, dass die Auth-Klasse am Dokument hängt, nicht an der View.)

### Facility-/Hausmeister-Tagesliste (Rüstliste)

Tagesarbeitsliste für Hausdienst und Medientechnik: pro Raum chronologisch Auf-/Abschlusszeiten, gewünschte Bestuhlung, angeforderte Technik (Beamer, Mikrofon) aus den Event-Attributen. Wird morgens gedruckt und abgearbeitet.

- **Tier:** gruppiert (1D) · **Auth:** Session · **mehrere Dokumente pro View:** nein
- **Datenquelle:** Events-View 'heute, Gebäude X' mit den Ausstattungs-/Bestuhlungs-Classification-Attributen der Eventtypen, gruppiert nach Raum
- **Link-Navigation:** Raum-Sektion → Raumwochenplan (Vorausschau: was kommt morgen in diesem Raum). Sonst keine.

### Dozenten-Semesterübersicht / Deputatsliste

Alle Lehrtermine eines Dozenten über das Semester, gruppiert nach Monat, mit serverseitig vorberechneten Stundensummen (Mustache kann nicht rechnen — Summen kommen als compute-/Aggregat-Feld aus der View). Grundlage für Deputatsabrechnung und Lehrauftrags-Nachweise.

- **Tier:** gruppiert (1D) · **Auth:** Session · **mehrere Dokumente pro View:** ja
- **Datenquelle:** appointmentBlocks-View gefiltert auf Dozenten-Ressource, Semesterfenster, gruppiert nach Monat; Summenfelder serverseitig (D3: Formatierung und Berechnung leben in der Query)
- **Link-Navigation:** Monats-Header → Dozenten-Wochenplan der jeweiligen Woche (parametrisierter Link); sonst keine. Dritte Doku über der Dozenten-Belegungs-View (neben Wochenplan und Bürotürschild).

## Maschinenformate

### CSV-Export-Ablösung (/rapla/calendar.csv)

Die CSV-Varianten der Export-Routen werden heute ebenfalls in Java assembliert. Ein Mustache-Template ist textformat-agnostisch — dieselbe Dokument-Pipeline kann text/csv emittieren; ein Admin kann Spalten ändern ohne Java-Patch.

- **Tier:** flach · **Auth:** publiziert (anonym) · **mehrere Dokumente pro View:** ja
- **Datenquelle:** appointmentBlocks flach mit @column-Spalten (dieselbe Datenbasis wie die Terminliste); Routen /rapla/calendar.csv + /rapla/internal_calendar.csv in CalendarPageController, 🔒-frozen
- **Link-Navigation:** keine — Maschinenformat
- **Uni-Beleg:** Routen laufen über dieselbe Uni-Deployment-Rewrite-Infrastruktur (infrastructure.md); konkreter CSV-Konsument nicht einzeln belegt

## Formulare & Workflows (interaktiv, schreibend — Phase 9 + [PRD 102](../prd/102-browser-credential-hardening.md) D7)

Die ersten **schreibenden** Dokumente: das Template rendert ein Formular, dessen Absenden
serverseitig eine Mutation auslöst. Neu gegenüber allen Fällen oben (die nur rendern und
drucken) sind drei Dinge — die *gerichtete Capability* als Auth-Klasse, das native
`<form>`-POST als Schreibpfad, und ein **Plugin**, das den Fach-Workflow besitzt. Das
Dokument-/Template-Modell ([PRD 097](../prd/097-event-html-templates-mustache.md)) bleibt fach-blind; das Token-Primitiv gehört
[PRD 102](../prd/102-browser-credential-hardening.md); der Ablauf (Anfragen beobachten,
Token prägen, Mails versenden, Entscheidungs-Endpoint prüfen) liegt im Plugin.

**Warum kein einfacher Link.** Ein „hier klicken zum Genehmigen"-Link als bloßer GET ist
kaputt: Mail-Scanner, Link-Prüfer und Prefetcher lösen den GET beim Zustellen aus und
genehmigen automatisch, bevor ein Mensch klickt. Eine Entscheidung ist ein Schreibvorgang
(§16) und muss ein POST hinter einem Button sein — deshalb überhaupt das Formular.

### Raumanfrage-Genehmigung per E-Mail (Accept/Deny-Formular)

Eine Raumanfrage entsteht in rapla; rapla mailt dem Genehmiger (Raumeigentümer) einen Link
auf eine Accept/Deny-Seite. Klick rendert die Anfrage-Zusammenfassung plus zwei Buttons; ein
Klick auf *Annehmen*/*Ablehnen* postet cookielos an den Plugin-Endpoint, der das Token prüft,
die Entscheidung ausführt, den Antragsteller benachrichtigt und das Token verbraucht.

- **Tier:** flach · **Auth:** gerichtete Capability (E-Mail-Link, einmalig) · **mehrere Dokumente pro View:** ja
- **Datenquelle:** einzelne Raumanfrage per ID, **ins Dokument gebacken** — es gibt keinen
  Aufrufer-Scope (der Klickende ist evtl. gar nicht eingeloggt); das Plugin liefert die
  Anfrage-Details mit Systemautorität, das versiegelte Token grenzt sie auf die eine Anfrage
  ein. Kein View-in-Caller-Scope wie bei den Session-Dokumenten.
- **Link-Navigation:** der E-Mail-Link **ist** die Navigation; das Formular postet an einen
  **registrierten** Plugin-Endpoint (`form-action` darauf gepinnt). GET rendert (idempotent,
  wiederholbar); POST entscheidet (verbraucht das Token einmalig — Zweitklick zeigt „bereits
  am … entschieden", löst kein zweites Schreiben aus).
- **Sicherheitsform:** opaque origin + `SameSite=Lax` ⇒ Cookie beim POST nicht mitgesendet,
  Token ist alleinige Autorität (identisch, ob Klickender eingeloggt oder anonym); Token
  versiegelt (`UrlCipherV2` GCM, `requestId` im Klartext-Payload, nicht ratbarer Pfad),
  eng (nur diese eine Anfrage, Accept-oder-Deny), `exp` an die Entscheidungsfrist gebunden,
  einmalig, trägt die Genehmiger-Identität für die Audit-Spur.
- **Beleg:** kein direkter Uni-Beleg — aus der Formular-/Sicherheits-Diskussion 2026-07-10
  ([PRD 102](../prd/102-browser-credential-hardening.md) D7 write-capability + [PRD 097](../prd/097-event-html-templates-mustache.md) Phase 9). Genehmigungs-Workflows sind Standard in
  der Raumverwaltung; hier als Referenz-Use-Case für den schreibenden Pfad durchgearbeitet.

### Slot-Auswahl durch Dozent (1-aus-N angebotene Termine)

Statt binär Accept/Deny wählt der Dozent einen aus mehreren angebotenen Terminen. Verallgemeinert
den vorigen Fall von „eine Aktion" auf „eine Auswahl aus einer deklarierten Menge" — bleibt aber
ein **statisches** Formular ohne Script: native Radio-Buttons / `<select>` / mehrere
Submit-Buttons genügen. Die statische CSP-Stufe (`script-src 'none'`, `sandbox allow-forms`) trägt
das.

- **Tier:** flach · **Auth:** gerichtete Capability · **mehrere Dokumente pro View:** ja
- **Datenquelle:** Kandidaten-Slots vom Plugin **bei Anfrage-Erstellung berechnet und ins
  Dokument gebacken** (das Plugin hat rapla-Verfügbarkeit); das Token deklariert die angebotene
  Menge. Der Endpoint **vertraut dem Formularwert nicht**: geprüft wird Mitgliedschaft in der
  deklarierten Menge **plus** aktuelle Verfügbarkeit.
- **Link-Navigation:** E-Mail-Link → Formular → POST an registrierten Endpoint (wie oben).
- **Neues Kernproblem = Veralten/Race, nicht UI:** zwischen Mailversand und Klick (evtl. Tage)
  kann ein Slot belegt werden. Das angebotene Set ist ein *Vorschlag*; der POST macht
  atomisches check-and-book; ist der Slot weg, schlägt das Plugin die verbliebenen neu vor
  (zweites Rendern). Das ist Plugin-Workflow, keine CSP-Frage.
- **Eskalationsgrenze:** zur Render-Zeit **bekannte** Auswahl ⇒ statisches Formular (hier).
  **Live berechnete** Auswahl (freie Verfügbarkeit browsen, abhängige Dropdowns, Kalender-Picker)
  ⇒ Komponenten-Stufe ([PRD 102](../prd/102-browser-credential-hardening.md) D6) oder in die SPA eingebettet, weil `connect-src 'none'` dem
  Dokument jedes Nachladen verbietet. Die Zahl der Optionen treibt die Stufe **nicht** — nur
  ob sie gebacken oder live sind.
- **Beleg:** Design-Diskussion 2026-07-10; verallgemeinert Accept/Deny.

### Vorgeschlagener Implementierungsweg

Reihenfolge so, dass jeder Schritt für sich testbar ist und der Engine kein Fach-Wissen zuwächst.
Nichts davon ist heute gebaut; Formulare sind derzeit hart blockiert (`form-action 'none'`, bare
`sandbox`) und der Sanitizer fasst `<form>` noch nicht an — inert, aber ein latenter Footgun.

1. **Sanitizer-Regeln für Formulare — im selben Change wie `allow-forms`, nie davor/danach.**
   `type=password` verbieten, `action`/`formaction` auf den registrierten Endpoint pinnen bzw.
   strippen, Methode auf POST zwingen. Solange kein `allow-forms` gesetzt ist, bleiben Formulare
   unabsendbar — das ist der korrekte Zwischenstand.
2. **Registry der erlaubten Submit-Endpoints** (Geschwister der Komponenten-/Script-Allowlist aus
   [PRD 102](../prd/102-browser-credential-hardening.md) D5/D6): Key → `{ url, erlaubte Dokumentnamen, benötigter Capability-Scope }`. Speist
   dreierlei: `form-action`-CSP, Sanitizer-`action`-Allowlist, Capability-Prägung. Admin
   referenziert **per Key, nie per URL** — Tippfehler/Exfil-URL fallen weg.
3. **Write-Capability-Primitiv erweitern** ([PRD 102](../prd/102-browser-credential-hardening.md) D7): `mintWrite(subject, object,
   actions/erlaubte-Menge, exp, singleUse)`, **durch ein Server-Event prägbar** (bei Mailversand,
   nicht erst beim Render), `validate()` von GET-Render **und** POST-Submit geteilt, Verbrauch an
   den Schreibvorgang gebunden (nicht ans Rendern).
4. **Render-Service als Plugin-Bean mit „gebackene Daten"-Modus** ([PRD 097](../prd/097-event-html-templates-mustache.md)): das Plugin muss
   Template (Mailbody, Entscheidungsseite) mit selbst beschafften Daten rendern können — es gibt
   keinen Aufrufer-Scope, in dem eine View liefe. Die Preview-Route rendert bereits aus einer
   Map, die Form existiert also schon.
5. **Fach-Plugin** (eigener PRD, spannt 097 + 102 + neues Plugin): Anfragen beobachten →
   `mintWrite` → Mail rendern+senden → Entscheidungs-Endpoint (`permitAll`-Chain mit eigenem
   Token-Check, Muster `oauthHelperFilterChain`) → Token prüfen, §12/§16-geprüfte Mutation,
   für den 1-aus-N-Fall Slot-Mitgliedschaft **und** Verfügbarkeit re-validieren, Token verbrauchen.
6. **Admin-Konfiguration:** Mail-Template + Entscheidungs-Template wählen, Formular per Key an den
   registrierten Endpoint hängen, TTL setzen.

Offen (gehört in den Plugin-PRD): **ein generischer Engine-Submit-Endpoint mit Dispatch** vs.
**pro-Plugin-Endpoints in der Registry** (hier skizziert — passt zu raplas Plugin-besitzt-seinen-
Controller-Modell, Kosten: `form-action` lässt N Endpoints zu statt einen; die Registry hält N
ehrlich). Ebenso offen: eingebettet-in-SPA (`postMessage` an den Parent) vs. standalone
(natives Formular) — das ist **[PRD 102](../prd/102-browser-credential-hardening.md) OQ4**.
