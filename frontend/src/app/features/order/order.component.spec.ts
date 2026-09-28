import {
  TestBed,
  fakeAsync,
  flushMicrotasks,
  tick,
} from '@angular/core/testing';
import { Component, input, PLATFORM_ID } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NEVER, Subject, of } from 'rxjs';
import { OrderEventsService, OrderStreamEvent } from './order-events.service';
import { OrderInformationComponent } from '../order-information/order-information.component';
import { InformationModel } from '../order-information/order-information.service';
import { OrderComponent } from './order.component';
import { OrderInformationService } from '../order-information/order-information.service';
import { QuoteEstimatorService } from '../calculator/services/quote-estimator.service';

describe('OrderComponent payment tracking', () => {
  let component: OrderComponent;
  let api: jasmine.SpyObj<QuoteEstimatorService>;
  let events: Subject<OrderStreamEvent>;
  const paid = { id: 'fixture', status: 'PAID', paymentStatus: 'RECEIVED' };
  beforeEach(() => {
    events = new Subject<OrderStreamEvent>();
    api = jasmine.createSpyObj<QuoteEstimatorService>('api', [
      'getOrder',
      'reportPayment',
    ]);
    TestBed.configureTestingModule({
      providers: [
        { provide: OrderEventsService, useValue: { watch: () => events } },
        { provide: PLATFORM_ID, useValue: 'browser' },
        { provide: ActivatedRoute, useValue: {} },
        { provide: Router, useValue: {} },
        {
          provide: TranslateService,
          useValue: { instant: (key: string) => key },
        },
        { provide: OrderInformationService, useValue: {} },
        { provide: QuoteEstimatorService, useValue: api },
      ],
    });
    spyOnProperty(document, 'hidden', 'get').and.returnValue(false);
    component = TestBed.runInInjectionContext(() => new OrderComponent());
    component.orderId = 'fixture';
  });
  afterEach(() => component.ngOnDestroy());

  it('refreshes on SSE and uses slow recovery polling while connected', fakeAsync(() => {
    api.getOrder.and.returnValue(
      of({ ...paid, status: 'PENDING_PAYMENT', paymentStatus: 'REPORTED' }),
    );
    component.loadOrder();
    events.next('connected');
    tick(10_000);
    expect(api.getOrder).toHaveBeenCalledTimes(1);
    api.getOrder.and.returnValue(of(paid));
    events.next('changed');
    expect(component.order()?.status).toBe('PAID');
    tick(60_000);
    expect(api.getOrder).toHaveBeenCalledTimes(3);
  }));

  it('falls back to fast polling when the stream disconnects', fakeAsync(() => {
    api.getOrder.and.returnValue(
      of({ ...paid, status: 'PENDING_PAYMENT', paymentStatus: 'REPORTED' }),
    );
    component.loadOrder();
    events.next('connected');
    events.next('disconnected');
    tick(1_000);
    expect(api.getOrder).toHaveBeenCalledTimes(2);
  }));

  it('rechecks when an event arrives during an older GET', () => {
    api.getOrder.and.returnValue(of(paid));
    component.loadOrder();
    const pending = new Subject<typeof paid>();
    api.getOrder.and.returnValue(pending);
    component.loadOrder();
    events.next('changed');
    api.getOrder.and.returnValue(of({ ...paid, status: 'SHIPPED' }));
    pending.next(paid);
    pending.complete();
    expect(component.order()?.status).toBe('SHIPPED');
  });

  it('closes the stream on hiding, terminal state and destruction', () => {
    api.getOrder.and.returnValue(of(paid));
    component.loadOrder();
    expect(events.observed).toBeTrue();
    (
      Object.getOwnPropertyDescriptor(document, 'hidden')?.get as jasmine.Spy
    ).and.returnValue(true);
    component.refreshWhenVisible();
    expect(events.observed).toBeFalse();
    (
      Object.getOwnPropertyDescriptor(document, 'hidden')?.get as jasmine.Spy
    ).and.returnValue(false);
    component.refreshWhenVisible();
    expect(events.observed).toBeTrue();
    api.getOrder.and.returnValue(of({ ...paid, status: 'COMPLETED' }));
    events.next('changed');
    expect(events.observed).toBeFalse();
    component.ngOnDestroy();
    expect(events.observed).toBeFalse();
  });

  it('distinguishes paid, production, completed and cancelled', () => {
    expect(component.trackingStep(paid)).toBe(2);
    expect(component.trackingStep({ ...paid, status: 'IN_PRODUCTION' })).toBe(
      3,
    );
    expect(component.trackingStep({ ...paid, status: 'COMPLETED' })).toBe(5);
    expect(component.trackingStep({ ...paid, status: 'CANCELLED' })).toBe(-1);
  });

  it('continues polling after reporting and slows down after confirmation', fakeAsync(() => {
    api.getOrder.and.returnValue(
      of({
        id: 'fixture',
        status: 'PENDING_PAYMENT',
        paymentStatus: 'REPORTED',
      }),
    );
    component.loadOrder();
    tick(1_000);
    expect(api.getOrder).toHaveBeenCalledTimes(2);
    api.getOrder.and.returnValue(of(paid));
    tick(1_000);
    expect(component.order()?.status).toBe('PAID');
    tick(59_999);
    expect(api.getOrder).toHaveBeenCalledTimes(3);
    api.getOrder.and.returnValue(of({ ...paid, status: 'IN_PRODUCTION' }));
    tick(1);
    expect(component.order()?.status).toBe('IN_PRODUCTION');
    component.ngOnDestroy();
  }));

  it('starts the animation immediately and opens a fresh fast window after a late report', fakeAsync(() => {
    component.order.set({
      id: 'fixture',
      status: 'PENDING_PAYMENT',
      paymentStatus: 'PENDING',
    });
    tick(11 * 60_000);
    const report = new Subject<typeof paid>();
    api.reportPayment.and.returnValue(report);
    api.getOrder.and.returnValue(of(paid));
    component.completeOrder();
    expect(component.isCheckingTwint()).toBeTrue();
    component.completeOrder();
    expect(api.reportPayment).toHaveBeenCalledTimes(1);
    report.next({
      ...paid,
      status: 'PENDING_PAYMENT',
      paymentStatus: 'REPORTED',
    });
    report.complete();
    tick(999);
    expect(api.getOrder).not.toHaveBeenCalled();
    tick(1);
    expect(component.order()?.status).toBe('PAID');
    expect(component.isCheckingTwint()).toBeFalse();
    component.ngOnDestroy();
  }));

  it('ends fast checking after ten minutes and keeps slower status refreshes', fakeAsync(() => {
    api.getOrder.and.returnValue(
      of({ ...paid, status: 'PENDING_PAYMENT', paymentStatus: 'REPORTED' }),
    );
    component.loadOrder();
    tick(10 * 60_000);
    expect(component.isCheckingTwint()).toBeFalse();
    const count = api.getOrder.calls.count();
    tick(59_999);
    expect(api.getOrder.calls.count()).toBe(count);
    tick(1);
    expect(api.getOrder.calls.count()).toBe(count + 1);
    component.ngOnDestroy();
  }));

  it('keeps bank transfer reports on the regular polling interval', fakeAsync(() => {
    component.selectPayment('bill');
    api.getOrder.and.returnValue(
      of({ ...paid, status: 'PENDING_PAYMENT', paymentStatus: 'REPORTED' }),
    );
    component.loadOrder();
    expect(component.isCheckingTwint()).toBeFalse();
    tick(9_999);
    expect(api.getOrder).toHaveBeenCalledTimes(1);
    tick(1);
    expect(api.getOrder).toHaveBeenCalledTimes(2);
    component.ngOnDestroy();
  }));

  it('ignores a GET response started before the report mutation', () => {
    const stale = new Subject<typeof paid>();
    api.getOrder.and.returnValue(stale);
    api.reportPayment.and.returnValue(of(paid));
    component.loadOrder();
    component.completeOrder();
    stale.next({
      ...paid,
      status: 'PENDING_PAYMENT',
      paymentStatus: 'PENDING',
    });
    stale.complete();
    expect(component.order()?.status).toBe('PAID');
  });

  it('keeps displayed data during a temporary network error', () => {
    component.order.set(paid);
    const request = new Subject<typeof paid>();
    api.getOrder.and.returnValue(request);
    component.loadOrder();
    request.error(new Error('offline'));
    expect(component.order()).toEqual(paid);
    expect(component.error()).toBeNull();
  });

  it('refreshes immediately when the page regains focus', () => {
    api.getOrder.and.returnValue(of(paid));
    component.refreshWhenVisible();
    expect(api.getOrder).toHaveBeenCalledTimes(1);
  });

  it('stops timers after destruction', fakeAsync(() => {
    api.getOrder.and.returnValue(of(paid));
    component.loadOrder();
    component.ngOnDestroy();
    tick(120_000);
    expect(api.getOrder).toHaveBeenCalledTimes(1);
  }));
});

@Component({
  selector: 'app-order-information',
  standalone: true,
  template: '',
})
class InformationStub {
  orderId = input<string | null>(null);
  models = input<InformationModel[]>([]);
}

describe('OrderComponent rendered timeline', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [OrderComponent, TranslateModule.forRoot()],
      providers: [
        { provide: OrderEventsService, useValue: { watch: () => NEVER } },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: { get: () => 'fixture' } } },
        },
        { provide: Router, useValue: {} },
        {
          provide: OrderInformationService,
          useValue: { resumeOrder: () => Promise.resolve() },
        },
        {
          provide: QuoteEstimatorService,
          useValue: {
            getOrder: () =>
              of({ id: 'fixture', status: 'PAID', paymentStatus: 'RECEIVED' }),
            getTwintPayment: () => of({}),
          },
        },
      ],
    });
    TestBed.overrideComponent(OrderComponent, {
      remove: { imports: [OrderInformationComponent] },
      add: { imports: [InformationStub] },
    });
    const translations = TestBed.inject(TranslateService);
    translations.setTranslation('it', {
      TRACKING: {
        STEP_PENDING: 'In attesa di pagamento',
        STEP_REPORTED: 'In verifica',
        STEP_PAID: 'Pagato',
        STEP_PRODUCTION: 'In produzione',
        STEP_SHIPPED: 'Spedito',
        PAID_DESCRIPTION: 'Pagamento confermato. In attesa di produzione.',
        STATUS_CANCELLED: 'Ordine annullato',
      },
    });
    translations.use('it');
  });

  it('renders a busy TWINT button and removes it on confirmation', fakeAsync(() => {
    const fixture = TestBed.createComponent(OrderComponent);
    fixture.detectChanges();
    flushMicrotasks();
    fixture.componentInstance.order.set({
      id: 'fixture',
      status: 'PENDING_PAYMENT',
      paymentStatus: 'REPORTED',
    });
    fixture.detectChanges();
    const root: HTMLElement = fixture.nativeElement;
    const button = root.querySelector<HTMLButtonElement>(
      'button[aria-busy="true"]',
    );
    expect(button?.disabled).toBeTrue();
    expect(button?.querySelector('.button-spinner')).not.toBeNull();
    expect(root.querySelector('p[role="status"]')).not.toBeNull();
    fixture.componentInstance.order.set({
      id: 'fixture',
      status: 'PAID',
      paymentStatus: 'RECEIVED',
    });
    fixture.detectChanges();
    expect(root.querySelector('.button-spinner')).toBeNull();
    expect(root.querySelector('button[aria-busy="true"]')).toBeNull();
    fixture.destroy();
  }));

  it('renders five steps with paid active, then production, and no progress for cancellation', fakeAsync(() => {
    const fixture = TestBed.createComponent(OrderComponent);
    fixture.componentInstance.loading.set(false);
    fixture.componentInstance.order.set({
      id: 'fixture',
      status: 'PAID',
      paymentStatus: 'RECEIVED',
    });
    fixture.detectChanges();
    flushMicrotasks();
    fixture.detectChanges();
    const root: HTMLElement = fixture.nativeElement;
    expect(root.querySelectorAll('.timeline-step').length).toBe(5);
    expect(root.querySelector('.timeline-step.active')?.textContent).toContain(
      'Pagato',
    );
    expect(root.textContent).toContain(
      'Pagamento confermato. In attesa di produzione.',
    );
    fixture.componentInstance.order.update((order) => ({
      ...order!,
      status: 'IN_PRODUCTION',
    }));
    fixture.detectChanges();
    expect(root.querySelector('.timeline-step.active')?.textContent).toContain(
      'In produzione',
    );
    fixture.componentInstance.order.update((order) => ({
      ...order!,
      status: 'CANCELLED',
    }));
    fixture.detectChanges();
    expect(root.querySelectorAll('.timeline-step').length).toBe(0);
    expect(root.textContent).toContain('Ordine annullato');
    fixture.destroy();
  }));
});
