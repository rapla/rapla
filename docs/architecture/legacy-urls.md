# Legacy URLs — the `/rapla/` prefix after the Spring Boot migration

The Jetty-era deployment served the whole webapp under the **context root `/rapla`**.
The Spring Boot migration (PRD 031) moved the context root to **`/`** — and that split
one URL space into two responsibilities that are easy to get wrong:

## Server side — routes kept their literal paths

The externally-subscribed endpoints deliberately kept `/rapla/` as a **literal path
prefix in their mappings** (`CalendarPageController` → `/rapla/calendar(.csv)?`,
`Export2iCalController` → `/rapla/ical`, …). They are 🔒-protected by the AGENTS.md §15
allow-list and `ApiPrefixArchitectureTest`: external iCal subscribers (Outlook, Google,
Apple) hold these exact URLs, so they must never move.

## Client side — URL generators must emit the prefix explicitly

Under Jetty, client code built subscription/export URLs as `codeBase + "calendar"` and
the *container* supplied `/rapla/`. Since the context-root move, the same code silently
produces `/calendar` — a 404 — while the server routes still answer at
`/rapla/calendar`. **Any code that generates a user-facing calendar/export URL (Swing
dialogs, HTML pages, iCal export, autoexport links) must emit the `/rapla/` prefix
itself.** When an old bookmarked URL works but a freshly generated one doesn't, check
the generator, not the controller (regression found 2026-06-18 after a multi-hour
debug; the fix touched URL generation only — existing published URLs never changed).

## `UrlEncryptor` — the salt rides inside the returned string

`UrlEncryptor.encrypt(plain)` does not return just the ciphertext: it returns
**`<base64-blob>&salt=<salt>`** — the salt parameter is embedded in the return value.
The decrypt side (`EncryptedHttpServletRequest`) expects to find `salt` as a request
parameter and reconstructs the key from it.

Consequences:
- The return value is only safe **appended raw** into a query string
  (`?page=calendar&user=x&key=` + encrypted). URL-encoding it, splitting on `&`, or
  storing it as a single opaque token breaks decryption with a key mismatch.
- `EncryptedHttpServletRequest.getRequestURI()` historically returns a **full URL**,
  not a path — callers that expect servlet-spec semantics must not parse it as a path.

Both behaviors are load-bearing for every published calendar URL in the wild; treat
the wire format as frozen.
