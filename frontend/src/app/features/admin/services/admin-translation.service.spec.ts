import { TestBed } from '@angular/core/testing';
import {
  HttpClientTestingModule,
  HttpTestingController,
} from '@angular/common/http/testing';
import {
  AdminTranslateLocalizedTextPayload,
  AdminTranslationService,
} from './admin-translation.service';

describe('AdminTranslationService', () => {
  let service: AdminTranslationService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [AdminTranslationService],
    });

    service = TestBed.inject(AdminTranslationService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('posts localized text translation requests with credentials', () => {
    const payload: AdminTranslateLocalizedTextPayload = {
      context: 'Home media HOME_SECTION / shop-gallery',
      sourceLanguage: 'it',
      overwriteExisting: false,
      fields: {
        title: {
          required: true,
          values: {
            it: 'Gallery shop',
            en: '',
            de: '',
            fr: '',
          },
        },
        altText: {
          required: true,
          values: {
            it: 'Prodotti stampati in 3D',
            en: '',
            de: '',
            fr: '',
          },
        },
      },
    };

    service.translateLocalizedText(payload).subscribe((response) => {
      expect(response.targetLanguages).toEqual(['en', 'de', 'fr']);
      expect(response.fields['title']?.en).toBe('Shop gallery');
    });

    const request = httpMock.expectOne(
      'http://localhost:8000/api/admin/translations/localized-text',
    );
    expect(request.request.method).toBe('POST');
    expect(request.request.withCredentials).toBeTrue();
    expect(request.request.body).toEqual(payload);

    request.flush({
      sourceLanguage: 'it',
      targetLanguages: ['en', 'de', 'fr'],
      fields: {
        title: {
          en: 'Shop gallery',
        },
      },
    });
  });
});
