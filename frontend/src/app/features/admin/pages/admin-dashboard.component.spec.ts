import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, input } from '@angular/core';
import { StlViewerComponent } from '../../../shared/components/stl-viewer/stl-viewer.component';
import { provideTranslateService } from '@ngx-translate/core';
import { of, Subject } from 'rxjs';
import { AdminDashboardComponent } from './admin-dashboard.component';
import { LanguageService } from '../../../core/services/language.service';
import {
  AdminOrder,
  AdminOrdersService,
  ReviewRequestPreview,
} from '../services/admin-orders.service';

@Component({ selector: 'app-stl-viewer', template: '' })
class StlViewerStubComponent {
  readonly file = input<Blob | null>(null);
  readonly color = input('');
  readonly height = input(0);
}

describe('Admin orders responsive layout', () => {
  let fixture: ComponentFixture<AdminDashboardComponent>;
  let frame: HTMLIFrameElement;
  const order: AdminOrder = {
    id: '6944ae80-2cef-461c-b3d8-e38e31df7326',
    orderNumber: '6944ae80',
    status: 'PAID',
    paymentStatus: 'RECEIVED',
    customerEmail: 'customer@example.com',
    totalChf: 129.55,
    createdAt: '2026-09-09T18:05:00Z',
    isCadOrder: true,
    items: [],
    emailLogs: [],
  };
  const preview: ReviewRequestPreview = {
    recipient: 'customer@example.com',
    subject: 'Review request',
    headline: 'Thank you',
    greeting: 'Hello,',
    intro: 'Thank you for your order.',
    request: 'Tell us honestly about your experience.',
    photoNote: 'You may add photos.',
    actionText: 'Write a review',
    reviewUrl: 'https://g.page/r/CXamfIi-St1wEAI/review',
    closing: 'Thank you.',
    signature: 'Joe and Matteo',
    footer: '3D-Fab',
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminDashboardComponent],
      providers: [
        provideTranslateService(),
        {
          provide: LanguageService,
          useValue: { localizedPath: (path: string) => `/it${path}` },
        },
        {
          provide: AdminOrdersService,
          useValue: {
            listOrders: () => of([order]),
            getOrder: () => of(order),
            getStatistics: () => of(null),
            previewReviewRequest: () => of(null),
            sendReviewRequest: () => of(order),
            downloadOrderItemFile: () => of(new Blob()),
          },
        },
      ],
    })
      .overrideComponent(AdminDashboardComponent, {
        remove: { imports: [StlViewerComponent] },
        add: { imports: [StlViewerStubComponent] },
      })
      .compileComponents();
    fixture = TestBed.createComponent(AdminDashboardComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    frame = document.createElement('iframe');
    frame.style.cssText = 'width: 1440px; height: 900px; border: 0;';
    document.body.appendChild(frame);
    const target = frame.contentDocument!;
    // Render the actual component and application styles in a viewport whose
    // media queries are independent of the Karma runner's window size.
    document
      .querySelectorAll('style, link[rel="stylesheet"]')
      .forEach((style) => {
        target.head.appendChild(style.cloneNode(true));
      });
    target.body.style.margin = '0';
    target.body.appendChild(fixture.nativeElement);
  });

  afterEach(() => {
    fixture.destroy();
    frame.remove();
  });

  function element(selector: string): HTMLElement {
    return frame.contentDocument!.querySelector<HTMLElement>(selector)!;
  }

  function waitForFrameLayout(): Promise<void> {
    return new Promise((resolve) =>
      frame.contentWindow!.requestAnimationFrame(() => resolve()),
    );
  }

  it('shows an asynchronous model response without another click', async () => {
    fixture.autoDetectChanges();
    const response = new Subject<Blob>();
    spyOn(
      TestBed.inject(AdminOrdersService),
      'downloadOrderItemFile',
    ).and.returnValue(response);
    const item = {
      id: 'model',
      originalFilename: 'model.stl',
    } as import('../services/admin-orders.service').AdminOrderItem;
    fixture.componentInstance.selectedOrder = order;
    fixture.componentInstance.openPreview(item);
    fixture.changeDetectorRef.markForCheck();
    fixture.detectChanges();
    expect(
      frame.contentDocument!.querySelector('.preview-loading'),
    ).not.toBeNull();
    response.next(
      new Blob(['solid preview\nendsolid preview'], { type: 'model/stl' }),
    );
    await fixture.whenStable();
    expect(frame.contentDocument!.querySelector('.preview-loading')).toBeNull();
    expect(
      frame.contentDocument!.querySelector('app-stl-viewer'),
    ).not.toBeNull();
  });

  it('shows an asynchronous model error without another click', async () => {
    fixture.autoDetectChanges();
    const response = new Subject<Blob>();
    spyOn(
      TestBed.inject(AdminOrdersService),
      'downloadOrderItemFile',
    ).and.returnValue(response);
    fixture.componentInstance.selectedOrder = order;
    fixture.componentInstance.openPreview({
      id: 'model',
      originalFilename: 'model.stl',
    } as import('../services/admin-orders.service').AdminOrderItem);
    fixture.changeDetectorRef.markForCheck();
    fixture.detectChanges();
    response.error(new Error('Missing model'));
    await fixture.whenStable();
    expect(frame.contentDocument!.querySelector('.preview-loading')).toBeNull();
    expect(
      frame.contentDocument!.querySelector('app-dialog .ui-banner--error'),
    ).not.toBeNull();
  });

  it('opens the review dialog after the preview response and records the asynchronous send result', async () => {
    fixture.autoDetectChanges();
    const service = TestBed.inject(AdminOrdersService);
    const previewResponse = new Subject<ReviewRequestPreview>();
    const sendResponse = new Subject<AdminOrder>();
    const previewCall = spyOn(service, 'previewReviewRequest').and.returnValue(
      previewResponse,
    );
    const sentOrder: AdminOrder = {
      ...order,
      status: 'SHIPPED',
      emailLogs: [
        {
          id: 'review-email',
          scope: 'ORDER',
          eventType: 'GOOGLE_REVIEW_REQUEST_CUSTOMER',
          status: 'SENT',
          origin: 'ADMIN',
          recipient: 'customer@example.com',
          subject: 'Review request',
          attemptedAt: '2026-09-28T10:00:00Z',
        },
      ],
    };
    const sendCall = spyOn(service, 'sendReviewRequest').and.returnValue(
      sendResponse,
    );
    fixture.componentInstance.selectedOrder = { ...order, status: 'SHIPPED' };
    fixture.changeDetectorRef.markForCheck();
    fixture.detectChanges();

    const previewButton = element(
      '.review-request-panel button',
    ) as HTMLButtonElement;
    previewButton.click();
    await fixture.whenStable();
    expect(previewCall).toHaveBeenCalledWith(order.id);
    expect(sendCall).not.toHaveBeenCalled();
    expect(previewButton.disabled).toBeTrue();
    expect(frame.contentDocument!.querySelector('app-dialog')).toBeNull();

    previewResponse.next(preview);
    previewResponse.complete();
    await fixture.whenStable();
    const dialog = frame.contentDocument!.querySelector('app-dialog dialog');
    expect(dialog).not.toBeNull();
    expect(dialog?.hasAttribute('open')).toBeTrue();
    expect(dialog?.textContent).toContain(preview.recipient);
    expect(dialog?.textContent).toContain(preview.subject);
    expect(previewButton.disabled).toBeFalse();
    expect(sendCall).not.toHaveBeenCalled();

    const sendButton = element(
      '.review-preview app-button button',
    ) as HTMLButtonElement;
    sendButton.click();
    await fixture.whenStable();
    expect(sendCall).toHaveBeenCalledWith(order.id);
    expect(sendButton.disabled).toBeTrue();
    sendResponse.next(sentOrder);
    sendResponse.complete();
    await fixture.whenStable();
    expect(frame.contentDocument!.querySelector('app-dialog')).toBeNull();
    expect(previewButton.disabled).toBeTrue();
    expect(element('.email-log-panel').textContent).toContain('Review request');
    expect(element('.email-log-panel').textContent).toContain(
      'customer@example.com',
    );
    expect(frame.contentDocument!.querySelector('.ui-banner--error')).toBeNull();
  });

  it('shows an asynchronous review preview error and enables retry without another click', async () => {
    fixture.autoDetectChanges();
    const response = new Subject<ReviewRequestPreview>();
    const previewCall = spyOn(
      TestBed.inject(AdminOrdersService),
      'previewReviewRequest',
    ).and.returnValue(response);
    fixture.componentInstance.selectedOrder = { ...order, status: 'SHIPPED' };
    fixture.changeDetectorRef.markForCheck();
    fixture.detectChanges();

    const button = element('.review-request-panel button') as HTMLButtonElement;
    button.click();
    await fixture.whenStable();
    expect(button.disabled).toBeTrue();
    response.error(new Error('Preview unavailable'));
    await fixture.whenStable();

    expect(button.disabled).toBeFalse();
    expect(element('.ui-banner--error').textContent).toContain(
      'ADMIN_ORDERS.REVIEW_PREVIEW_ERROR',
    );
    expect(frame.contentDocument!.querySelector('app-dialog')).toBeNull();
    button.click();
    expect(previewCall).toHaveBeenCalledTimes(2);
  });

  it('closes the dialog and shows a send error while the order refresh is pending', async () => {
    fixture.autoDetectChanges();
    const service = TestBed.inject(AdminOrdersService);
    const response = new Subject<AdminOrder>();
    const refreshResponse = new Subject<AdminOrder>();
    spyOn(service, 'previewReviewRequest').and.returnValue(of(preview));
    spyOn(service, 'sendReviewRequest').and.returnValue(response);
    const refreshCall = spyOn(service, 'getOrder').and.returnValue(refreshResponse);
    fixture.componentInstance.selectedOrder = { ...order, status: 'SHIPPED' };
    fixture.changeDetectorRef.markForCheck();
    fixture.detectChanges();

    element('.review-request-panel button').click();
    await fixture.whenStable();
    element('.review-preview app-button button').click();
    await fixture.whenStable();
    response.error(new Error('Send unavailable'));
    await fixture.whenStable();

    expect(frame.contentDocument!.querySelector('app-dialog')).toBeNull();
    expect(fixture.componentInstance.reviewSending).toBeFalse();
    expect(element('.ui-banner--error').textContent).toContain(
      'ADMIN_ORDERS.REVIEW_SEND_ERROR',
    );
    expect(refreshCall).toHaveBeenCalledWith(order.id);
    refreshResponse.next(order);
    refreshResponse.complete();
    await fixture.whenStable();
  });

  it('keeps desktop cells under their headers and filters visible', () => {
    const cells = Array.from(
      frame.contentDocument!.querySelectorAll('tbody td'),
    );
    const headers = Array.from(
      frame.contentDocument!.querySelectorAll('thead th'),
    );
    expect(cells.length).toBe(4);
    cells.forEach((cell, index) => {
      expect(frame.contentWindow!.getComputedStyle(cell).display).toBe(
        'table-cell',
      );
      expect(
        Math.abs(
          cell.getBoundingClientRect().left -
            headers[index].getBoundingClientRect().left,
        ),
      ).toBeLessThan(1);
      expect(
        Math.abs(
          cell.getBoundingClientRect().top -
            cells[0].getBoundingClientRect().top,
        ),
      ).toBeLessThan(1);
    });
    expect(
      element('#order-filters').getBoundingClientRect().height,
    ).toBeGreaterThan(0);
    expect(element('.filters-toggle').getBoundingClientRect().height).toBe(0);
  });

  it('shows reachable filters and cards without horizontal overflow on a phone', async () => {
    frame.style.width = '390px';
    await waitForFrameLayout();
    expect(element('#order-filters').getBoundingClientRect().height).toBe(0);
    element('.filters-toggle').click();
    fixture.detectChanges();
    await waitForFrameLayout();
    expect(
      element('#order-filters').getBoundingClientRect().height,
    ).toBeGreaterThan(0);
    expect(element('.filters-toggle').getAttribute('aria-expanded')).toBe(
      'true',
    );
    expect(element('.orders-table').scrollWidth).toBeLessThanOrEqual(
      element('.list-panel').clientWidth,
    );
    fixture.componentInstance.mobileDetailOpen = true;
    fixture.detectChanges();
    await waitForFrameLayout();
    expect(element('.list-panel').getBoundingClientRect().height).toBe(0);
    expect(
      element('.mobile-back-button').getBoundingClientRect().height,
    ).toBeGreaterThan(0);
    expect(element('.detail-panel').scrollWidth).toBeLessThanOrEqual(
      element('.detail-panel').clientWidth,
    );
  });
});
