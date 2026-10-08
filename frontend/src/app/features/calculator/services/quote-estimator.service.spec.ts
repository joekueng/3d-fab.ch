import { OrderInformationService } from '../../order-information/order-information.service';
import { TestBed, fakeAsync, flushMicrotasks } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { of } from 'rxjs';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { environment } from '../../../../environments/environment';
import {
  QuoteCalculationFailure,
  QuoteEstimatorService,
  QuoteRequest,
  QuoteResult,
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

  it('requests reuse of the current session and preserves a 429 response', fakeAsync(() => {
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

    service.calculate(request, 'session-1').subscribe({
      error: (error: QuoteCalculationFailure) => {
        failure = error;
      },
    });

    flushMicrotasks();
    const sessionRequest = httpTesting.expectOne(
      `${environment.apiUrl}/api/quote-sessions`,
    );
    expect(sessionRequest.request.body).toEqual({
      information: { id: 'draft', token: 'key' },
      reuseSessionId: 'session-1',
      itemCount: 1,
    });
    sessionRequest.flush(
      {
        status: 429,
        error: 'Too Many Requests',
        path: '/api/quote-sessions',
      },
      {
        status: 429,
        statusText: 'Too Many Requests',
        headers: { 'Retry-After': '37' },
      },
    );

    expect(failure).toEqual(
      jasmine.objectContaining({
        fileName: 'part-a.stl',
        status: 429,
        code: 'QUOTE_RATE_LIMITED',
        retryAfterSeconds: 37,
      }),
    );
  }));

  const twoFiles = (): QuoteRequest => ({
    items: ['part-a.stl', 'part-b.stl'].map((name) => ({
      file: new File(['mesh'], name, { type: 'model/stl' }), quantity: 1,
    })),
    material: 'PLA', quality: 'standard', mode: 'easy',
  });

  it('reserves the whole batch and processes files sequentially with its permit', fakeAsync(() => {
    const request = twoFiles();
    let result: QuoteResult | undefined;
    spyOn(service, 'getQuoteSession').and.returnValue(of({
      session: { id: 'session-1' }, items: [{ status: 'READY', originalFilename: 'part-a.stl' }],
    }));
    service.calculate(request, 'session-1').subscribe((event) => {
      if (typeof event !== 'number') result = event;
    });
    flushMicrotasks();
    const init = httpTesting.expectOne(`${environment.apiUrl}/api/quote-sessions`);
    expect(init.request.body.itemCount).toBe(2);
    init.flush({ id: 'session-1', calculationId: 'permit-1' });
    const uploads = `${environment.apiUrl}/api/quote-sessions/session-1/line-items`;
    const first = httpTesting.expectOne(uploads);
    expect(first.request.headers.get('X-Quote-Calculation')).toBe('permit-1');
    expect(((first.request.body as FormData).get('file') as File).name).toBe('part-a.stl');
    httpTesting.expectNone(uploads);
    first.flush({ status: 'READY' });
    const second = httpTesting.expectOne(uploads);
    expect(second.request.headers.get('X-Quote-Calculation')).toBe('permit-1');
    expect(((second.request.body as FormData).get('file') as File).name).toBe('part-b.stl');
    second.flush({ status: 'READY' });
    expect(result?.sessionId).toBe('session-1');
    expect(result?.failedItems).toEqual([]);
  }));

  it('continues after an individual failure and preserves successful files', fakeAsync(() => {
    let result: QuoteResult | undefined;
    spyOn(service, 'getQuoteSession').and.returnValue(of({
      session: { id: 'session-1' }, items: [{ status: 'READY', originalFilename: 'part-b.stl' }],
    }));
    service.calculate(twoFiles()).subscribe((event) => {
      if (typeof event !== 'number') result = event;
    });
    flushMicrotasks();
    httpTesting.expectOne(`${environment.apiUrl}/api/quote-sessions`)
      .flush({ id: 'session-1', calculationId: 'permit-1' });
    const uploads = `${environment.apiUrl}/api/quote-sessions/session-1/line-items`;
    httpTesting.expectOne(uploads).flush({ code: 'MODEL_PROCESSING_FAILED' }, {
      status: 422, statusText: 'Unprocessable Entity',
    });
    httpTesting.expectOne(uploads).flush({ status: 'READY' });
    expect(result?.failedItems?.length).toBe(1);
    expect(result?.failedItems?.[0].fileName).toBe('part-a.stl');
    expect(result?.items[0].fileName).toBe('part-b.stl');
  }));

  it('cancels the active upload and never starts queued files after unsubscribe', fakeAsync(() => {
    const subscription = service.calculate(twoFiles()).subscribe();
    flushMicrotasks();
    httpTesting.expectOne(`${environment.apiUrl}/api/quote-sessions`)
      .flush({ id: 'session-1', calculationId: 'permit-1' });
    const uploads = `${environment.apiUrl}/api/quote-sessions/session-1/line-items`;
    const first = httpTesting.expectOne(uploads);
    subscription.unsubscribe();
    expect(first.cancelled).toBeTrue();
    httpTesting.expectNone(uploads);
  }));

  it('maps official pricing inputs used for provisional quantity estimates', () => {
    const result = service.mapSessionToQuoteResult({
      session: { id: 'session-1', setupCostChf: 2 },
      items: [
        {
          id: 'line-1',
          originalFilename: 'part.stl',
          unitPriceChf: 1.52,
          baseUnitPriceChf: 0.8,
          printTimeSeconds: 3600,
          materialGrams: 20,
          quantity: 10,
        },
      ],
      machineHourTiers: [
        { startHours: 0, endHours: 10, costChfPerHour: 2 },
        { startHours: 10, endHours: null, costChfPerHour: 1 },
      ],
      grandTotalChf: 26.2,
    });

    expect(result.items[0].baseUnitPrice).toBe(0.8);
    expect(result.machineHourTiers).toEqual([
      { startHours: 0, endHours: 10, costChfPerHour: 2 },
      { startHours: 10, endHours: null, costChfPerHour: 1 },
    ]);
  });
});
