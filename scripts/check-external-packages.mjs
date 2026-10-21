// Metadata only: no package installation, lifecycle scripts or Gradle execution.
import { readFileSync } from 'node:fs';
import { isDeepStrictEqual } from 'node:util';

const root = new URL('../', import.meta.url);
const read = (path) => readFileSync(new URL(path, root), 'utf8');
const manifest = JSON.parse(read('frontend/package.json'));
const lock = JSON.parse(read('frontend/package-lock.json'));
const checks = [];
const failures = [];
const check = (label, run) => checks.push({ label, run });
const requireThat = (condition, message) => {
  if (!condition) throw new Error(message);
};
async function request(url) {
  const response = await fetch(url, { signal: AbortSignal.timeout(15000) });
  requireThat(response.ok, `${url}: HTTP ${response.status}`);
  return response;
}

for (const section of ['dependencies', 'devDependencies', 'optionalDependencies']) {
  requireThat(isDeepStrictEqual(manifest[section] ?? {}, lock.packages?.['']?.[section] ?? {}),
    `${section}: package.json and lockfile disagree; regenerate with npm, never hand-edit integrity`);
  for (const name of Object.keys(manifest[section] ?? {})) {
    check(`npm ${name}`, async () => {
      const entry = lock.packages[`node_modules/${name}`];
      requireThat(entry?.version && entry.integrity && entry.resolved, 'Missing locked version/origin/integrity');
      const metadata = await (await request(`https://registry.npmjs.org/${encodeURIComponent(name)}/${encodeURIComponent(entry.version)}`)).json();
      requireThat(metadata.name === name && metadata.version === entry.version, 'Registry identity mismatch');
      requireThat(metadata.dist?.integrity === entry.integrity, 'Integrity differs from registry metadata');
      requireThat(metadata.dist?.tarball === entry.resolved, 'Tarball URL differs from registry metadata');
      requireThat(!metadata.deprecated, `Deprecated: ${metadata.deprecated}`);
      return entry.version;
    });
  }
}

// Deliberately scoped to literal, versioned Maven coordinates in this build file.
// BOM-managed versions, classifiers and transitives still require Gradle resolution.
const build = read('backend/build.gradle').replace(/\/\*[\s\S]*?\*\//g, '').replace(/^\s*\/\/.*$/gm, '');
const coordinates = new Set([...build.matchAll(/['"]([\w.-]+:[\w.-]+:[\w.-]+)['"]/g)].map((match) => match[1]));
requireThat(coordinates.size > 0, 'No literal Maven coordinates found; review checker coverage');
for (const coordinate of coordinates) {
  check(`maven ${coordinate}`, async () => {
    const [group, artifact, version] = coordinate.split(':');
    const base = `https://repo.maven.apache.org/maven2/${group.replaceAll('.', '/')}/${artifact}`;
    const pom = await (await request(`${base}/${version}/${artifact}-${version}.pom`)).text();
    requireThat(pom.includes('<project'), 'Expected Maven POM');
    const metadata = await (await request(`${base}/maven-metadata.xml`)).text();
    return `published; release=${metadata.match(/<release>([^<]+)<\/release>/)?.[1] ?? 'unspecified'}`;
  });
}
for (const match of build.matchAll(/id\s+['"]([^'"]+)['"]\s+version\s+['"]([^'"]+)['"]/g)) {
  const [, id, version] = match;
  check(`gradle plugin ${id}:${version}`, async () => {
    const marker = `${id}.gradle.plugin`;
    const pom = await (await request(`https://plugins.gradle.org/m2/${id.replaceAll('.', '/')}/${marker}/${version}/${marker}-${version}.pom`)).text();
    requireThat(pom.includes('<project'), 'Expected Gradle plugin marker POM');
    return 'published';
  });
}

// STEP conversion uses a separate, fully pinned Python environment.
const pythonRequirements = read('backend/step-requirements.txt')
  .replace(/\\\r?\n\s*/g, ' ')
  .split(/\r?\n/)
  .map((line) => line.trim())
  .filter((line) => line && !line.startsWith('#'))
  .map((line) => {
    const match = line.match(/^([\w.-]+)==([\w.+-]+)((?:\s+--hash=sha256:[a-f0-9]{64})+)$/);
    requireThat(match, `Unsupported or unpinned Python requirement: ${line}`);
    return { name: match[1], version: match[2], hashes: [...match[3].matchAll(/sha256:([a-f0-9]{64})/g)].map((hash) => hash[1]) };
  });
requireThat(pythonRequirements.length > 0, 'No pinned STEP converter requirements found');
const normalizePythonName = (name) => name.toLowerCase().replace(/[_.-]+/g, '-');
const pythonVersions = new Map(pythonRequirements.map(({ name, version }) => [normalizePythonName(name), version]));
for (const { name, version, hashes } of pythonRequirements) {
  check(`pypi ${name}:${version}`, async () => {
    const metadata = await (await request(`https://pypi.org/pypi/${encodeURIComponent(name)}/${encodeURIComponent(version)}/json`)).json();
    requireThat(normalizePythonName(metadata.info?.name ?? '') === normalizePythonName(name)
      && metadata.info?.version === version, 'PyPI identity mismatch');
    const published = new Set(metadata.urls.filter((file) => !file.yanked && file.filename.endsWith('.whl')).map((file) => file.digests?.sha256));
    requireThat(hashes.every((hash) => published.has(hash)), 'Locked wheel hash differs from PyPI metadata');
    for (const dependency of metadata.info.requires_dist ?? []) {
      const pin = dependency.match(/^([\w.-]+)==([\w.+-]+)$/);
      requireThat(pin && pythonVersions.get(normalizePythonName(pin[1])) === pin[2],
        `Python dependency is not locked: ${dependency}`);
    }
    return `verified wheel hashes; Python ${metadata.info.requires_python ?? 'unspecified'}`;
  });
}

// Limit requests to avoid hammering registries; collect every failure.
for (let offset = 0; offset < checks.length; offset += 4) {
  const batch = checks.slice(offset, offset + 4);
  const results = await Promise.allSettled(batch.map(({ run }) => run()));
  results.forEach((result, index) => {
    const { label } = batch[index];
    if (result.status === 'fulfilled') console.log(`PASS ${label}: ${result.value}`);
    else {
      failures.push(label);
      console.error(`FAIL ${label}: ${result.reason.message}`);
    }
  });
}
console.log(`${checks.length - failures.length}/${checks.length} metadata checks passed.`);
console.log('Scope: direct locked npm packages, literal versioned Maven coordinates, versioned Gradle plugins, pinned STEP Python wheels.');
console.log('Publication is not proof of trusted ownership, API compatibility or absence of vulnerabilities.');
process.exitCode = failures.length ? 1 : 0;
