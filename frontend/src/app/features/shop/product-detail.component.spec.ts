import { DOCUMENT, Location } from '@angular/common';
import { PLATFORM_ID, RESPONSE_INIT, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
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
      images: [
        {
          mediaAssetId: 'image-1',
          title: null,
          altText: null,
          usageType: 'SHOP_PRODUCT',
          usageKey: 'product-1',
          sortOrder: 0,
          isPrimary: true,
          thumb: null,
          card: null,
          hero: {
            jpegUrl: '/media/product.jpg',
            avifUrl: null,
            webpUrl: null,
            pngUrl: null,
          },
        },
      ],
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

  function createComponent(
    routerUrl = '/de/shop/p/91823f84-bike-wall-hanger',
    options?: {
      currentLang?: 'it' | 'en' | 'de' | 'fr';
      selectedLang?: 'it' | 'en' | 'de' | 'fr';
      apiStatus?: number;
    },
  ) {
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

    const currentLang = signal<'it' | 'en' | 'de' | 'fr'>(
      options?.currentLang ?? 'de',
    );
    const languageService = {
      currentLang,
      selectedLang: () => options?.selectedLang ?? currentLang(),
      setLocalizedRouteOverrides: jasmine.createSpy(
        'setLocalizedRouteOverrides',
      ),
      clearLocalizedRouteOverrides: jasmine.createSpy(
        'clearLocalizedRouteOverrides',
      ),
    };

    const shopService = {
      cartLoaded: signal(false),
      cartLoading: signal(false),
      getProductByPublicPath: jasmine
        .createSpy('getProductByPublicPath')
        .and.returnValue(
          options?.apiStatus
            ? throwError(() => ({ status: options.apiStatus }))
            : of(buildProduct()),
        ),
      quantityForVariant: jasmine
        .createSpy('quantityForVariant')
        .and.returnValue(0),
      loadCart: jasmine.createSpy('loadCart').and.returnValue(of(null)),
      resolveMediaUrl: jasmine
        .createSpy('resolveMediaUrl')
        .and.callFake((media) => media?.jpegUrl ?? null),
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
      paramMap: of(
        convertToParamMap({ productSlug: '91823f84-bike-wall-hanger' }),
      ),
      snapshot: {
        paramMap: convertToParamMap({
          productSlug: '91823f84-bike-wall-hanger',
        }),
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

    TestBed.overrideComponent(ProductDetailComponent, {
      set: { template: '' },
    });
    const fixture: ComponentFixture<ProductDetailComponent> =
      TestBed.createComponent(ProductDetailComponent);

    return {
      component: fixture.componentInstance,
      seoService,
      responseInit,
      fixture,
      currentLang,
    };
  }

  function readStructuredData() {
    const document = TestBed.inject(DOCUMENT);
    return JSON.parse(
      document.getElementById('shop-product-jsonld')?.textContent ?? 'null',
    );
  }

  for (const lang of ['it', 'en', 'de', 'fr'] as const) {
    it(`renders the displayed offer with the ${lang} canonical URL on the server`, () => {
      const { fixture } = createComponent(undefined, { currentLang: lang });
      fixture.detectChanges();
      const data = readStructuredData();
      expect(data['@type']).toBe('Product');
      expect(data.url).toBe(
        new URL(buildProduct().localizedPaths[lang]!, document.location.origin)
          .href,
      );
      expect(data.image).toEqual([
        `${document.location.origin}/media/product.jpg`,
      ]);
      expect(data.offers).toEqual(
        jasmine.objectContaining({
          '@type': 'Offer',
          price: '29.90',
          priceCurrency: 'CHF',
          availability: 'https://schema.org/InStock',
          itemCondition: 'https://schema.org/NewCondition',
        }),
      );
      expect(data.description).toBe('Wall mount for bicycles');
      fixture.destroy();
      expect(readStructuredData()).toBeNull();
    });
  }

  it('updates the offer with the selected variant without duplicating scripts', () => {
    const { component, fixture } = createComponent();
    fixture.detectChanges();
    const product = buildProduct();
    const variant = {
      ...product.variants[0],
      id: 'variant-2',
      sku: 'BW-2',
      priceChf: 42.5,
      colorLabel: 'Red',
    };
    component.product.set({
      ...product,
      variants: [...product.variants, variant],
    });
    component.selectVariant(variant);
    fixture.detectChanges();
    const data = readStructuredData();
    expect(data.offers.price).toBe(component.priceLabel().toFixed(2));
    expect(data.sku).toBe('BW-2');
    expect(data.color).toBe('Red');
    expect(document.querySelectorAll('#shop-product-jsonld').length).toBe(1);
  });

  it('removes stale offers during loading and errors', () => {
    const { component, fixture } = createComponent();
    fixture.detectChanges();
    expect(readStructuredData()).not.toBeNull();
    component.loading.set(true);
    fixture.detectChanges();
    expect(readStructuredData()).toBeNull();
    component.loading.set(false);
    fixture.detectChanges();
    expect(readStructuredData()).not.toBeNull();
    component.error.set('SHOP.NOT_FOUND');
    fixture.detectChanges();
    expect(readStructuredData()).toBeNull();
  });

  it('omits non-indexable, imageless and non-purchasable products', () => {
    const { component, fixture } = createComponent();
    fixture.detectChanges();
    for (const product of [
      buildProduct({ indexable: false }),
      buildProduct({ images: [] }),
      buildProduct({ variants: [], defaultVariant: null }),
    ]) {
      component.product.set(product);
      fixture.detectChanges();
      expect(readStructuredData()).toBeNull();
    }
  });

  it('escapes product text so SSR serialization cannot close the JSON-LD script', () => {
    const { component, fixture } = createComponent();
    fixture.detectChanges();
    const name = '</script><script>alert(1)</script>';
    component.product.set(buildProduct({ name }));
    fixture.detectChanges();
    expect(readStructuredData().name).toBe(name);
    expect(
      document.getElementById('shop-product-jsonld')?.outerHTML,
    ).not.toContain(name);
  });

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

  it('uses the route language for canonical SEO even if the selected translation language lags', () => {
    const { component, seoService } = createComponent(undefined, {
      currentLang: 'de',
      selectedLang: 'en',
    });

    (component as any).applySeo(buildProduct());

    expect(seoService.applyResolvedSeo).toHaveBeenCalledWith(
      jasmine.objectContaining({
        canonicalPath: '/de/shop/p/91823f84-bike-wall-hanger',
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

  it('returns 503 and a visible error for a temporary backend failure', () => {
    const { component, fixture, seoService, responseInit } = createComponent(
      undefined,
      { apiStatus: 500 },
    );
    fixture.detectChanges();
    expect(responseInit.status).toBe(503);
    expect(component.error()).toBe('SHOP.LOAD_ERROR');
    expect(readStructuredData()).toBeNull();
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
    const { fixture, seoService, responseInit } = createComponent(undefined, {
      apiStatus: 404,
    });
    fixture.detectChanges();

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
