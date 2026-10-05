import { spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';

const advisory = 'https://github.com/advisories/GHSA-vfj7-8cjw-p6xm';
const expires = '2026-11-04T00:00:00Z';
const testPackages = new Set(['braces', 'chokidar', 'karma', 'karma-jasmine', '@angular/build']);

// Temporary triage for one development-only advisory. Unknown data fails closed.
export function checkAudit(report, lock, now = new Date()) {
  if (report.auditReportVersion !== 2 || report.error || !report.vulnerabilities ||
      typeof report.vulnerabilities !== 'object' || Array.isArray(report.vulnerabilities)) {
    throw new Error('Invalid npm audit report');
  }
  const vulnerabilities = report.vulnerabilities;
  function accepted(name, visiting = new Set()) {
    const finding = vulnerabilities[name];
    if (!(now < new Date(expires)) || !testPackages.has(name) || visiting.has(name) ||
        finding?.name !== name || !Array.isArray(finding.nodes) || !finding.nodes.length ||
        !Array.isArray(finding.via) || !finding.via.length) return false;
    if (!finding.nodes.every(node => lock.packages?.[node]?.dev === true)) return false;
    const next = new Set([...visiting, name]);
    return finding.via.every(cause => {
      if (typeof cause === 'string') return accepted(cause, next);
      return name === 'braces' && cause?.name === 'braces' && cause.url === advisory &&
        finding.nodes.every(node => lock.packages[node].version === '3.0.3');
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
    console.log(`Affected test packages: ${allowed.join(', ')}`);
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
