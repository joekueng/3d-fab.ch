import { resolvePublicRedirectTarget } from './server-routing';

describe('server routing redirects', () => {
  it('does not handle the root path because it is resolved separately', () => {
    expect(resolvePublicRedirectTarget('/')).toBeNull();
  });

  it('redirects unprefixed public pages to the default language', () => {
    expect(resolvePublicRedirectTarget('/about')).toBe('/it/about');
    expect(resolvePublicRedirectTarget('/about/')).toBe('/it/about');
  });

  it('redirects calculator paths directly to the canonical basic route', () => {
    expect(resolvePublicRedirectTarget('/calculator')).toBe(
      '/it/calculator/basic',
    );
    expect(resolvePublicRedirectTarget('/it/calculator')).toBe(
      '/it/calculator/basic',
    );
  });

  it('redirects legacy shop product aliases to the canonical product route', () => {
    expect(
      resolvePublicRedirectTarget('/shop/accessories/desk-cable-clip'),
    ).toBe('/it/shop/p/desk-cable-clip');
    expect(
      resolvePublicRedirectTarget('/de/shop/zubehor/schreibtisch-kabelhalter'),
    ).toBe('/de/shop/p/schreibtisch-kabelhalter');
  });

  it('drops unsupported language-like prefixes instead of nesting them', () => {
    expect(resolvePublicRedirectTarget('/es/about')).toBe('/it/about');
    expect(resolvePublicRedirectTarget('/de-CH/about')).toBe('/it/about');
  });

  it('normalizes supported language prefixes and trailing slashes', () => {
    expect(resolvePublicRedirectTarget('/DE/about')).toBe('/de/about');
    expect(resolvePublicRedirectTarget('/it/about/')).toBe('/it/about');
    expect(resolvePublicRedirectTarget('/fr')).toBeNull();
  });

  it('does not redirect static files and sitemap resources', () => {
    expect(resolvePublicRedirectTarget('/go/flyer')).toBeNull();
    expect(resolvePublicRedirectTarget('/go/flyer/')).toBeNull();
    expect(resolvePublicRedirectTarget('/assets/logo.svg')).toBeNull();
    expect(resolvePublicRedirectTarget('/robots.txt')).toBeNull();
    expect(resolvePublicRedirectTarget('/sitemap.xml')).toBeNull();
  });
});
