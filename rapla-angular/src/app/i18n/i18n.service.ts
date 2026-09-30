import { HttpClient } from '@angular/common/http';
import { Injectable, Pipe, type PipeTransform, inject } from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localeCs from '@angular/common/locales/cs';
import localeDe from '@angular/common/locales/de';
import localeEs from '@angular/common/locales/es';
import localeFi from '@angular/common/locales/fi';
import localeFr from '@angular/common/locales/fr';
import localeNl from '@angular/common/locales/nl';
import localePl from '@angular/common/locales/pl';
import localePt from '@angular/common/locales/pt';
import { firstValueFrom } from 'rxjs';

// PRD 124 OQ4 — the nine rapla languages, bundled statically (en is built in).
for (const data of [
  localeCs,
  localeDe,
  localeEs,
  localeFi,
  localeFr,
  localeNl,
  localePl,
  localePt,
]) {
  registerLocaleData(data);
}
const LANGUAGES = new Set(['en', 'cs', 'de', 'es', 'fi', 'fr', 'nl', 'pl', 'pt']);
const SPA_BUNDLE = 'org.rapla.SpaResources';
const MAIN_BUNDLE = 'org.rapla.RaplaResources';

/** Wire shape of GET /api/locale/{id} (rapla-core LocalePackage), the fields the SPA reads. */
export interface LocalePackage {
  language: string;
  country: string;
  bundles: Record<string, Record<string, string>>;
}

/**
 * Swing semantics: without arguments the text is returned raw (getString); with arguments
 * it is a MessageFormat pattern — {n} is substituted and '' becomes '.
 */
// ponytail: {n} + '' only; no choice/number formats (the bundles don't use them in the SPA)
export function formatText(pattern: string, args: unknown[]): string {
  if (args.length === 0) return pattern;
  return pattern
    .replace(/\{(\d+)\}/g, (m, i: string) =>
      Number(i) < args.length ? String(args[Number(i)]) : m,
    )
    .replace(/''/g, "'");
}

export function localeIdOf(language: string, country: string): string {
  if (!LANGUAGES.has(language)) return 'en';
  return /^[A-Z]{2}$/.test(country) ? `${language}-${country}` : language;
}

// PRD 124 D1 — one catalogue per page life (a language switch reloads), so module state:
// plain TS code calls t() without DI, and specs install the German catalogue once.
let catalogue: { texts: Record<string, string>; locale: string } = { texts: {}, locale: 'de-DE' };

export function setCatalogue(texts: Record<string, string>, locale: string): void {
  catalogue = { texts, locale };
}

export function t(key: string, ...args: unknown[]): string {
  return formatText(catalogue.texts[key] ?? key, args);
}

export function localeId(): string {
  return catalogue.locale;
}

/**
 * Loads all texts of the user's language in one request, resolved server-side (user
 * preference, else server language). Keys are the Swing keys; SPA-only texts live in
 * SpaResources. Merge order: plugin bundles, then RaplaResources, then SpaResources (last wins).
 */
@Injectable({ providedIn: 'root' })
export class I18nService {
  private readonly http = inject(HttpClient);

  async load(): Promise<void> {
    try {
      const pkg = await firstValueFrom(this.http.get<LocalePackage>('/api/locale/spa'));
      const { [MAIN_BUNDLE]: main = {}, [SPA_BUNDLE]: spa = {}, ...plugins } = pkg.bundles;
      setCatalogue(
        Object.assign({}, ...Object.values(plugins), main, spa),
        localeIdOf(pkg.language, pkg.country),
      );
    } catch (e) {
      console.warn('[i18n] catalogue not loaded, showing keys', e);
      setCatalogue({}, 'de-DE');
    }
  }
}

@Pipe({ name: 't' })
export class TPipe implements PipeTransform {
  transform(key: string, ...args: unknown[]): string {
    return t(key, ...args);
  }
}
