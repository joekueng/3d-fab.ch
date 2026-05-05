#!/usr/bin/env node

import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { dirname, extname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const rootDir = join(scriptDir, '..');

const scanRoots = [
  join(rootDir, 'src', 'app', 'features'),
  join(rootDir, 'src', 'styles'),
];

const rules = [
  {
    name: 'carousel-dot-clone',
    pattern:
      /\.(?!ui-carousel-dot\b)[A-Za-z0-9_-]*(?:carousel|slider|gallery|project)[A-Za-z0-9_-]*-dot\b/g,
    message:
      'Use .ui-carousel-dot from src/styles/_ui.scss instead of page-local dot styles.',
  },
  {
    name: 'carousel-keyframes-clone',
    pattern:
      /@keyframes\s+(?!uiCarouselProgress\b)[A-Za-z0-9_-]*(?:Carousel|carousel|Progress|progress)[A-Za-z0-9_-]*/g,
    message:
      'Use the shared uiCarouselProgress keyframes instead of page-local carousel progress animations.',
  },
  {
    name: 'carousel-duration-token-clone',
    pattern:
      /--(?!ui-carousel-duration\b)[A-Za-z0-9_-]*(?:carousel|slider|gallery|project)[A-Za-z0-9_-]*duration\b/g,
    message:
      'Use --ui-carousel-duration for carousel progress timing.',
  },
  {
    name: 'old-home-project-display-style',
    pattern: /#d75f19|font-size:\s*3\.35rem/g,
    message:
      'Do not reintroduce the old project-carousel orange eyebrow or hardcoded oversized title.',
  },
  {
    name: 'old-carousel-dot-class',
    pattern: /\b(?:home-project-dot|shop-carousel-dot)\b/g,
    message:
      'Use class="ui-carousel-dot"; keep page-local classes only for layout containers.',
  },
];

const allowedExtensions = new Set(['.html', '.scss', '.ts']);
const files = scanRoots.flatMap((root) => walk(root)).filter((file) =>
  allowedExtensions.has(extname(file)),
);

const failures = [];

for (const file of files) {
  const source = readFileSync(file, 'utf8');
  for (const rule of rules) {
    for (const match of source.matchAll(rule.pattern)) {
      failures.push({
        file,
        index: match.index ?? 0,
        match: match[0],
        rule,
      });
    }
  }
}

if (failures.length > 0) {
  console.error('UI reuse check failed.');
  for (const failure of failures) {
    const line = lineNumber(failure.index, readFileSync(failure.file, 'utf8'));
    const filePath = relative(rootDir, failure.file);
    console.error(
      `- ${filePath}:${line} [${failure.rule.name}] ${failure.match}`,
    );
    console.error(`  ${failure.rule.message}`);
  }
  process.exit(1);
}

console.log('UI reuse check passed.');

function walk(root) {
  if (!existsSync(root)) {
    return [];
  }

  return readdirSync(root, { withFileTypes: true }).flatMap((entry) => {
    const fullPath = join(root, entry.name);
    if (entry.isDirectory()) {
      return walk(fullPath);
    }
    return entry.isFile() ? [fullPath] : [];
  });
}

function lineNumber(index, source) {
  return source.slice(0, index).split('\n').length;
}
