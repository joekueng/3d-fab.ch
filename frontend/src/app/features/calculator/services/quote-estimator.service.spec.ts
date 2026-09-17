import { OrderInformationService } from '../../order-information/order-information.service';
import { TestBed, fakeAsync, flushMicrotasks } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { environment } from '../../../../environments/environment';
import {
  QuoteCalculationFailure,
  QuoteEstimatorService,
  QuoteRequest,
} from './quote-estimator.service';

describe('QuoteEstimatorService', () => {
  let service: QuoteEstimatorService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: OrderInformationService,
          useValue: {
            saveDraft: () => Promise.resolve({ id: 'draft', token: 'key' }),
            draftCredential: () => ({ id: 'draft', token: 'key' }),
          },
        },
      ],
    });
    service = TestBed.inject(QuoteEstimatorService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('preserves a rate-limit failure when session creation returns 429', fakeAsync(() => {
    const request: QuoteRequest = {
      items: [
        {
          file: new File(['mesh'], 'part-a.stl', { type: 'model/stl' }),
          quantity: 1,
        },
      ],
      material: 'PLA',
      quality: 'standard',
      mode: 'easy',
    };
    let failure: QuoteCalculationFailure | undefined;

    service.calculate(request).subscribe({
      error: (error: QuoteCalculationFailure) => {
        failure = error;
      },
    });

    flushMicrotasks();
    httpTesting.expectOne(`${environment.apiUrl}/api/quote-sessions`).flush(
      {
        status: 429,
        error: 'Too Many Requests',
        path: '/api/quote-sessions',
      },
      { status: 429, statusText: 'Too Many Requests' },
    );

    expect(failure).toEqual(
      jasmine.objectContaining({
        fileName: 'part-a.stl',
        status: 429,
        code: 'QUOTE_RATE_LIMITED',
      }),
    );
  }));
});
