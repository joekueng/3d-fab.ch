import { ActivatedRouteSnapshot, Router } from '@angular/router';
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
  }): {
    meta: jasmine.SpyObj<Meta>;
    title: jasmine.SpyObj<Title>;
  } {
    const events$ = new Subject<unknown>();
    const title = jasmine.createSpyObj<Title>('Title', ['setTitle']);
    const meta = jasmine.createSpyObj<Meta>('Meta', ['updateTag']);
    const translate = {
      instant: (key: string) => options.translations[key] ?? key,
    } as TranslateService;
    const router = {
      url: options.url,
      events: events$.asObservable(),
      routerState: {
        snapshot: {
          root: createSnapshot(options.data),
        },
      },
    } as unknown as Router;

    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    const service = new SeoService(router, title, meta, translate, document);

    return { meta, title };
  }

  beforeEach(() => {
    cleanupSeoDom();
  });

  afterEach(() => {
    cleanupSeoDom();
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
});
