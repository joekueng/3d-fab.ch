import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import {
  HttpClientTestingModule,
  HttpTestingController,
} from '@angular/common/http/testing';
import {
  ShopCartResponse,
  ShopProductDetail,
  ShopService,
} from './shop.service';
import { LanguageService } from '../../../core/services/language.service';

describe('ShopService', () => {
  let service: ShopService;
  let httpMock: HttpTestingController;
  const currentLang = signal<'it' | 'en' | 'de' | 'fr'>('it');
  const languageService = {
    currentLang,
    selectedLang: jasmine.createSpy('selectedLang').and.returnValue('it'),
  };

  const buildCart = (): ShopCartResponse => ({
    session: {
      id: 'session-1',
      status: 'ACTIVE',
      sessionType: 'SHOP_CART',
    },
    items: [
      {
        id: 'line-1',
        lineItemType: 'SHOP_PRODUCT',
        originalFilename: 'desk-cable-clip.stl',
        displayName: 'Desk Cable Clip',
        quantity: 2,
        printTimeSeconds: null,
        materialGrams: null,
        colorCode: 'Coral Red',
        filamentVariantId: null,
        shopProductId: 'product-1',
        shopProductVariantId: 'variant-red',
        shopProductSlug: 'desk-cable-clip',
        shopProductName: 'Desk Cable Clip',
        shopVariantLabel: 'Coral Red',
        shopVariantColorName: 'Coral Red',
        shopVariantColorHex: '#ff6b6b',
        materialCode: 'PLA',
        quality: null,
        nozzleDiameterMm: null,
        layerHeightMm: null,
        infillPercent: null,
        infillPattern: null,
        supportsEnabled: false,
        status: 'READY',
        convertedStoredPath: '/storage/items/desk-cable-clip.stl',
        unitPriceChf: 11.4,
      },
      {
        id: 'line-2',
        lineItemType: 'SHOP_PRODUCT',
        originalFilename: 'desk-cable-clip.stl',
        displayName: 'Desk Cable Clip',
        quantity: 1,
        printTimeSeconds: null,
        materialGrams: null,
        colorCode: 'Sand Beige',
        filamentVariantId: null,
        shopProductId: 'product-1',
        shopProductVariantId: 'variant-sand',
        shopProductSlug: 'desk-cable-clip',
        shopProductName: 'Desk Cable Clip',
        shopVariantLabel: 'Sand Beige',
        shopVariantColorName: 'Sand Beige',
        shopVariantColorHex: '#d8c3a5',
        materialCode: 'PLA',
        quality: null,
        nozzleDiameterMm: null,
        layerHeightMm: null,
        infillPercent: null,
        infillPattern: null,
        supportsEnabled: false,
        status: 'READY',
        convertedStoredPath: '/storage/items/desk-cable-clip.stl',
        unitPriceChf: 12.0,
      },
    ],
    printItemsTotalChf: 34.8,
    cadTotalChf: 0,
    itemsTotalChf: 34.8,
    baseSetupCostChf: 0,
    nozzleChangeCostChf: 0,
    setupCostChf: 0,
    shippingCostChf: 2,
    globalMachineCostChf: 0,
    grandTotalChf: 36.8,
  });

  const buildProduct = (): ShopProductDetail => ({
    id: '12345678-abcd-4abc-9abc-1234567890ab',
    slug: 'desk-cable-clip',
    name: 'Supporto cavo scrivania',
    excerpt: 'Accessorio tecnico',
    description: 'Descrizione prodotto',
    seoTitle: null,
    seoDescription: null,
    ogTitle: null,
    ogDescription: null,
    indexable: true,
    isFeatured: true,
    sortOrder: 0,
    category: {
      id: 'category-1',
      slug: 'accessori',
      name: 'Accessori',
    },
    breadcrumbs: [],
    priceFromChf: 9.9,
    priceToChf: 12.5,
    defaultVariant: null,
    variants: [],
    primaryImage: null,
    images: [],
    model3d: null,
    publicPath: '12345678-supporto-cavo-scrivania',
    localizedPaths: {
      it: '/it/shop/p/12345678-supporto-cavo-scrivania',
      en: '/en/shop/p/12345678-desk-cable-clip',
      de: '/de/shop/p/12345678-schreibtisch-kabelhalter',
      fr: '/fr/shop/p/12345678-support-cable-bureau',
    },
  });

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        ShopService,
        {
          provide: LanguageService,
          useValue: languageService,
        },
      ],
    });

    currentLang.set('it');
    languageService.selectedLang.and.returnValue('it');

    service = TestBed.inject(ShopService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('loads the server-side cart and updates quantity indexes', () => {
    let response: ShopCartResponse | undefined;
    service.loadCart().subscribe((cart) => {
      response = cart;
    });

    const request = httpMock.expectOne('http://localhost:8000/api/shop/cart');
    expect(request.request.method).toBe('GET');
    expect(request.request.withCredentials).toBeTrue();
    request.flush(buildCart());

    expect(response?.grandTotalChf).toBe(36.8);
    expect(service.cartLoaded()).toBeTrue();
    expect(service.cartItemCount()).toBe(3);
    expect(service.quantityForProduct('product-1')).toBe(3);
    expect(service.quantityForVariant('variant-red')).toBe(2);
    expect(service.quantityForVariant('variant-sand')).toBe(1);
  });

  it('posts add-to-cart with credentials and replaces local cart state', () => {
    service.addToCart('variant-red', 2).subscribe();

    const request = httpMock.expectOne(
      'http://localhost:8000/api/shop/cart/items',
    );
    expect(request.request.method).toBe('POST');
    expect(request.request.withCredentials).toBeTrue();
    expect(request.request.body).toEqual({
      shopProductVariantId: 'variant-red',
      quantity: 2,
    });
    request.flush(buildCart());

    expect(service.cart()?.session?.id).toBe('session-1');
    expect(service.cartItemCount()).toBe(3);
  });

  it('resolves product detail from the public product slug', () => {
    let response: ShopProductDetail | undefined;

    service
      .getProductByPublicPath('12345678-supporto-cavo-scrivania')
      .subscribe((product) => {
        response = product;
      });

    const request = httpMock.expectOne((request) => {
      return (
        request.method === 'GET' &&
        request.url ===
          'http://localhost:8000/api/shop/products/by-id-prefix/12345678' &&
        request.params.get('lang') === 'it'
      );
    });
    request.flush(buildProduct());

    expect(response?.id).toBe('12345678-abcd-4abc-9abc-1234567890ab');
    expect(response?.name).toBe('Supporto cavo scrivania');
  });

  it('resolves products from the stable uuid prefix even if the slug tail is stale', () => {
    let response: ShopProductDetail | undefined;

    service.getProductByPublicPath('12345678-qualunque-nome').subscribe({
      next: (product) => {
        response = product;
      },
      error: () => fail('Expected stale slug tails to resolve from the uuid prefix'),
    });

    const request = httpMock.expectOne((request) => {
      return (
        request.method === 'GET' &&
        request.url ===
          'http://localhost:8000/api/shop/products/by-id-prefix/12345678' &&
        request.params.get('lang') === 'it'
      );
    });
    request.flush(buildProduct());

    expect(response?.id).toBe('12345678-abcd-4abc-9abc-1234567890ab');
  });

  it('resolves bare uuid product paths through the stable uuid prefix endpoint', () => {
    let response: ShopProductDetail | undefined;

    service.getProductByPublicPath('12345678').subscribe({
      next: (product) => {
        response = product;
      },
      error: () => fail('Expected bare uuid path to resolve from the uuid prefix'),
    });

    const request = httpMock.expectOne((request) => {
      return (
        request.method === 'GET' &&
        request.url ===
          'http://localhost:8000/api/shop/products/by-id-prefix/12345678' &&
        request.params.get('lang') === 'it'
      );
    });
    request.flush(buildProduct());

    expect(response?.publicPath).toBe('12345678-supporto-cavo-scrivania');
  });

  it('uses the route language for public shop lookups when translate.currentLang lags behind', () => {
    let response: ShopProductDetail | undefined;

    currentLang.set('de');
    languageService.selectedLang.and.returnValue('en');

    service
      .getProductByPublicPath('12345678-schreibtisch-kabelhalter')
      .subscribe((product) => {
        response = product;
      });

    const request = httpMock.expectOne((request) => {
      return (
        request.method === 'GET' &&
        request.url ===
          'http://localhost:8000/api/shop/products/by-id-prefix/12345678' &&
        request.params.get('lang') === 'de'
      );
    });
    request.flush(buildProduct());

    expect(response?.id).toBe('12345678-abcd-4abc-9abc-1234567890ab');
  });
});
