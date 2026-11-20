import { spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';

const advisory = 'https://github.com/advisories/GHSA-fc5q-39xc-vjpf';
const expires = '2026-11-25T00:00:00Z';
const reviewedNodes = new Map([
  ['node_modules/@angular-devkit/architect', '0.2202.3'],
  ['node_modules/@angular-devkit/core', '22.2.3'],
  ['node_modules/@angular-devkit/schematics', '22.2.3'],
  ['node_modules/@angular/build', '22.2.3'],
  ['node_modules/@angular/cli', '22.2.3'],
  ['node_modules/@parcel/watcher', '2.6.0'],
  ['node_modules/@schematics/angular', '22.2.3'],
  ['node_modules/@vitejs/plugin-basic-ssl', '2.3.0'],
  ['node_modules/anymatch', '3.1.3'],
  ['node_modules/anymatch/node_modules/picomatch', '2.3.2'],
  ['node_modules/fdir', '6.5.0'],
  ['node_modules/karma', '6.4.4'],
  ['node_modules/karma-jasmine', '5.1.0'],
  ['node_modules/karma/node_modules/chokidar', '3.6.0'],
  ['node_modules/karma/node_modules/picomatch', '2.3.2'],
  ['node_modules/karma/node_modules/readdirp', '3.6.0'],
  ['node_modules/picomatch', '4.0.7'],
  ['node_modules/tinyglobby', '0.2.17'],
  ['node_modules/vite', '8.3.3'],
]);

// npm still reports this withdrawn advisory. Unknown findings fail closed.
export function checkAudit(report, lock, now = new Date()) {
  if (report.auditReportVersion !== 2 || report.error || !report.vulnerabilities ||
      typeof report.vulnerabilities !== 'object' || Array.isArray(report.vulnerabilities)) {
    throw new Error('Invalid npm audit report');
  }
  const vulnerabilities = report.vulnerabilities;
  function accepted(name, visiting = new Set()) {
    const finding = vulnerabilities[name];
    if (!(now < new Date(expires)) || visiting.has(name) ||
        finding?.name !== name || !Array.isArray(finding.nodes) || !finding.nodes.length ||
        !Array.isArray(finding.via) || !finding.via.length) return false;
    if (!finding.nodes.every(node => {
      if (typeof node !== 'string' || !reviewedNodes.has(node) ||
          !('/' + node).endsWith('/node_modules/' + name)) return false;
      const entry = lock.packages?.[node];
      return entry?.dev === true && entry.version === reviewedNodes.get(node);
    })) return false;
    const next = new Set([...visiting, name]);
    return finding.via.every(cause => {
      if (typeof cause === 'string') return accepted(cause, next);
      return name === 'picomatch' && cause?.name === name && cause.url === advisory;
    });
  }
  const allowed = [], blocked = [];
  for (const name of Object.keys(vulnerabilities)) {
    (accepted(name) ? allowed : blocked).push(name);
  }
  return { allowed, blocked };
}

function main() {
  const frontend = fileURLToPath(new URL('../frontend/', import.meta.url));
  const result = spawnSync('npm', ['audit', '--package-lock-only', '--ignore-scripts', '--json'], {
    cwd: frontend, encoding: 'utf8', maxBuffer: 10 * 1024 * 1024,
  });
  if (result.error || ![0, 1].includes(result.status)) throw new Error('npm audit could not complete');
  const report = JSON.parse(result.stdout);
  const lock = JSON.parse(readFileSync(new URL('../frontend/package-lock.json', import.meta.url), 'utf8'));
  const { allowed, blocked } = checkAudit(report, lock);
  if (result.status === 1 && !allowed.length && !blocked.length) throw new Error('npm audit failed without findings');
  if (allowed.length) {
    console.log(`Temporary development-only exception: ${advisory}; expires ${expires}`);
    console.log(`Affected development packages: ${allowed.join(', ')}`);
  }
  if (blocked.length) {
    console.error(`Unaccepted audit findings: ${blocked.join(', ')}`);
    process.exitCode = 1;
  } else {
    console.log('Frontend audit policy passed.');
  }
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try { main(); }
  catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
