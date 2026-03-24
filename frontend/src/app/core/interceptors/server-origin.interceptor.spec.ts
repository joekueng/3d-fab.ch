import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { REQUEST } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { serverOriginInterceptor } from './server-origin.interceptor';

describe('serverOriginInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([serverOriginInterceptor])),
        provideHttpClientTesting(),
        {
          provide: REQUEST,
          useValue: {
            protocol: 'https',
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
});
