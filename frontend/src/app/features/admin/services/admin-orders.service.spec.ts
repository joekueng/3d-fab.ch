import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../../environments/environment';
import { AdminOrdersService } from './admin-orders.service';

describe('AdminOrdersService', () => {
  it('sends customer search terms in the authenticated request body', () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const service = TestBed.inject(AdminOrdersService);
    const http = TestBed.inject(HttpTestingController);
    const filters = {
      q: 'customer@example.test',
      orderStatus: 'PAID',
      paymentStatus: 'ALL',
      orderType: 'ALL',
    };
    service.listOrders(2, filters).subscribe();

    const request = http.expectOne({
      method: 'POST',
      url: `${environment.apiUrl}/api/admin/orders/search`,
    });
    expect(request.request.withCredentials).toBeTrue();
    expect(request.request.body).toEqual({ pagination: { page: 2 }, filters });
    request.flush({ items: [], page: 2, size: 25, totalElements: 0 });
    http.verify();
  });
});
