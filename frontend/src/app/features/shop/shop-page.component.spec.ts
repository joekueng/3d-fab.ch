import { PLATFORM_ID, RESPONSE_INIT, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { SeoService } from '../../core/services/seo.service';
import { LanguageService } from '../../core/services/language.service';
import {
  ShopCategoryDetail,
  ShopCategoryTree,
  ShopProductCatalogResponse,
  ShopService,
} from './services/shop.service';
import { ShopRouteService } from './services/shop-route.service';
import { ShopPageComponent } from './shop-page.component';

describe('ShopPageComponent', () => {
  function buildCategory(
    overrides: Partial<ShopCategoryDetail> = {},
  ): ShopCategoryDetail {
    return {
      id: 'cat-1',
      slug: 'compatible-with-garmin',
      name: 'Compatible with Garmin',
      description: 'Accessories compatible with Garmin devices.',
      seoTitle: null,
      seoDescription: null,
      ogTitle: null,
      ogDescription: null,
      indexable: true,
      sortOrder: 0,
      productCount: 3,
      breadcrumbs: [],
      primaryImage: null,
      images: [],
      children: [],
      ...overrides,
    };
  }

  function buildCatalog(
    overrides: Partial<ShopProductCatalogResponse> = {},
  ): ShopProductCatalogResponse {
    return {
      categorySlug: null,
      featuredOnly: null,
      category: null,
      products: [],
      ...overrides,
    };
  }

  function createComponent(routerUrl = '/de/shop') {
    const responseInit: { status?: number } = {};
    const seoService = jasmine.createSpyObj<SeoService>('SeoService', [
      'applyResolvedSeo',
      'applyPageSeo',
    ]);
    const translate = jasmine.createSpyObj<TranslateService>(
      'TranslateService',
      ['instant'],
    );
    translate.instant.and.callFake((key: string, params?: { count?: number }) => {
      const translations: Record<string, string> = {
        'SHOP.TITLE': 'Technische Lösungen',
        'SHOP.SUBTITLE': 'Fertige Produkte, die praktische Probleme lösen',
        'SHOP.CATALOG_TITLE': 'Alle Produkte',
        'SHOP.CATALOG_LABEL': 'Katalog',
        'SHOP.SELECTED_CATEGORY': 'Ausgewählte Kategorie',
        'SHOP.CATALOG_META_DESCRIPTION':
          'Entdecken Sie 3D-gedruckte Produkte und technisches Zubehör.',
        'SEO.ROUTES.SHOP.CATEGORY_TITLE': 'Shop-Kategorie | 3D fab',
        'SEO.ROUTES.SHOP.CATEGORY_DESCRIPTION':
          'Entdecken Sie Produkte dieser Kategorie und technische Lösungen.',
      };
      if (key === 'SHOP.CATEGORY_META') {
        return `${params?.count ?? 0} Produkte in dieser Kategorie verfügbar`;
      }
      return translations[key] ?? key;
    });

    const currentLang = signal<'it' | 'en' | 'de' | 'fr'>('de');
    const languageService = {
      currentLang,
      selectedLang: () => currentLang(),
    };

    const shopService = {
      cart: signal(null),
      cartLoading: signal(false),
      cartLoaded: signal(false),
      cartItemCount: signal(0),
      cartSessionId: signal<string | null>(null),
      getCategories: jasmine
        .createSpy('getCategories')
        .and.returnValue(of([] as ShopCategoryTree[])),
      getProductCatalog: jasmine
        .createSpy('getProductCatalog')
        .and.returnValue(of(buildCatalog())),
      flattenCategoryTree: jasmine
        .createSpy('flattenCategoryTree')
        .and.returnValue([]),
      quantityForProduct: jasmine.createSpy('quantityForProduct').and.returnValue(0),
      loadCart: jasmine.createSpy('loadCart').and.returnValue(of(null)),
      clearCart: jasmine.createSpy('clearCart').and.returnValue(of(null)),
      removeCartItem: jasmine.createSpy('removeCartItem').and.returnValue(of(null)),
      updateCartItem: jasmine.createSpy('updateCartItem').and.returnValue(of(null)),
    };

    const router = {
      url: routerUrl,
      navigate: jasmine.createSpy('navigate'),
    } as unknown as Router;

    const activatedRoute = {
      paramMap: of(convertToParamMap({})),
      snapshot: {
        paramMap: convertToParamMap({}),
      },
    } as unknown as ActivatedRoute;

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [ShopPageComponent],
      providers: [
        { provide: SeoService, useValue: seoService },
        { provide: TranslateService, useValue: translate },
        { provide: LanguageService, useValue: languageService },
        { provide: ShopService, useValue: shopService },
        {
          provide: ShopRouteService,
          useValue: jasmine.createSpyObj<ShopRouteService>('ShopRouteService', [
            'shopRootCommands',
          ]),
        },
        { provide: Router, useValue: router },
        { provide: ActivatedRoute, useValue: activatedRoute },
        { provide: RESPONSE_INIT, useValue: responseInit },
        { provide: PLATFORM_ID, useValue: 'server' },
      ],
    });

    const fixture: ComponentFixture<ShopPageComponent> =
      TestBed.createComponent(ShopPageComponent);

    return {
      component: fixture.componentInstance,
      seoService,
      responseInit,
    };
  }

  it('keeps index follow on the public shop root', () => {
    const { component, seoService } = createComponent();

    (component as any).applyDefaultSeo();

    expect(seoService.applyPageSeo).toHaveBeenCalledWith(
      jasmine.objectContaining({
        title: 'Technische Lösungen | 3D fab',
        robots: 'index, follow',
      }),
    );
  });

  it('keeps noindex for categories explicitly marked as non-indexable', () => {
    const { component, seoService } = createComponent('/de/shop/compatible-with-garmin');

    (component as any).applySeo(buildCategory({ indexable: false }));

    expect(seoService.applyPageSeo).toHaveBeenCalledWith(
      jasmine.objectContaining({
        robots: 'noindex, nofollow',
      }),
    );
  });

  it('uses a soft SSR fallback for non-404 category load errors', () => {
    const { component, seoService, responseInit } = createComponent(
      '/de/shop/compatible-with-garmin',
    );

    expect((component as any).shouldUseSoftSeoFallback({ status: 500 })).toBeTrue();
    (component as any).setResponseStatus(200);
    (component as any).applySoftFallbackSeo('compatible-with-garmin');

    expect(responseInit.status).toBe(200);
    expect(seoService.applyResolvedSeo).toHaveBeenCalledWith(
      jasmine.objectContaining({
        title: 'Compatible With Garmin | Technische Lösungen | 3D fab',
        description:
          'Entdecken Sie Produkte dieser Kategorie und technische Lösungen.',
        robots: 'index, follow',
        canonicalPath: '/de/shop/compatible-with-garmin',
        alternates: null,
        xDefault: null,
      }),
    );
  });

  it('keeps hard 404 noindex behavior for missing categories', () => {
    const { component, seoService, responseInit } = createComponent(
      '/de/shop/compatible-with-garmin',
    );

    expect((component as any).shouldUseSoftSeoFallback({ status: 404 })).toBeFalse();
    (component as any).setResponseStatus(404);
    (component as any).applyHardErrorSeo();

    expect(responseInit.status).toBe(404);
    expect(seoService.applyResolvedSeo).toHaveBeenCalledWith(
      jasmine.objectContaining({
        robots: 'noindex, nofollow',
        alternates: null,
        xDefault: null,
      }),
    );
  });
});
