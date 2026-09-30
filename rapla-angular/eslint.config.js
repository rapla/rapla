// @ts-check
const eslint = require('@eslint/js');
const { defineConfig } = require('eslint/config');
const tseslint = require('typescript-eslint');
const angular = require('angular-eslint');

module.exports = defineConfig([
  {
    ignores: ['dist/**'],
  },
  {
    files: ['**/*.ts'],
    extends: [
      eslint.configs.recommended,
      tseslint.configs.recommended,
      tseslint.configs.stylistic,
      angular.configs.tsRecommended,
    ],
    processor: angular.processInlineTemplates,
    rules: {
      // Secure-context-only browser APIs are undefined over plain http://<ip> (customer
      // intranet, 2026-09-30: every "Neu" dialog and the copy chips died). Go through the helpers that
      // carry a fallback; add a helper before allowing a new API here.
      'no-restricted-properties': [
        'error',
        {
          object: 'crypto',
          property: 'randomUUID',
          message: 'Secure-context only — use typedId()/uuidV4() from event/event-draft.ts.',
        },
        {
          object: 'crypto',
          property: 'subtle',
          message: 'Secure-context only — undefined over http://<ip>; needs a fallback helper.',
        },
        {
          object: 'navigator',
          property: 'clipboard',
          message: 'Secure-context only — use copyText() from common/copy-text.ts.',
        },
        {
          object: 'navigator',
          property: 'share',
          message: 'Secure-context only — undefined over http://<ip>; needs a fallback helper.',
        },
        {
          object: 'navigator',
          property: 'serviceWorker',
          message: 'Secure-context only — undefined over http://<ip>.',
        },
        {
          object: 'navigator',
          property: 'geolocation',
          message: 'Secure-context only — undefined over http://<ip>.',
        },
      ],
      // The rule above only sees a bare object name; catch the qualified forms too.
      'no-restricted-syntax': [
        'error',
        {
          selector:
            "MemberExpression[object.type='MemberExpression'][object.property.name='crypto'][property.name=/^(randomUUID|subtle)$/]",
          message: 'Secure-context only — use typedId()/uuidV4() from event/event-draft.ts.',
        },
        {
          selector:
            "MemberExpression[object.type='MemberExpression'][object.property.name='navigator'][property.name=/^(clipboard|share|serviceWorker|geolocation)$/]",
          message: 'Secure-context only — undefined over http://<ip>; use or add a fallback helper.',
        },
      ],
      '@angular-eslint/directive-selector': [
        'error',
        {
          type: 'attribute',
          prefix: 'app',
          style: 'camelCase',
        },
      ],
      '@angular-eslint/component-selector': [
        'error',
        {
          type: 'element',
          prefix: 'app',
          style: 'kebab-case',
        },
      ],
    },
  },
  {
    files: ['**/*.html'],
    extends: [angular.configs.templateRecommended, angular.configs.templateAccessibility],
    rules: {},
  },
]);
