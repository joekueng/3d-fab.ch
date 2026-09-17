import { ActivatedRouteSnapshot, NavigationEnd, Router } from '@angular/router';
import { Meta, Title } from '@angular/platform-browser';
import { Subject } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { SeoService } from './seo.service';

describe('SeoService', () => {
  function createSnapshot(
    data: Record<string, unknown>,
    firstChild: ActivatedRouteSnapshot | null = null,
  ): ActivatedRouteSnapshot {
    return {
      data,
      firstChild,
    } as unknown as ActivatedRouteSnapshot;
  }

  function cleanupSeoDom(): void {
    document.head
      .querySelectorAll(
        'link[rel="canonical"], link[rel="alternate"][data-seo-managed="true"], meta[property="og:locale:alternate"][data-seo-managed="true"]',
      )
      .forEach((node) => node.remove());
    document.documentElement.removeAttribute('lang');
  }

  function createService(options: {
    url: string;
    data: Record<string, unknown>;
    translations: Record<string, string>;
    navigated?: boolean;
  }): {
    service: SeoService;
    meta: jasmine.SpyObj<Meta>;
    title: jasmine.SpyObj<Title>;
    router: Router;
    events$: Subject<unknown>;
  } {
    const events$ = new Subject<unknown>();
    const title = jasmine.createSpyObj<Title>('Title', ['setTitle']);
    const meta = jasmine.createSpyObj<Meta>('Meta', ['updateTag']);
    const translate = {
      instant: (key: string) => options.translations[key] ?? key,
    } as TranslateService;
    const router = {
      url: options.url,
      navigated: options.navigated ?? true,
      events: events$.asObservable(),
      routerState: {
        snapshot: {
          root: createSnapshot(options.data),
        },
      },
    } as unknown as Router;

    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    const service = new SeoService(router, title, meta, translate, document);

    return { service, meta, title, router, events$ };
  }

  beforeEach(() => {
    cleanupSeoDom();
  });

  afterEach(() => {
    cleanupSeoDom();
  });

  it('preserves the SSR canonical while the initial router URL is still root', () => {
    const canonical = document.createElement('link');
    canonical.rel = 'canonical';
    canonical.href = `${document.location.origin}/it/terms`;
    document.head.appendChild(canonical);
    const { title, meta } = createService({
      url: '/', navigated: false, data: {}, translations: {},
    });
    expect(canonical.getAttribute('href')).toBe(`${document.location.origin}/it/terms`);
    expect(title.setTitle).not.toHaveBeenCalled();
    expect(meta.updateTag).not.toHaveBeenCalled();
  });

  it('does not invent a homepage canonical before initial client navigation', () => {
    createService({ url: '/', navigated: false, data: {}, translations: {} });
    expect(document.querySelector('link[rel="canonical"]')).toBeNull();
  });

  it('updates the canonical after initial navigation and language changes', () => {
    const { router, events$ } = createService({
      url: '/', navigated: false, data: {}, translations: {},
    });
    let navigationId = 0;
    for (const path of ['/it/terms', '/de/terms', '/fr/privacy', '/en/materials']) {
      Object.defineProperty(router, 'url', { value: `${path}?utm_source=test`, configurable: true });
      events$.next(new NavigationEnd(++navigationId, path, path));
      const canonical = document.querySelector('link[rel="canonical"]');
      expect(canonical?.getAttribute('href')).toBe(`${document.location.origin}${path}`);
      expect(document.querySelectorAll('link[rel="canonical"]').length).toBe(1);
    }
  });

  it('adds the language prefix to canonical and hreflang URLs', () => {
    const { meta, title } = createService({
      url: '/privacy?utm=test',
      data: {
        seoTitleKey: 'SEO.ROUTES.LEGAL.PRIVACY.TITLE',
        seoDescriptionKey: 'SEO.ROUTES.LEGAL.PRIVACY.DESCRIPTION',
      },
      translations: {
        'SEO.ROUTES.LEGAL.PRIVACY.TITLE': 'Privacy Policy | 3D fab',
        'SEO.ROUTES.LEGAL.PRIVACY.DESCRIPTION': 'Privacy description',
      },
    });

    expect(title.setTitle).toHaveBeenCalledWith('Privacy Policy | 3D fab');

    const canonical = document.head.querySelector(
      'link[rel="canonical"]',
    ) as HTMLLinkElement | null;
    expect(canonical?.getAttribute('href')).toBe(
      `${document.location.origin}/it/privacy`,
    );

    const alternates = Array.from(
      document.head.querySelectorAll(
        'link[rel="alternate"][data-seo-managed="true"]',
      ),
    ).map((node) => ({
      hreflang: node.getAttribute('hreflang'),
      href: node.getAttribute('href'),
    }));

    expect(alternates).toContain({
      hreflang: 'en-CH',
      href: `${document.location.origin}/en/privacy`,
    });
    expect(alternates).toContain({
      hreflang: 'x-default',
      href: `${document.location.origin}/it/privacy`,
    });
    expect(document.documentElement.lang).toBe('it-CH');

    const ogUrlCall = meta.updateTag.calls
      .allArgs()
      .find(([tag]) => tag.property === 'og:url');
    expect(ogUrlCall?.[0].content).toBe(
      `${document.location.origin}/it/privacy`,
    );

    const ogLocaleCall = meta.updateTag.calls
      .allArgs()
      .find(([tag]) => tag.property === 'og:locale');
    expect(ogLocaleCall?.[0].content).toBe('it_CH');
  });

  it('uses the locale-adaptive root as x-default for home pages', () => {
    createService({
      url: '/de',
      data: {
        seoTitleKey: 'SEO.ROUTES.HOME.TITLE',
        seoDescriptionKey: 'SEO.ROUTES.HOME.DESCRIPTION',
      },
      translations: {
        'SEO.ROUTES.HOME.TITLE': '3D-Druck in Zürich | 3D fab',
        'SEO.ROUTES.HOME.DESCRIPTION': '3D-Druckservice in Zürich',
      },
    });

    const alternates = Array.from(
      document.head.querySelectorAll(
        'link[rel="alternate"][data-seo-managed="true"]',
      ),
    ).map((node) => ({
      hreflang: node.getAttribute('hreflang'),
      href: node.getAttribute('href'),
    }));

    expect(alternates).toContain({
      hreflang: 'x-default',
      href: `${document.location.origin}/`,
    });
  });

  it('resolves translated route metadata for the active language', () => {
    const { meta, title } = createService({
      url: '/en/about',
      data: {
        seoTitleKey: 'SEO.ROUTES.ABOUT.TITLE',
        seoDescriptionKey: 'SEO.ROUTES.ABOUT.DESCRIPTION',
      },
      translations: {
        'SEO.ROUTES.ABOUT.TITLE': 'About Us | 3D fab',
        'SEO.ROUTES.ABOUT.DESCRIPTION': 'About description',
      },
    });

    expect(title.setTitle).toHaveBeenCalledWith('About Us | 3D fab');

    const descriptionCall = meta.updateTag.calls
      .allArgs()
      .find(([tag]) => tag.name === 'description');
    expect(descriptionCall?.[0].content).toBe('About description');
    expect(document.documentElement.lang).toBe('en-CH');
  });

  it('applies canonical and hreflang values resolved from localized paths', () => {
    const { service } = createService({
      url: '/it/shop/p/12345678-supporto-cavo-scrivania',
      data: {},
      translations: {},
    });

    service.applyResolvedSeo({
      title: 'Supporto cavo scrivania | 3D fab',
      description: 'Accessorio tecnico',
      robots: 'index, follow',
      ogTitle: 'Supporto cavo scrivania | 3D fab',
      ogDescription: 'Accessorio tecnico',
      canonicalPath: '/it/shop/p/12345678-supporto-cavo-scrivania',
      alternates: {
        it: '/it/shop/p/12345678-supporto-cavo-scrivania',
        en: '/en/shop/p/12345678-desk-cable-clip',
        de: '/de/shop/p/12345678-schreibtisch-kabelhalter',
      },
      xDefault: '/it/shop/p/12345678-supporto-cavo-scrivania',
    });

    const canonical = document.head.querySelector(
      'link[rel="canonical"]',
    ) as HTMLLinkElement | null;
    expect(canonical?.getAttribute('href')).toBe(
      `${document.location.origin}/it/shop/p/12345678-supporto-cavo-scrivania`,
    );

    const alternates = Array.from(
      document.head.querySelectorAll(
        'link[rel="alternate"][data-seo-managed="true"]',
      ),
    ).map((node) => ({
      hreflang: node.getAttribute('hreflang'),
      href: node.getAttribute('href'),
    }));

    expect(alternates).toContain({
      hreflang: 'de-CH',
      href: `${document.location.origin}/de/shop/p/12345678-schreibtisch-kabelhalter`,
    });
    expect(alternates).toContain({
      hreflang: 'x-default',
      href: `${document.location.origin}/it/shop/p/12345678-supporto-cavo-scrivania`,
    });
  });
});
