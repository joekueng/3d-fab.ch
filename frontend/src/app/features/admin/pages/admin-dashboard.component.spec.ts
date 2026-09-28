import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';
import { AdminDashboardComponent } from './admin-dashboard.component';
import {
  AdminOrder,
  AdminOrdersService,
  ReviewRequestPreview,
} from '../services/admin-orders.service';

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

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminDashboardComponent, TranslateModule.forRoot()],
      providers: [
        {
          provide: AdminOrdersService,
          useValue: {
            listOrders: () => of([order]),
            getOrder: () => of(order),
            getStatistics: () => of(null),
            previewReviewRequest: () => of(null),
            sendReviewRequest: () => of(order),
          },
        },
      ],
    }).compileComponents();
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

  it('shows the request before sending and records the manual result', () => {
    const service = TestBed.inject(AdminOrdersService);
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
    const previewCall = spyOn(service, 'previewReviewRequest').and.returnValue(
      of(preview),
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
      of(sentOrder),
    );
    fixture.componentInstance.selectedOrder = { ...order, status: 'SHIPPED' };

    fixture.componentInstance.openReviewRequestPreview();
    expect(previewCall).toHaveBeenCalledWith(order.id);
    expect(sendCall).not.toHaveBeenCalled();
    expect(fixture.componentInstance.reviewRequestPreview?.reviewUrl).toBe(
      preview.reviewUrl,
    );

    fixture.componentInstance.sendReviewRequest();
    expect(sendCall).toHaveBeenCalledWith(order.id);
    expect(fixture.componentInstance.canRequestReview(sentOrder)).toBeFalse();
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

  it('shows reachable filters and cards without horizontal overflow on a phone', () => {
    frame.style.width = '390px';
    expect(element('#order-filters').getBoundingClientRect().height).toBe(0);
    element('.filters-toggle').click();
    fixture.detectChanges();
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
    expect(element('.list-panel').getBoundingClientRect().height).toBe(0);
    expect(
      element('.mobile-back-button').getBoundingClientRect().height,
    ).toBeGreaterThan(0);
    expect(element('.detail-panel').scrollWidth).toBeLessThanOrEqual(
      element('.detail-panel').clientWidth,
    );
  });
});
