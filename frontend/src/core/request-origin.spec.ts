import { resolveRequestOrigin } from './request-origin';

describe('resolveRequestOrigin', () => {
  it('prefers forwarded host and protocol when present', () => {
    expect(
      resolveRequestOrigin({
        protocol: 'http',
        headers: {
          host: 'internal:4000',
          'x-forwarded-host': '3d-fab.ch',
          'x-forwarded-proto': 'https',
        },
      }),
    ).toBe('https://3d-fab.ch');
  });

  it('falls back to request protocol and host', () => {
    expect(
      resolveRequestOrigin({
        protocol: 'http',
        headers: {
          host: 'localhost:4000',
        },
      }),
    ).toBe('http://localhost:4000');
  });

  it('uses the first forwarded value when proxies append multiple entries', () => {
    expect(
      resolveRequestOrigin({
        protocol: 'http',
        headers: {
          host: 'internal:4000',
          'x-forwarded-host': '3d-fab.ch, proxy.local',
          'x-forwarded-proto': 'https, http',
        },
      }),
    ).toBe('https://3d-fab.ch');
  });
});
