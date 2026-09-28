import { expect, test } from '@playwright/test';

test('RENDER-008: the proxy serves public media and denies private storage', async ({ request }) => {
  const publicResponse = await request.get('/media/e2e-public-marker.txt');
  expect(publicResponse.status()).toBe(200);
  expect(await publicResponse.text()).toBe('public E2E media\n');

  const privateResponse = await request.get('/media/e2e-private-marker.txt');
  expect(privateResponse.status()).toBe(404);
});
