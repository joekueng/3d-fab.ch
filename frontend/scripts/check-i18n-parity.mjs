#!/usr/bin/env node

import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { dirname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const frontendDir = join(scriptDir, '..');
const i18nDir = join(frontendDir, 'src', 'assets', 'i18n');
const referenceLang = 'it';
const requiredLangs = ['it', 'en', 'de', 'fr'];

const localeFiles = requiredLangs.map((lang) => ({
  lang,
  file: join(i18nDir, `${lang}.json`),
}));

const failures = [];

for (const { lang, file } of localeFiles) {
  if (!existsSync(file)) {
    failures.push(`${lang}: missing ${relative(frontendDir, file)}`);
  }
}

if (failures.length > 0) {
  reportAndExit(failures);
}

const locales = Object.fromEntries(
  localeFiles.map(({ lang, file }) => [lang, readJson(file)]),
);
const flattened = Object.fromEntries(
  Object.entries(locales).map(([lang, data]) => [lang, flatten(data)]),
);
const reference = flattened[referenceLang];
const referenceKeys = new Set(Object.keys(reference));

for (const lang of requiredLangs.filter((lang) => lang !== referenceLang)) {
  const current = flattened[lang];
  const currentKeys = new Set(Object.keys(current));

  for (const key of referenceKeys) {
    if (!currentKeys.has(key)) {
      failures.push(`${lang}: missing key ${key}`);
      continue;
    }

    const expected = reference[key];
    const actual = current[key];
    if (expected.type !== actual.type) {
      failures.push(
        `${lang}: ${key} has type ${actual.type}, expected ${expected.type}`,
      );
      continue;
    }

    if (
      expected.type === 'string' &&
      expected.value.trim().length > 0 &&
      actual.value.trim().length === 0
    ) {
      failures.push(`${lang}: ${key} is empty`);
    }

    if (
      expected.type === 'array' &&
      expected.value.length !== actual.value.length
    ) {
      failures.push(
        `${lang}: ${key} has ${actual.value.length} entries, expected ${expected.value.length}`,
      );
    }

    if (expected.type === 'string' && actual.type === 'string') {
      const expectedPlaceholders = placeholders(expected.value);
      const actualPlaceholders = placeholders(actual.value);
      if (expectedPlaceholders.join(',') !== actualPlaceholders.join(',')) {
        failures.push(
          `${lang}: ${key} placeholders [${actualPlaceholders.join(
            ', ',
          )}] do not match reference [${expectedPlaceholders.join(', ')}]`,
        );
      }
    }
  }

  for (const key of currentKeys) {
    if (!referenceKeys.has(key)) {
      failures.push(`${lang}: extra key ${key}`);
    }
  }
}

const unknownFiles = readdirSync(i18nDir).filter(
  (entry) =>
    entry.endsWith('.json') && !requiredLangs.includes(entry.slice(0, -5)),
);
if (unknownFiles.length > 0) {
  failures.push(
    `unexpected locale files: ${unknownFiles
      .map((file) => relative(frontendDir, join(i18nDir, file)))
      .join(', ')}`,
  );
}

if (failures.length > 0) {
  reportAndExit(failures);
}

console.log(
  `i18n parity check passed for ${requiredLangs.join(', ')} using ${referenceLang}.json as reference.`,
);

function readJson(file) {
  try {
    return JSON.parse(readFileSync(file, 'utf8'));
  } catch (error) {
    reportAndExit([`${relative(frontendDir, file)}: ${error.message}`]);
  }
}

function flatten(value, path = '') {
  if (Array.isArray(value)) {
    return {
      [path]: {
        type: 'array',
        value,
      },
    };
  }

  if (value && typeof value === 'object') {
    return Object.entries(value).reduce((accumulator, [key, child]) => {
      return {
        ...accumulator,
        ...flatten(child, path ? `${path}.${key}` : key),
      };
    }, {});
  }

  return {
    [path]: {
      type: typeof value,
      value: String(value ?? ''),
    },
  };
}

function placeholders(value) {
  const matches = [...value.matchAll(/{{\s*([A-Za-z0-9_.-]+)\s*}}/g)].map(
    (match) => match[1],
  );
  return [...new Set(matches)].sort();
}

function reportAndExit(messages) {
  console.error('i18n parity check failed.');
  for (const message of messages) {
    console.error(`- ${message}`);
  }
  process.exit(1);
}
