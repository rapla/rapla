# PRD 103 — i18n language coverage: pick the languages to complete

**Status:** in-progress — 2026-07-21 (gaps filled by LLM translation; native review outstanding)
**Related:** PRD 072 (combined login page — its i18n pass on 2026-07-21 triggered this audit)

## Abstract

Rapla ships nine UI languages, but only German is complete; the others sit at
63–77% of the English base and most plugin bundles are German-only. This PRD
holds the measured coverage data and the decision: which languages do we commit
to keeping complete, and what happens to the rest (leave as-is with per-key
English fallback, or drop).

## Measured coverage (2026-07-21)

Text keys only — `icon.*` keys (icon resource paths, intentionally untranslated)
and the auto-generated CLDR date-format bundle
(`components/i18n/internal/locales/format_*`) are excluded. Missing keys fall
back per-key to the English base bundle at runtime, so incompleteness shows as
mixed-language UI, never as errors.

| Bundle | base | cs | de | es | fi | fr | nl | pl | pt |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| RaplaResources (main) | 507 | 386 | 507 | 414 | 449 | 448 | 436 | 434 | 440 |
| RaplaSystemInfo | 6 | 2 | 4 | 4 | 4 | 4 | 4 | 4 | 3 |
| ExternalEventImportResources | 16 | – | 16 | – | – | – | – | – | – |
| AutoExportResources | 5 | 1 | 5 | 4 | – | 4 | 4 | 1 | 1 |
| EventTimeCalculatorResources | 16 | 13 | 16 | 13 | – | 13 | 14 | 14 | 1 |
| ExchangeConnectorResources | 39 | – | 39 | – | – | – | – | – | – |
| Export2iCalResources | 12 | – | 12 | 1 | – | 12 | – | – | – |
| ImportFromICalResources | 4 | – | 4 | – | – | – | – | – | – |
| NotificationResources | 17 | 1 | 17 | 14 | – | 14 | 14 | 1 | – |
| PeriodCopyResources | 4 | – | 4 | – | – | – | – | – | – |
| PlanningStatusResources | 5 | – | 5 | – | – | – | – | – | – |
| ResourceRequestResources | 10 | – | 9 | – | – | – | – | – | – |
| SetOwnerResources | 2 | 2 | 2 | 2 | – | 2 | 2 | 2 | – |
| UrlEncryptionResources | 2 | – | 2 | – | – | – | – | – | – |
| **TOTAL (645)** | 645 | 405 | 642 | 452 | 453 | 497 | 474 | 456 | 445 |
| **%** | | 63% | **100%** | 70% | 70% | 77% | 73% | 71% | 69% |

Ranking: **de 100%** › fr 77% › nl 73% › pl 71% › es/fi 70% › pt 69% › cs 63%.
A stray `NotificationResources_da.properties` carries a single key — Danish is
not a real language in the set. `pt` is pt-BR ("Senha"/"usuário").

Notable structural facts:

- German misses only 3 keys total (1 in ResourceRequestResources, 2 in
  RaplaSystemInfo) — effectively the second source of truth alongside English.
- Six plugin bundles are **German-only** (ExchangeConnector, ImportFromICal,
  PeriodCopy, PlanningStatus, ResourceRequest, UrlEncryption,
  ExternalEventImport) — for every other language those plugins render pure
  English.
- The main-bundle gap for the non-de languages is 60–120 keys each — mostly
  keys added after ~2015 (the translations froze while English/German kept
  growing).

### Regenerating the numbers

```bash
# per-language key coverage vs the English base, icon.* excluded
python3 - <<'EOF'
import os, re, collections
def keys(fn):
    ks=set(); cont=False
    for raw in open(fn, encoding='utf-8', errors='replace'):
        line=raw.strip()
        if cont: cont=line.endswith('\\'); continue
        if not line or line[0] in '#!': continue
        cont=line.endswith('\\')
        m=re.match(r'([^=:\s]+)\s*[=:]', line)
        if m and not m.group(1).startswith('icon.'): ks.add(m.group(1))
    return ks
families={}
for root,dirs,files in os.walk('.'):
    if any(x in root for x in ('target','node_modules','/.git','.agents/worktrees','rapla-core-filtered')): continue
    if '/src/main/resources' not in root or 'i18n/internal/locales' in root: continue
    for f in files:
        m=re.match(r'(.+?)_([a-z]{2})\.properties$', f)
        if m and os.path.exists(os.path.join(root,m.group(1)+'.properties')):
            families.setdefault(os.path.join(root,m.group(1)),{})[m.group(2)]=os.path.join(root,f)
per=collections.Counter(); base_total=0
for bp,d in sorted(families.items()):
    bk=keys(bp+'.properties'); base_total+=len(bk)
    for l,f in d.items(): per[l]+=len(keys(f)&bk)
for l in sorted(per): print(f'{l}: {per[l]}/{base_total} ({100*per[l]/base_total:.0f}%)')
EOF
```

## Goal

A locked list of supported languages, each at ~100% of the text keys across all
bundles, plus a stated policy for the unsupported rest. Measurable: the script
above reports ≥99% for every language on the supported list.

## Scope

### In scope
- Decision: which of cs/es/fi/fr/nl/pl/pt to complete (and whether to add new
  languages, e.g. via LLM-assisted translation with native review).
- Filling the chosen languages' gaps (main bundle + plugin bundles).
- Policy for the rest: keep with English fallback, or delete the stale files.
- Cleanup: the one-key `NotificationResources_da.properties`.

### Out of scope
- The Angular SPA's i18n (separate mechanism, not properties bundles).
- Icon keys / the CLDR format bundles (not translations).
- Runtime language-selection mechanics (already work — server setting,
  browser fallback on the login page since 2026-07-21, per-user preference).

## Plan

### Phase 0 — Decide (this PRD's open questions)
- [x] OQ1 resolved 2026-07-21: complete ALL seven (cs/es/fi/fr/nl/pl/pt) via
      LLM-assisted translation (one agent per language, English source +
      German reference, terminology calibrated against each existing bundle).
- [ ] OQ2 (drop-or-keep policy) is moot for the seven; still open for the
      one-key `NotificationResources_da.properties`.

### Phase 1 — Fill the gaps — DONE 2026-07-21
- [x] Pass 1: 654 keys inserted into existing language files (main bundle,
      RaplaSystemInfo, AutoExport, EventTimeCalculator, Export2iCal (es),
      Notification, SetOwner).
- [x] Pass 2: 679 keys in 62 NEW files for previously German-only bundles
      (ExchangeConnector, ExternalEventImport, ImportFromICal, PeriodCopy,
      PlanningStatus, ResourceRequest, UrlEncryption, Export2iCal,
      Notification fi/pt, SetOwner fi/pt, AutoExport fi, EventTimeCalculator fi).
- [x] `complete_reservation` added to the German ResourceRequest bundle.
- [x] Verified: every file loads via `java.util.Properties`; coverage now
      ≥99.5% for all nine languages (only the Maven-token keys `rapla.build`/
      `rapla.version` and CLDR/icon keys excluded — see below).
- [ ] Native-speaker review of the machine translations (spot-check at least
      the high-visibility strings: login, menus, error dialogs).
- [ ] Delete or complete `NotificationResources_da.properties`.

### Phase 2 — Keep it complete
- [ ] Optional: a test/CI check that fails when a supported language falls
      below the threshold (same key-diff logic as the script above).

### Coverage after Phase 1 (2026-07-21)

cs/es/fr 99.8%, de 99.5%, fi/nl/pl/pt 100.0% — the sub-100 remainders are
exclusively the untranslatable Maven-filtered tokens `rapla.build` /
`rapla.version` in `RaplaSystemInfo` (identical via base-bundle fallback).

## Tests

Run the regeneration script; every supported language reports ≥99%. If Phase 2
lands, the CI check is the durable form of the same assertion.

## Open Questions

- **OQ1** — Which languages are worth completing? French (77%) is the cheapest;
  cs (63%) the most expensive. Do any have real users, or is de+en fallback the
  actual deployed reality (DHBW is de)? LLM-assisted translation makes "all
  seven" feasible if a review path exists. *Resolution:* pending.
- **OQ2** — For languages NOT on the supported list: keep the stale files
  (harmless per-key English fallback) or remove them and the language from the
  chooser? *Resolution:* pending.

## Decisions locked

*(none yet — draft)*
