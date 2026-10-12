import assert from 'node:assert/strict';
import { test } from 'node:test';
import { checkAudit } from './check-frontend-audit.mjs';

function fixture() {
  return {
    report: {
      auditReportVersion: 2,
      vulnerabilities: {
        braces: { name: 'braces', nodes: ['node_modules/braces'], via: [
          { name: 'braces', url: 'https://github.com/advisories/GHSA-vfj7-8cjw-p6xm' },
        ] },
        karma: { name: 'karma', nodes: ['node_modules/karma'], via: ['braces'] },
      },
    },
    lock: { packages: {
      'node_modules/braces': { version: '3.0.3', dev: true },
      'node_modules/karma': { version: '6.4.4', dev: true },
    } },
  };
}
const today = new Date('2026-10-05T00:00:00Z');

test('allows the specific development-only advisory and its dependent test package', () => {
  const { report, lock } = fixture();
  assert.deepEqual(checkAudit(report, lock, today), { allowed: ['braces', 'karma'], blocked: [] });
});

test('blocks a new advisory even when the existing advisory is also present', () => {
  const { report, lock } = fixture();
  report.vulnerabilities.braces.via.push({ name: 'braces', url: 'https://github.com/advisories/GHSA-new' });
  assert.deepEqual(checkAudit(report, lock, today).blocked, ['braces', 'karma']);
});

test('blocks runtime dependencies and dependencies missing from the lockfile', () => {
  for (const entry of [{ version: '3.0.3', dev: false }, undefined]) {
    const { report, lock } = fixture();
    lock.packages['node_modules/braces'] = entry;
    assert.deepEqual(checkAudit(report, lock, today).blocked, ['braces', 'karma']);
  }
  const { report, lock } = fixture();
  lock.packages['node_modules/karma'].dev = false;
  assert.deepEqual(checkAudit(report, lock, today).blocked, ['karma']);
});

test('blocks changed package versions and use outside the reviewed test packages', () => {
  const { report, lock } = fixture();
  lock.packages['node_modules/braces'].version = '3.0.4';
  assert.deepEqual(checkAudit(report, lock, today).blocked, ['braces', 'karma']);
  const other = fixture();
  other.report.vulnerabilities.unrelated = { name: 'unrelated', nodes: ['node_modules/karma'], via: ['braces'] };
  assert.deepEqual(checkAudit(other.report, other.lock, today).blocked, ['unrelated']);
});

test('the exception expires at the specified UTC time', () => {
  const { report, lock } = fixture();
  assert.deepEqual(checkAudit(report, lock, new Date('2026-11-04T00:00:00Z')).blocked, ['braces', 'karma']);
});

test('blocks missing causes, cycles, and malformed or failed audit responses', () => {
  const { report, lock } = fixture();
  report.vulnerabilities.braces.via = ['karma'];
  assert.deepEqual(checkAudit(report, lock, today).blocked, ['braces', 'karma']);
  report.vulnerabilities.braces.via = ['missing'];
  assert.deepEqual(checkAudit(report, lock, today).blocked, ['braces', 'karma']);
  for (const invalid of [{}, { ...report, error: { code: 'NETWORK' } }, { ...report, vulnerabilities: [] }]) {
    assert.throws(() => checkAudit(invalid, lock, today), /Invalid npm audit report/);
  }
});

test('passes a clean audit without requiring an exception', () => {
  assert.deepEqual(checkAudit({ auditReportVersion: 2, vulnerabilities: {} }, { packages: {} },
    new Date('2026-12-01T00:00:00Z')), { allowed: [], blocked: [] });
});
