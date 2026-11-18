import assert from 'node:assert/strict';
import { test } from 'node:test';
import { checkAudit } from './check-frontend-audit.mjs';

function fixture() {
  return {
    report: {
      auditReportVersion: 2,
      vulnerabilities: {
        picomatch: { name: 'picomatch', nodes: ['node_modules/picomatch'], via: [
          { name: 'picomatch', url: 'https://github.com/advisories/GHSA-fc5q-39xc-vjpf' },
        ] },
        karma: { name: 'karma', nodes: ['node_modules/karma'], via: ['picomatch'] },
      },
    },
    lock: { packages: {
      'node_modules/picomatch': { version: '4.0.7', dev: true },
      'node_modules/karma': { version: '6.4.4', dev: true },
    } },
  };
}
const today = new Date('2026-11-18T00:00:00Z');

test('allows the specific development-only advisory and its dependent development package', () => {
  const { report, lock } = fixture();
  assert.deepEqual(checkAudit(report, lock, today), { allowed: ['picomatch', 'karma'], blocked: [] });
});

test('blocks a new advisory even when the existing advisory is also present', () => {
  const { report, lock } = fixture();
  report.vulnerabilities.picomatch.via.push({ name: 'picomatch', url: 'https://github.com/advisories/GHSA-new' });
  assert.deepEqual(checkAudit(report, lock, today).blocked, ['picomatch', 'karma']);
});

test('blocks runtime dependencies and dependencies missing from the lockfile', () => {
  for (const entry of [{ version: '4.0.7', dev: false }, undefined]) {
    const { report, lock } = fixture();
    lock.packages['node_modules/picomatch'] = entry;
    assert.deepEqual(checkAudit(report, lock, today).blocked, ['picomatch', 'karma']);
  }
  const { report, lock } = fixture();
  lock.packages['node_modules/karma'].dev = false;
  assert.deepEqual(checkAudit(report, lock, today).blocked, ['karma']);
});

test('blocks changed package versions and use outside the reviewed development packages', () => {
  const { report, lock } = fixture();
  lock.packages['node_modules/picomatch'].version = '4.0.8';
  assert.deepEqual(checkAudit(report, lock, today).blocked, ['picomatch', 'karma']);
  const changedDependent = fixture();
  changedDependent.lock.packages['node_modules/karma'].version = '6.4.5';
  assert.deepEqual(checkAudit(changedDependent.report, changedDependent.lock, today).blocked, ['karma']);
  const other = fixture();
  other.report.vulnerabilities.unrelated = { name: 'unrelated', nodes: ['node_modules/karma'], via: ['picomatch'] };
  assert.deepEqual(checkAudit(other.report, other.lock, today).blocked, ['unrelated']);
});

test('blocks unreviewed installations of the same package', () => {
  const { report, lock } = fixture();
  const node = 'node_modules/other/node_modules/picomatch';
  lock.packages[node] = { version: '4.0.7', dev: true };
  report.vulnerabilities.picomatch.nodes.push(node);
  assert.deepEqual(checkAudit(report, lock, today).blocked, ['picomatch', 'karma']);
});

test('the exception expires at the specified UTC time', () => {
  const { report, lock } = fixture();
  assert.deepEqual(checkAudit(report, lock, new Date('2026-11-25T00:00:00Z')).blocked, ['picomatch', 'karma']);
});

test('blocks missing causes, cycles, and malformed or failed audit responses', () => {
  const { report, lock } = fixture();
  report.vulnerabilities.picomatch.via = ['karma'];
  assert.deepEqual(checkAudit(report, lock, today).blocked, ['picomatch', 'karma']);
  report.vulnerabilities.picomatch.via = ['missing'];
  assert.deepEqual(checkAudit(report, lock, today).blocked, ['picomatch', 'karma']);
  for (const invalid of [{}, { ...report, error: { code: 'NETWORK' } }, { ...report, vulnerabilities: [] }]) {
    assert.throws(() => checkAudit(invalid, lock, today), /Invalid npm audit report/);
  }
});

test('passes a clean audit without requiring an exception', () => {
  assert.deepEqual(checkAudit({ auditReportVersion: 2, vulnerabilities: {} }, { packages: {} },
    new Date('2026-12-01T00:00:00Z')), { allowed: [], blocked: [] });
});
