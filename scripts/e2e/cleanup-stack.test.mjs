import assert from 'node:assert/strict';
import {test} from 'node:test';
import {mkdtempSync, writeFileSync, readFileSync, mkdirSync, cpSync, rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join, resolve} from 'node:path';
import {spawnSync} from 'node:child_process';

const project = 'e2e-printcalc-e2e-abc123';
const other = 'e2e-printcalc-e2e-other1';
const script = resolve('scripts/e2e/cleanup-stack.sh');
const fakeDocker = `#!/usr/bin/env node
const fs = require('node:fs');
const a = process.argv.slice(2);
const path = process.env.FAKE_STATE;
const state = JSON.parse(fs.readFileSync(path));
state.calls.push(a);
const save = () => fs.writeFileSync(path, JSON.stringify(state));
save();
if (process.env.FAKE_LIST_FAIL && a.includes('ls')) process.exit(7);
if (a[0] === 'info') process.exit(0);
if (a[0] === 'compose') {
  if (a.includes('up')) {
    const project = a[a.indexOf('--project-name') + 1];
    state.resources.push({kind:'container', id:'partial-backend', project});
    save();
    if (process.env.FAKE_TERM) process.kill(process.ppid, 'SIGTERM');
    process.exit(42);
  }
  if (a.includes('down')) { console.error('simulated compose down failure'); process.exit(5); }
  process.exit(0);
}
const kind = a[0] === 'ps' || a[0] === 'rm' ? 'container' : a[0];
if (a[0] === 'ps' || a[1] === 'ls') {
  const filters = a.flatMap((x,i) => x === '--filter' ? [a[i+1]] : []);
  const selected = state.resources.filter(r => r.kind === kind && filters.every(f =>
    f === 'label=com.docker.compose.project' ||
    f === 'label=com.docker.compose.project=' + r.project ||
    (f === 'label=ch.3d-fab.e2e.browser=true' && r.browser)));
  for (const r of selected) console.log(a.includes('--format') ?
    (kind === 'container' ? r.project : 'com.docker.compose.project=' + r.project) : r.id);
  process.exit(0);
}
if (a[1] === 'inspect') process.exit(1);
if (a[0] === 'rm' || a[1] === 'rm') {
  const id = a.at(-1);
  if (id === process.env.FAKE_REMOVE_FAIL) process.exit(9);
  state.resources = state.resources.filter(r => !(r.kind === kind && r.id === id));
  save(); process.exit(0);
}
console.error('Unexpected fake Docker call', a); process.exit(99);
`;

function fixture(t) {
    const dir = mkdtempSync(join(tmpdir(), 'e2e-cleanup-test-'));
    t.after(() => rmSync(dir, {recursive: true, force: true}));
    writeFileSync(join(dir, 'docker'), fakeDocker, {mode: 0o755});
    const statePath = join(dir, 'state.json');
    const resources = [project, other, 'print-calculator-prod'].flatMap(p =>
        ['container', 'volume', 'network'].map(kind => ({kind, id: p + '-' + kind, project: p})));
    resources.push({kind: 'container', id: 'browser', project, browser: true});
    writeFileSync(statePath, JSON.stringify({resources, calls: []}));
    const env = {...process.env, PATH: dir + ':' + process.env.PATH, FAKE_STATE: statePath};
    return {
        dir, env, state: () => JSON.parse(readFileSync(statePath)),
        run: (...args) => spawnSync('bash', [script, ...args], {env, encoding: 'utf8'})
    };
}

test('preview lists only the selected project and makes no changes', t => {
    const f = fixture(t);
    const result = f.run('--project', project);
    assert.equal(result.status, 0, result.stderr);
    assert.ok(result.stdout.includes('browser'));
    assert.ok(!result.stdout.includes(other));
    assert.equal(f.state().resources.length, 10);
});
test('deletion removes browser first and preserves production and concurrent jobs; repeat is safe', t => {
    const f = fixture(t);
    assert.equal(f.run('--project', project, '--delete').status, 0);
    const state = f.state();
    assert.equal(state.resources.length, 6);
    assert.ok(state.resources.every(r => r.project !== project));
    assert.deepEqual(state.calls.find(a => a[0] === 'rm'), ['rm', '-f', 'browser']);
    assert.equal(f.run('--project', project, '--delete').status, 0);
});
test('invalid projects and deletion without selection are rejected before Docker access', t => {
    const f = fixture(t);
    for (const args of [['--project', 'print-calculator-prod', '--delete'], ['--delete'],
        ['--project', project + '*', '--delete'], ['--project', project, '--force']]) {
        assert.equal(f.run(...args).status, 2);
    }
    assert.equal(f.state().calls.length, 0);
});
test('discovery excludes production and includes partially removed stacks', t => {
    const f = fixture(t);
    const result = f.run('--list');
    assert.equal(result.status, 0, result.stderr);
    assert.deepEqual(result.stdout.trim().split('\n').sort(), [project, other].sort());
    assert.equal(f.state().resources.length, 10);
});
test('failed removal is reported while remaining cleanup is attempted', t => {
    const f = fixture(t);
    f.env.FAKE_REMOVE_FAIL = project + '-container';
    assert.equal(f.run('--project', project, '--delete').status, 1);
    assert.ok(f.state().resources.some(r => r.id === project + '-container'));
    assert.ok(f.state().calls.some(a => a[0] === 'network' && a[1] === 'rm'));
});
test('missing workflow manifest is harmless, discovery failure is not hidden', t => {
    const f = fixture(t);
    assert.equal(f.run('--project-file', join(f.dir, 'missing'), '--delete').status, 0);
    assert.equal(f.state().calls.length, 0);
    f.env.FAKE_LIST_FAIL = '1';
    assert.equal(f.run('--list').status, 7);
});
for (const terminated of [false, true]) test(`partial startup cleans up after ${terminated ? 'SIGTERM' : 'failure'} even if Compose down fails`, t => {
    const f = fixture(t);
    if (terminated) f.env.FAKE_TERM = '1';
    const root = join(f.dir, 'repo');
    mkdirSync(join(root, 'scripts/e2e'), {recursive: true});
    for (const file of ['run.sh', 'cleanup-stack.sh']) {
        cpSync(resolve('scripts/e2e', file), join(root, 'scripts/e2e', file));
    }
    const result = spawnSync('bash', [join(root, 'scripts/e2e/run.sh')], {env: f.env, encoding: 'utf8'});
    assert.equal(result.status, terminated ? 143 : 42, result.stderr);
    assert.match(result.stderr, /Compose cleanup failed/);
    const recorded = readFileSync(join(root, '.e2e-project'), 'utf8').trim();
    assert.match(recorded, /^e2e-printcalc-e2e-[a-z0-9]+$/);
    assert.ok(!f.state().resources.some(r => r.project === recorded));
    assert.equal(f.state().resources.length, 10);
});
