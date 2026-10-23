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
            useDraft: () => Promise.resolve(),
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

  it('sends each OBJ unit with its original file and the calculation permit', async () => {
    const file = new File(['v 0 0 0'], 'inches.obj');
    const subscription = service
      .calculate({
        items: [{ file, quantity: 1, modelUnit: 'in' }],
        material: 'PLA',
        quality: 'standard',
        mode: 'easy',
      })
      .subscribe();
    await Promise.resolve();
    await Promise.resolve();
    httpTesting
      .expectOne(`${environment.apiUrl}/api/quote-sessions`)
      .flush({ id: 'units-session', calculationId: 'units-permit' });
    const upload = httpTesting.expectOne(
      `${environment.apiUrl}/api/quote-sessions/units-session/line-items`,
    );
    const multipart = upload.request.body as FormData;
    expect(multipart.get('file')).toBe(file);
    expect(upload.request.headers.get('X-Quote-Calculation')).toBe(
      'units-permit',
    );
    const settings = JSON.parse(
      await (multipart.get('settings') as Blob).text(),
    ) as { modelUnit: string };
    expect(settings.modelUnit).toBe('in');
    subscription.unsubscribe();
    expect(upload.cancelled).toBeTrue();
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
      file: new File(['mesh'], name, { type: 'model/stl' }),
      quantity: 1,
    })),
    material: 'PLA',
    quality: 'standard',
    mode: 'easy',
  });

  it('reserves the whole batch and processes files sequentially with its permit', fakeAsync(() => {
    const request = twoFiles();
    let result: QuoteResult | undefined;
    spyOn(service, 'getQuoteSession').and.returnValue(
      of({
        session: { id: 'session-1' },
        items: [{ status: 'READY', originalFilename: 'part-a.stl' }],
      }),
    );
    service.calculate(request, 'session-1').subscribe((event) => {
      if (typeof event !== 'number') result = event;
    });
    flushMicrotasks();
    const init = httpTesting.expectOne(
      `${environment.apiUrl}/api/quote-sessions`,
    );
    expect(init.request.body.itemCount).toBe(2);
    init.flush({ id: 'session-1', calculationId: 'permit-1' });
    const uploads = `${environment.apiUrl}/api/quote-sessions/session-1/line-items`;
    const first = httpTesting.expectOne(uploads);
    expect(first.request.headers.get('X-Quote-Calculation')).toBe('permit-1');
    expect(((first.request.body as FormData).get('file') as File).name).toBe(
      'part-a.stl',
    );
    httpTesting.expectNone(uploads);
    first.flush({ status: 'READY' });
    const second = httpTesting.expectOne(uploads);
    expect(second.request.headers.get('X-Quote-Calculation')).toBe('permit-1');
    expect(((second.request.body as FormData).get('file') as File).name).toBe(
      'part-b.stl',
    );
    second.flush({ status: 'READY' });
    expect(result?.sessionId).toBe('session-1');
    expect(result?.failedItems).toEqual([]);
  }));

  it('continues after an individual failure and preserves successful files', fakeAsync(() => {
    let result: QuoteResult | undefined;
    spyOn(service, 'getQuoteSession').and.returnValue(
      of({
        session: { id: 'session-1' },
        items: [{ status: 'READY', originalFilename: 'part-b.stl' }],
      }),
    );
    service.calculate(twoFiles()).subscribe((event) => {
      if (typeof event !== 'number') result = event;
    });
    flushMicrotasks();
    httpTesting
      .expectOne(`${environment.apiUrl}/api/quote-sessions`)
      .flush({ id: 'session-1', calculationId: 'permit-1' });
    const uploads = `${environment.apiUrl}/api/quote-sessions/session-1/line-items`;
    httpTesting.expectOne(uploads).flush(
      { code: 'MODEL_PROCESSING_FAILED' },
      {
        status: 422,
        statusText: 'Unprocessable Entity',
      },
    );
    httpTesting.expectOne(uploads).flush({ status: 'READY' });
    expect(result?.failedItems?.length).toBe(1);
    expect(result?.failedItems?.[0].fileName).toBe('part-a.stl');
    expect(result?.items[0].fileName).toBe('part-b.stl');
  }));

  it('cancels the active upload and never starts queued files after unsubscribe', fakeAsync(() => {
    const subscription = service.calculate(twoFiles()).subscribe();
    flushMicrotasks();
    httpTesting
      .expectOne(`${environment.apiUrl}/api/quote-sessions`)
      .flush({ id: 'session-1', calculationId: 'permit-1' });
    const uploads = `${environment.apiUrl}/api/quote-sessions/session-1/line-items`;
    const first = httpTesting.expectOne(uploads);
    subscription.unsubscribe();
    expect(first.cancelled).toBeTrue();
    httpTesting.expectNone(uploads);
  }));

  for (const response of [
    { errorCode: 'MODEL_OUT_OF_PRINT_VOLUME' },
    { pricingBreakdown: { errorCode: 'MODEL_OUT_OF_PRINT_VOLUME' } },
  ]) {
    it(`preserves the oversized-model reason from ${Object.keys(response)[0]}`, fakeAsync(() => {
      let failure: QuoteCalculationFailure | undefined;
      const request = { ...twoFiles(), items: twoFiles().items.slice(0, 1) };
      service
        .calculate(request)
        .subscribe({ error: (error) => (failure = error) });
      flushMicrotasks();
      httpTesting
        .expectOne(`${environment.apiUrl}/api/quote-sessions`)
        .flush({ id: 'session-1', calculationId: 'permit-1' });
      httpTesting
        .expectOne(
          `${environment.apiUrl}/api/quote-sessions/session-1/line-items`,
        )
        .flush({ status: 'REVIEW_REQUIRED', ...response });
      expect(failure?.code).toBe('MODEL_OUT_OF_PRINT_VOLUME');
      expect(failure?.sessionId).toBe('session-1');
    }));
  }

  it('sends split consent to the backend and returns a priced split model', async () => {
    const request = {
      ...twoFiles(),
      items: twoFiles().items.slice(0, 1),
      acceptSplitPrinting: true,
    };
    let result: QuoteResult | undefined;
    service.calculate(request).subscribe((event) => {
      if (typeof event !== 'number') result = event;
    });
    await Promise.resolve();
    await Promise.resolve();
    httpTesting
      .expectOne(`${environment.apiUrl}/api/quote-sessions`)
      .flush({ id: 'session-1', calculationId: 'permit-1' });
    const upload = httpTesting.expectOne(
      `${environment.apiUrl}/api/quote-sessions/session-1/line-items`,
    );
    const settings = JSON.parse(
      await ((upload.request.body as FormData).get('settings') as Blob).text(),
    );
    expect(settings.allowSplitForOversized).toBeTrue();
    upload.flush({ status: 'READY', requiresSplitPrinting: true });
    httpTesting
      .expectOne(`${environment.apiUrl}/api/quote-sessions/session-1/resume`)
      .flush(null);
    httpTesting
      .expectOne(`${environment.apiUrl}/api/quote-sessions/session-1`)
      .flush({
        session: { id: 'session-1' },
        items: [
          {
            status: 'READY',
            originalFilename: 'part-a.stl',
            requiresSplitPrinting: true,
            unitPriceChf: 12,
            printTimeSeconds: 3600,
            materialGrams: 20,
            quantity: 1,
          },
        ],
        setupFeeChf: 12,
        grandTotalChf: 24,
      });
    await Promise.resolve();
    await Promise.resolve();
    expect(result?.items[0].requiresSplitPrinting).toBeTrue();
    expect(result?.totalPrice).toBe(24);
    expect(result?.failedItems).toEqual([]);
  });

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
