import { Location } from '@angular/common';
import { PLATFORM_ID, RESPONSE_INIT, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { SeoService } from '../../core/services/seo.service';
import { LanguageService } from '../../core/services/language.service';
import { ShopRouteService } from './services/shop-route.service';
import { ShopProductDetail, ShopService } from './services/shop.service';
import { ProductDetailComponent } from './product-detail.component';

describe('ProductDetailComponent', () => {
  function buildProduct(
    overrides: Partial<ShopProductDetail> = {},
  ): ShopProductDetail {
    return {
      id: '91823f84-1111-2222-3333-444444444444',
      slug: 'bike-wall-hanger',
      name: 'Bike Wall-Hanger',
      excerpt: 'Wall mount for bicycles',
      description: '<p>Wall mount for bicycles</p>',
      seoTitle: null,
      seoDescription: null,
      ogTitle: null,
      ogDescription: null,
      indexable: true,
      isFeatured: false,
      sortOrder: 0,
      category: {
        id: 'category-1',
        slug: 'bike-accessories',
        name: 'Bike Accessories',
      },
      breadcrumbs: [],
      priceFromChf: 29.9,
      priceToChf: 29.9,
      defaultVariant: {
        id: 'variant-1',
        sku: 'BW-1',
        variantLabel: 'PLA',
        colorName: 'Black',
        colorLabel: 'Black',
        colorHex: '#111111',
        priceChf: 29.9,
        isDefault: true,
      },
      variants: [
        {
          id: 'variant-1',
          sku: 'BW-1',
          variantLabel: 'PLA',
          colorName: 'Black',
          colorLabel: 'Black',
          colorHex: '#111111',
          priceChf: 29.9,
          isDefault: true,
        },
      ],
      primaryImage: null,
      images: [],
      model3d: null,
      publicPath: '91823f84-bike-wall-hanger',
      localizedPaths: {
        it: '/it/shop/p/91823f84-supporto-bici-muro',
        en: '/en/shop/p/91823f84-bike-wall-hanger',
        de: '/de/shop/p/91823f84-bike-wall-hanger',
        fr: '/fr/shop/p/91823f84-support-mural-velo',
      },
      ...overrides,
    };
  }

  function createComponent(routerUrl = '/de/shop/p/91823f84-bike-wall-hanger') {
    const responseInit: { status?: number } = {};
    const seoService = jasmine.createSpyObj<SeoService>('SeoService', [
      'applyResolvedSeo',
      'applyPageSeo',
    ]);
    const translate = jasmine.createSpyObj<TranslateService>(
      'TranslateService',
      ['instant'],
    );
    translate.instant.and.callFake((key: string) => {
      const translations: Record<string, string> = {
        'SHOP.TITLE': 'Technische Lösungen',
        'SHOP.CATALOG_META_DESCRIPTION':
          'Entdecken Sie technische 3D-Druck-Lösungen.',
        'SEO.ROUTES.SHOP.PRODUCT_TITLE': 'Produkt | 3D fab',
        'SEO.ROUTES.SHOP.PRODUCT_DESCRIPTION':
          'Entdecken Sie Details, Materialien, Varianten und Verfügbarkeit.',
      };
      return translations[key] ?? key;
    });

    const currentLang = signal<'it' | 'en' | 'de' | 'fr'>('de');
    const languageService = {
      currentLang,
      selectedLang: () => currentLang(),
      setLocalizedRouteOverrides: jasmine.createSpy('setLocalizedRouteOverrides'),
      clearLocalizedRouteOverrides: jasmine.createSpy(
        'clearLocalizedRouteOverrides',
      ),
    };

    const shopService = {
      cartLoaded: signal(false),
      cartLoading: signal(false),
      getProductByPublicPath: jasmine
        .createSpy('getProductByPublicPath')
        .and.returnValue(of(buildProduct())),
      quantityForVariant: jasmine
        .createSpy('quantityForVariant')
        .and.returnValue(0),
      loadCart: jasmine.createSpy('loadCart').and.returnValue(of(null)),
      resolveMediaUrl: jasmine.createSpy('resolveMediaUrl').and.returnValue(null),
    };

    const router = {
      url: routerUrl,
      navigate: jasmine.createSpy('navigate'),
      navigateByUrl: jasmine.createSpy('navigateByUrl'),
      parseUrl: jasmine.createSpy('parseUrl'),
      createUrlTree: jasmine.createSpy('createUrlTree'),
      serializeUrl: jasmine.createSpy('serializeUrl'),
    } as unknown as Router;

    const activatedRoute = {
      paramMap: of(convertToParamMap({ productSlug: '91823f84-bike-wall-hanger' })),
      snapshot: {
        paramMap: convertToParamMap({ productSlug: '91823f84-bike-wall-hanger' }),
      },
    } as unknown as ActivatedRoute;

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [ProductDetailComponent],
      providers: [
        { provide: SeoService, useValue: seoService },
        { provide: TranslateService, useValue: translate },
        { provide: LanguageService, useValue: languageService },
        { provide: ShopService, useValue: shopService },
        {
          provide: ShopRouteService,
          useValue: jasmine.createSpyObj<ShopRouteService>('ShopRouteService', [
            'shopRootCommands',
            'productPathSegment',
            'isCatalogUrl',
          ]),
        },
        { provide: Router, useValue: router },
        { provide: ActivatedRoute, useValue: activatedRoute },
        {
          provide: Location,
          useValue: jasmine.createSpyObj<Location>('Location', ['back']),
        },
        { provide: RESPONSE_INIT, useValue: responseInit },
        { provide: PLATFORM_ID, useValue: 'server' },
      ],
    });

    const fixture: ComponentFixture<ProductDetailComponent> =
      TestBed.createComponent(ProductDetailComponent);

    return {
      component: fixture.componentInstance,
      seoService,
      responseInit,
    };
  }

  it('applies index follow SEO for indexable products', () => {
    const { component, seoService } = createComponent();

    (component as any).applySeo(buildProduct());

    expect(seoService.applyResolvedSeo).toHaveBeenCalledWith(
      jasmine.objectContaining({
        title: 'Bike Wall-Hanger | 3D fab',
        robots: 'index, follow',
        canonicalPath: '/de/shop/p/91823f84-bike-wall-hanger',
        alternates: buildProduct().localizedPaths,
        xDefault: '/it/shop/p/91823f84-supporto-bici-muro',
      }),
    );
  });

  it('applies noindex for products explicitly marked as non-indexable', () => {
    const { component, seoService } = createComponent();

    (component as any).applySeo(buildProduct({ indexable: false }));

    expect(seoService.applyResolvedSeo).toHaveBeenCalledWith(
      jasmine.objectContaining({
        robots: 'noindex, nofollow',
      }),
    );
  });

  it('builds a soft SSR fallback with 200 + index follow', () => {
    const { component, seoService, responseInit } = createComponent();

    expect((component as any).shouldUseSoftSeoFallback({ status: 500 })).toBeTrue();
    (component as any).setResponseStatus(200);
    (component as any).applySoftFallbackSeo('91823f84-bike-wall-hanger');

    expect(responseInit.status).toBe(200);
    expect(seoService.applyResolvedSeo).toHaveBeenCalledWith(
      jasmine.objectContaining({
        title: 'Bike Wall Hanger | 3D fab',
        description:
          'Entdecken Sie Details, Materialien, Varianten und Verfügbarkeit.',
        robots: 'index, follow',
        canonicalPath: '/de/shop/p/91823f84-bike-wall-hanger',
        alternates: null,
        xDefault: null,
      }),
    );
  });

  it('keeps hard fallback noindex for missing products', () => {
    const { component, seoService, responseInit } = createComponent();

    expect((component as any).shouldUseSoftSeoFallback({ status: 404 })).toBeFalse();
    (component as any).setResponseStatus(404);
    (component as any).applyHardFallbackSeo();

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
