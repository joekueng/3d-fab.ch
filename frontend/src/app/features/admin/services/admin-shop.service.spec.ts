import { TestBed } from '@angular/core/testing';
import {
  HttpClientTestingModule,
  HttpTestingController,
} from '@angular/common/http/testing';
import {
  AdminShopService,
  AdminTranslateShopProductPayload,
} from './admin-shop.service';

describe('AdminShopService', () => {
  let service: AdminShopService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [AdminShopService],
    });

    service = TestBed.inject(AdminShopService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('posts product translation requests with credentials', () => {
    const payload: AdminTranslateShopProductPayload = {
      categoryId: 'category-1',
      sourceLanguage: 'it',
      overwriteExisting: false,
      materialCodes: ['PLA', 'PETG'],
      names: {
        it: 'Supporto cavo scrivania',
        en: '',
        de: '',
        fr: '',
      },
      excerpts: {
        it: 'Accessorio tecnico',
        en: '',
        de: '',
        fr: '',
      },
      descriptions: {
        it: '<p>Descrizione prodotto</p>',
        en: '',
        de: '',
        fr: '',
      },
      seoTitles: {
        it: 'Supporto cavo scrivania | 3D fab',
        en: '',
        de: '',
        fr: '',
      },
      seoDescriptions: {
        it: 'Supporto tecnico stampato in 3D per scrivania.',
        en: '',
        de: '',
        fr: '',
      },
    };

    service.translateProduct(payload).subscribe((response) => {
      expect(response.targetLanguages).toEqual(['en', 'de', 'fr']);
      expect(response.names.en).toBe('Desk cable clip');
    });

    const request = httpMock.expectOne(
      'http://localhost:8000/api/admin/shop/products/translate',
    );
    expect(request.request.method).toBe('POST');
    expect(request.request.withCredentials).toBeTrue();
    expect(request.request.body).toEqual(payload);

    request.flush({
      sourceLanguage: 'it',
      targetLanguages: ['en', 'de', 'fr'],
      names: {
        en: 'Desk cable clip',
        de: 'Schreibtisch-Kabelhalter',
        fr: 'Support de cable de bureau',
      },
      excerpts: {},
      descriptions: {},
      seoTitles: {},
      seoDescriptions: {},
    });
  });
});
