import { beforeEach } from 'vitest';

import raplaEn from '../../rapla-core/src/main/resources/org/rapla/RaplaResources.properties';
import raplaDe from '../../rapla-core/src/main/resources/org/rapla/RaplaResources_de.properties';
import spaEn from '../../rapla-core/src/main/resources/org/rapla/SpaResources.properties';
import spaDe from '../../rapla-core/src/main/resources/org/rapla/SpaResources_de.properties';
import { setCatalogue } from './app/i18n/i18n.service';
import { parseProperties } from './app/i18n/properties';

// PRD 124 — specs run against the German catalogue (English per-key fallback, as the
// server does), so existing German assertions stay valid after the string migration.
setCatalogue(Object.assign({}, ...[raplaEn, raplaDe, spaEn, spaDe].map(parseProperties)), 'de-DE');

// Persisted view state (scope chips, render mode, date window) lives in
// localStorage (state/persist.ts). Clear it before each test so persisted state
// never leaks between tests and the stores start from their documented defaults.
beforeEach(() => {
  try {
    localStorage.clear();
  } catch {
    // jsdom without storage — nothing to clear.
  }
});
