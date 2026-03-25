import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { REQUEST } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { serverOriginInterceptor } from './server-origin.interceptor';

type TestGlobal = typeof globalThis & {
  __SSR_INTERNAL_API_ORIGIN__?: string;
};

describe('serverOriginInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  const testGlobal = globalThis as TestGlobal;
  const originalInternalApiOrigin = testGlobal.__SSR_INTERNAL_API_ORIGIN__;

  beforeEach(() => {
    delete testGlobal.__SSR_INTERNAL_API_ORIGIN__;

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([serverOriginInterceptor])),
        provideHttpClientTesting(),
        {
          provide: REQUEST,
          useValue: {
            protocol: 'https',
            url: '/de/shop/p/91823f84-bike-wall-hanger',
            headers: {
              host: 'dev.3d-fab.ch',
              authorization: 'Basic dGVzdDp0ZXN0',
              cookie: 'session=abc123',
              'accept-language': 'de-CH,de;q=0.9,en;q=0.8',
            },
          },
        },
      ],
    });

    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    if (originalInternalApiOrigin) {
      testGlobal.__SSR_INTERNAL_API_ORIGIN__ = originalInternalApiOrigin;
      return;
    }
    delete testGlobal.__SSR_INTERNAL_API_ORIGIN__;
  });

  it('rewrites relative SSR URLs to the incoming origin and forwards auth headers', () => {
    http.get('/api/shop/products/by-path/example?lang=de').subscribe();

    const request = httpMock.expectOne(
      'https://dev.3d-fab.ch/api/shop/products/by-path/example?lang=de',
    );
    expect(request.request.headers.get('authorization')).toBe(
      'Basic dGVzdDp0ZXN0',
    );
    expect(request.request.headers.get('cookie')).toBe('session=abc123');
    expect(request.request.headers.get('accept-language')).toBe(
      'de-CH,de;q=0.9,en;q=0.8',
    );
    request.flush({});
  });

  it('does not overwrite explicit request headers', () => {
    http
      .get('/api/shop/products', {
        headers: {
          authorization: 'Bearer explicit-token',
        },
      })
      .subscribe();

    const request = httpMock.expectOne(
      'https://dev.3d-fab.ch/api/shop/products',
    );
    expect(request.request.headers.get('authorization')).toBe(
      'Bearer explicit-token',
    );
    expect(request.request.headers.get('cookie')).toBe('session=abc123');
    request.flush({});
  });

  it('uses the internal SSR API origin for public shop discovery calls', () => {
    testGlobal.__SSR_INTERNAL_API_ORIGIN__ = 'http://backend:8000/';

    http.get('/api/shop/products/by-id-prefix/91823f84?lang=de').subscribe();

    const request = httpMock.expectOne(
      'http://backend:8000/api/shop/products/by-id-prefix/91823f84?lang=de',
    );
    expect(request.request.headers.get('authorization')).toBe(
      'Basic dGVzdDp0ZXN0',
    );
    expect(request.request.headers.get('cookie')).toBe('session=abc123');
    expect(request.request.headers.get('accept-language')).toBe(
      'de-CH,de;q=0.9,en;q=0.8',
    );
    request.flush({});
  });

  it('bypasses the public origin even when the proxy strips authorization on shop SSR requests', () => {
    testGlobal.__SSR_INTERNAL_API_ORIGIN__ = 'http://backend:8000/';

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([serverOriginInterceptor])),
        provideHttpClientTesting(),
        {
          provide: REQUEST,
          useValue: {
            protocol: 'https',
            url: '/de/shop/p/91823f84-bike-wall-hanger',
            headers: {
              host: 'dev.3d-fab.ch',
              cookie: 'session=abc123',
              'accept-language': 'de-CH,de;q=0.9,en;q=0.8',
            },
          },
        },
      ],
    });

    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);

    http.get('/api/shop/products/by-id-prefix/91823f84?lang=de').subscribe();

    const request = httpMock.expectOne(
      'http://backend:8000/api/shop/products/by-id-prefix/91823f84?lang=de',
    );
    expect(request.request.headers.get('authorization')).toBeNull();
    expect(request.request.headers.get('cookie')).toBe('session=abc123');
    expect(request.request.headers.get('accept-language')).toBe(
      'de-CH,de;q=0.9,en;q=0.8',
    );
    request.flush({});
  });

  it('keeps transactional shop API calls on the public origin', () => {
    testGlobal.__SSR_INTERNAL_API_ORIGIN__ = 'http://backend:8000/';

    http.get('/api/shop/cart').subscribe();

    const request = httpMock.expectOne('https://dev.3d-fab.ch/api/shop/cart');
    expect(request.request.headers.get('authorization')).toBe(
      'Basic dGVzdDp0ZXN0',
    );
    request.flush({});
  });

  it('keeps non-shop pages on the public origin even for public shop APIs', () => {
    testGlobal.__SSR_INTERNAL_API_ORIGIN__ = 'http://backend:8000/';

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([serverOriginInterceptor])),
        provideHttpClientTesting(),
        {
          provide: REQUEST,
          useValue: {
            protocol: 'https',
            url: '/de/checkout?session=abc',
            headers: {
              host: 'dev.3d-fab.ch',
              cookie: 'session=abc123',
              'accept-language': 'de-CH,de;q=0.9,en;q=0.8',
            },
          },
        },
      ],
    });

    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);

    http.get('/api/shop/products/by-id-prefix/91823f84?lang=de').subscribe();

    const request = httpMock.expectOne(
      'https://dev.3d-fab.ch/api/shop/products/by-id-prefix/91823f84?lang=de',
    );
    expect(request.request.headers.get('authorization')).toBeNull();
    expect(request.request.headers.get('cookie')).toBe('session=abc123');
    request.flush({});
  });
});
