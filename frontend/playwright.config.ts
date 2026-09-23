import { defineConfig, devices } from '@playwright/test';

const baseURL = process.env['E2E_BASE_URL'] ?? 'http://localhost:4200';
const target = new URL(baseURL);
const isDev = target.origin === 'https://dev.3d-fab.ch';
const isLocal =
  target.protocol === 'http:' &&
  ['127.0.0.1', 'localhost'].includes(target.hostname) &&
  !target.username &&
  !target.password;

if (!isDev && !isLocal) {
  throw new Error(
    'E2E_BASE_URL must be https://dev.3d-fab.ch or local HTTP loopback',
  );
}

const suite = process.env['E2E_SUITE'] ?? 'smoke';
if (!['smoke', 'fullstack', 'ui-states'].includes(suite)) {
  throw new Error(`Unknown E2E_SUITE: ${suite}`);
}
if (isDev && suite !== 'smoke') {
  throw new Error('Only the read-only smoke suite may target deployed dev');
}
const httpUser = process.env['E2E_HTTP_USER'];
const httpPassword = process.env['E2E_HTTP_PASSWORD'];
if (isDev && (!httpUser || !httpPassword)) {
  throw new Error('Deployed dev requires E2E_HTTP_USER and E2E_HTTP_PASSWORD for HTTP Basic Auth');
}

const testMatch =
  suite === 'smoke'
    ? '**/smoke/**/*.spec.ts'
    : suite === 'ui-states'
      ? '**/ui-states/**/*.spec.ts'
      : '**/*.spec.ts';

const deviceProjects = {
  chromium: devices['Desktop Chrome'],
  firefox: devices['Desktop Firefox'],
  webkit: devices['Desktop Safari'],
  'mobile-chromium': devices['Pixel 7'],
  'mobile-webkit': devices['iPhone 13'],
} as const;
const requestedProjects = (process.env['E2E_PROJECTS'] ?? 'chromium').split(',');
for (const project of requestedProjects) {
  if (!(project in deviceProjects)) throw new Error(`Unknown E2E project: ${project}`);
}
if (isDev && requestedProjects.some((project) => project !== 'chromium')) {
  throw new Error('Deployed-dev smoke is restricted to Chromium');
}

export default defineConfig({
  testDir: './e2e',
  testMatch,
  testIgnore: suite === 'fullstack' ? ['**/ui-states/**', '**/dev-smoke.spec.ts'] : undefined,
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env['CI'],
  retries: process.env['CI'] ? 1 : 0,
  projects: requestedProjects.map((name) => ({
    name,
    use: deviceProjects[name as keyof typeof deviceProjects],
  })),
  reporter: [
    ['list'],
    ['html', { open: 'never' }],
    ['json', { outputFile: 'test-results/results.json' }],
  ],
  use: {
    baseURL,
    httpCredentials: isDev ? { username: httpUser!, password: httpPassword! } : undefined,
    headless: true,
    launchOptions: { slowMo: Number(process.env['E2E_SLOWMO'] ?? 0) },
    video: 'retain-on-failure',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
});
