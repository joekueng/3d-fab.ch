import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { resolve } from 'node:path';
import ts from 'typescript';

const sources = {
  app: 'src/app/app.routes.ts',
  about: 'src/app/features/about/about.routes.ts',
  calculator: 'src/app/features/calculator/calculator.routes.ts',
  contact: 'src/app/features/contact/contact.routes.ts',
  legal: 'src/app/features/legal/legal.routes.ts',
  shop: 'src/app/features/shop/shop.routes.ts',
  admin: 'src/app/features/admin/admin.routes.ts',
};

const manifestSource = readFileSync(resolve('e2e/coverage-manifest.ts'), 'utf8');
const javascript = ts.transpileModule(manifestSource, {
  compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 },
}).outputText;
const { routeCoverage, scenarioCatalog } = await import(`data:text/javascript,${encodeURIComponent(javascript)}`);

const missing = [];
const stale = [];
for (const [source, path] of Object.entries(sources)) {
  const content = readFileSync(resolve(path), 'utf8');
  const current = new Set([...content.matchAll(/\bpath:\s*'([^']*)'/g)].map((match) => match[1]));
  const recorded = new Set(routeCoverage.filter((item) => item.source === source).map((item) => item.path));
  for (const route of current) if (!recorded.has(route)) missing.push(`${source}: ${route}`);
  for (const route of recorded) if (!current.has(route)) stale.push(`${source}: ${route}`);
}

for (const [id, scenario] of Object.entries(scenarioCatalog)) {
  const specPath = resolve('e2e', scenario.spec);
  if (!existsSync(specPath) || !readFileSync(specPath, 'utf8').includes(id)) {
    stale.push(`${id}: missing from ${scenario.spec}`);
  }
}
for (const route of routeCoverage) {
  if (!scenarioCatalog[route.scenario]) {
    missing.push(`${route.source}: ${route.path} references unknown ${route.scenario}`);
  }
}
function specFiles(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = resolve(directory, entry.name);
    return entry.isDirectory() ? specFiles(path) : entry.name.endsWith('.spec.ts') ? [path] : [];
  });
}
for (const path of specFiles(resolve('e2e'))) {
  if (path.endsWith('/dev-smoke.spec.ts')) continue;
  const ids = [...readFileSync(path, 'utf8').matchAll(/\b[A-Z][A-Z-]*-\d{3}\b/g)].map((match) => match[0]);
  for (const id of ids) if (!scenarioCatalog[id]) missing.push(`${path}: ${id} is not catalogued`);
}

if (missing.length || stale.length) {
  if (missing.length) console.error(`Routes requiring a coverage decision:\n${missing.join('\n')}`);
  if (stale.length) console.error(`Stale route entries:\n${stale.join('\n')}`);
  process.exit(1);
}
console.log(`E2E inventory matches ${routeCoverage.length} route decisions and ${Object.keys(scenarioCatalog).length} scenarios.`);
