import { TestBed } from '@angular/core/testing';
import {
  HttpClientTestingModule,
  HttpTestingController,
} from '@angular/common/http/testing';
import { ShopCartResponse, ShopService } from './shop.service';
import { LanguageService } from '../../../core/services/language.service';

describe('ShopService', () => {
  let service: ShopService;
  let httpMock: HttpTestingController;

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

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        ShopService,
        {
          provide: LanguageService,
          useValue: {
            selectedLang: () => 'it',
          },
        },
      ],
    });

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

    const request = httpMock.expectOne('http://localhost:8000/api/shop/cart/items');
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
});
