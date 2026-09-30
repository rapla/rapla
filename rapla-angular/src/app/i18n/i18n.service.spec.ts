import { HttpClient } from '@angular/common/http';
import { Injector, runInInjectionContext } from '@angular/core';
import { of, throwError } from 'rxjs';

import {
  I18nService,
  type LocalePackage,
  TPipe,
  formatText,
  localeId,
  localeIdOf,
  setCatalogue,
  t,
} from './i18n.service';

function pkg(language: string, country: string, bundles: LocalePackage['bundles']): LocalePackage {
  return { language, country, bundles };
}

function service(response: LocalePackage | Error): I18nService {
  const http = {
    get: () => (response instanceof Error ? throwError(() => response) : of(response)),
  } as unknown as HttpClient;
  const injector = Injector.create({ providers: [{ provide: HttpClient, useValue: http }] });
  return runInInjectionContext(injector, () => new I18nService());
}

describe('formatText', () => {
  it('returns a pattern without arguments unchanged, like Swing getString', () => {
    expect(formatText("Benutzer ''{0}''", [])).toBe("Benutzer ''{0}''");
  });

  it('substitutes {n} and unescapes quotes like MessageFormat', () => {
    expect(formatText("Benutzer ''{0}'' hat {1} Termine", ['homer', 3])).toBe(
      "Benutzer 'homer' hat 3 Termine",
    );
  });
});

describe('localeIdOf', () => {
  it('uses language and country of a supported language', () => {
    expect(localeIdOf('fr', 'FR')).toBe('fr-FR');
    expect(localeIdOf('pt', '')).toBe('pt');
  });

  it('falls back to en for a language without locale data', () => {
    expect(localeIdOf('da', 'DK')).toBe('en');
  });
});

describe('I18nService', () => {
  afterEach(() => setCatalogue({}, 'de-DE'));

  it('merges all bundles, the SPA bundle winning, and formats', async () => {
    const svc = service(
      pkg('fr', 'FR', {
        'org.rapla.plugin.x.XResources': { save: 'plugin', only_plugin: 'P' },
        'org.rapla.RaplaResources': { save: 'Enregistrer', cancel: 'Annuler' },
        'org.rapla.SpaResources': { cancel: 'Annuler SPA', greet: 'Bonjour {0}' },
      }),
    );
    await svc.load();
    expect(localeId()).toBe('fr-FR');
    expect(t('save')).toBe('Enregistrer');
    expect(t('cancel')).toBe('Annuler SPA');
    expect(t('only_plugin')).toBe('P');
    expect(t('greet', 'homer')).toBe('Bonjour homer');
    expect(new TPipe().transform('greet', 'bart')).toBe('Bonjour bart');
  });

  it('shows the key and keeps de-DE when the catalogue cannot be loaded', async () => {
    const svc = service(new Error('401'));
    await svc.load();
    expect(localeId()).toBe('de-DE');
    expect(t('save')).toBe('save');
  });
});
